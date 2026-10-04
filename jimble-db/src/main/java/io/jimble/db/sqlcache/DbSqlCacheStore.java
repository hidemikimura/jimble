package io.jimble.db.sqlcache;

import io.jimble.db.FrameworkTables;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DB に置く（要件 F-D-28）
 *
 * <p>
 * <b>Redis が無いところで台をまたいで効かせたいとき</b>のためのもの。
 * SQL の結果を DB に置くので、<b>引く SQL より軽いときにしか得しない</b>
 * （結合が多い・件数が多い・集計が重い、といったクエリに使う）。
 * </p>
 *
 * <p>
 * テーブルは<b>初めて使うときに作る</b>。使わないアプリに作らないためである。
 * </p>
 */
public final class DbSqlCacheStore implements SqlCacheStore {

	/** 値のテーブル名 */
	public static final String TABLE = FrameworkTables.SQL_CACHE;

	/** タグのテーブル名 */
	public static final String TAG_TABLE = FrameworkTables.SQL_CACHE_TAG;

	/* テーブルを作る排他 */
	private static final ReentrantLock INIT_LOCK = new ReentrantLock();

	/* 作ったか */
	private static volatile boolean initialized = false;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String get (String key) throws Exception {

		initialize();

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			Data row = db.select(
				"SELECT content, expires_at FROM %s WHERE cache_key = ?".formatted(TABLE), key).orElse(null);

			if (row == null || row.isEmpty()) {
				return null;
			}

			long expiresAt = row.getLong("expires_at");

			if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
				return null;
			}

			return row.getString("content");

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put (String key, Set<String> tags, String value, Duration ttl) throws Exception {

		if (tags.isEmpty()) {
			return;
		}

		initialize();

		long expiresAt = ttl == null || ttl.isZero() ? 0 : System.currentTimeMillis() + ttl.toMillis();

		purgeIfDue();

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			db.transaction(tx -> {

				db.execute("""
					INSERT INTO %s (cache_key, content, expires_at, created_at)
					VALUES (?, ?, ?, ?)
					""".formatted(TABLE)
					+ Sqls.upsert(db.dialect(), List.of("cache_key"), "content", "expires_at", "created_at")
					, key, value, expiresAt, System.currentTimeMillis());

				db.execute("DELETE FROM %s WHERE cache_key = ?".formatted(TAG_TABLE), key);

				List<List<Object>> params = new ArrayList<>();

				for (String tag : tags) {
					params.add(List.of(Hash.sipHash(tag), key));
				}

				db.executeBatch(
					Sqls.insertIgnoreInto(db.dialect(), TAG_TABLE)
						+ " (tag, cache_key) VALUES (?, ?)"
						+ Sqls.insertIgnoreTail(db.dialect())
					, params);

			});

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invalidate (Set<String> tags) throws Exception {

		if (tags.isEmpty()) {
			return;
		}

		initialize();

		List<Object> hashes = new ArrayList<>();
		StringBuilder places = new StringBuilder();

		for (String tag : tags) {
			if (!places.isEmpty()) {
				places.append(", ");
			}
			places.append('?');
			hashes.add(Hash.sipHash(tag));
		}

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			/*
			 * 消す対象のキーを先に引いてから消す。
			 * <b>1本の DELETE ... WHERE cache_key IN (SELECT ...) にすると
			 * MySQL は同じテーブルを読みながら消せない。</b>
			 */
			List<Data> rows = db.selectList(
				"SELECT DISTINCT cache_key FROM %s WHERE tag IN (%s)".formatted(TAG_TABLE, places)
				, hashes.toArray());

			if (rows.isEmpty()) {
				return;
			}

			List<List<Object>> params = new ArrayList<>();

			for (Data row : rows) {
				params.add(List.of(row.getString("cache_key")));
			}

			db.executeBatch("DELETE FROM %s WHERE cache_key = ?".formatted(TABLE), params);
			db.executeBatch("DELETE FROM %s WHERE cache_key = ?".formatted(TAG_TABLE), params);

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear () throws Exception {

		initialize();

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			db.execute("DELETE FROM %s".formatted(TAG_TABLE));
			db.execute("DELETE FROM %s".formatted(TABLE));

		}

	}

	/** 期限切れを消す間隔 */
	static final long PURGE_INTERVAL_MILLIS = 10 * 60 * 1000L;

	/** 1回に消す件数 */
	static final int PURGE_BATCH = 1000;

	/** 1回の掃除で回す上限（それ以上は次の回に） */
	static final int PURGE_ROUNDS = 20;

	/* 最後に掃除を始めた時刻 */
	private static final java.util.concurrent.atomic.AtomicLong LAST_PURGE = new java.util.concurrent.atomic.AtomicLong(0);

	/**
	 * 期限切れを消す（{@value #PURGE_INTERVAL_MILLIS} ミリ秒に1回。別のスレッドで）
	 *
	 * <p>
	 * <b>2.5.1 までは誰も消していなかった</b>（D-282）。読むときに期限を見て返さないだけだったので、
	 * 違うパラメータで引いた結果が1行ずつ溜まり続けた。入れたリクエストを待たせないよう、別のスレッドで消す。
	 * </p>
	 */
	private static void purgeIfDue () {

		long now = System.currentTimeMillis();
		long last = LAST_PURGE.get();

		if (now - last < PURGE_INTERVAL_MILLIS || !LAST_PURGE.compareAndSet(last, now)) {
			return;
		}

		Thread.ofVirtual().name("jimble-sql-cache-purge").start(() -> {
			try {
				purgeExpired(now);
			} catch (Exception ex) {
				Log.error(ex, "期限切れの SQL結果キャッシュを消せませんでした");
			}
		});

	}

	/**
	 * 期限切れを消す
	 *
	 * @param now	いまの時刻（ミリ秒）
	 * @return	消した件数
	 */
	static int purgeExpired (long now) {

		int purged = 0;

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			for (int round = 0; round < PURGE_ROUNDS; round++) {

				List<Data> rows = db.selectList(
					"SELECT cache_key FROM %s WHERE expires_at > 0 AND expires_at < ? LIMIT %d".formatted(TABLE, PURGE_BATCH)
					, now);

				if (rows.isEmpty()) {
					break;
				}

				List<List<Object>> params = new ArrayList<>();
				for (Data row : rows) {
					params.add(List.of(row.getString("cache_key")));
				}

				/*
				 * <b>行を先に消し、タグは行が無くなったキーのものだけ消す。</b>
				 * あいだに同じキーを入れ直した人がいると、行の期限は延びていて（消えない）、タグも入れ直されている。
				 * タグを先に消すと、入れ直したタグまで消えて、更新しても消えないキャッシュが残る
				 */
				db.executeBatch("DELETE FROM %s WHERE cache_key = ? AND expires_at > 0 AND expires_at < ?".formatted(TABLE)
					, params.stream().map(param -> List.<Object>of(param.get(0), now)).toList());
				db.executeBatch("DELETE FROM %s WHERE cache_key = ? AND NOT EXISTS (SELECT 1 FROM %s WHERE cache_key = ?)".formatted(TAG_TABLE, TABLE)
					, params.stream().map(param -> List.<Object>of(param.get(0), param.get(0))).toList());

				purged += rows.size();

				if (rows.size() < PURGE_BATCH) {
					break;
				}

			}

		}

		return purged;

	}

	/**
	 * テーブルを作る
	 *
	 * @throws Exception	作れなかった場合
	 */
	private void initialize () throws Exception {

		if (initialized) {
			return;
		}

		INIT_LOCK.lock();

		try {

			if (initialized) {
				return;
			}

			try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

				DBVersion version = new DBVersion(TABLE, "SQL結果キャッシュ");

				version.add(1)
					.mysql("""
						create table %s (
							cache_key varchar(64) not null primary key
							, content longtext not null
							, expires_at bigint not null default 0
							, created_at bigint not null
						) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
						""".formatted(TABLE, version.placeholder()))
					.postgresql("""
						create table %s (
							cache_key varchar(64) not null primary key
							, content text not null
							, expires_at bigint not null default 0
							, created_at bigint not null
						)
						""".formatted(TABLE));

				// 期限切れを消すときの順路（D-282）
				version.add(2)
					.mysql("create index %s__index_1 on %s (expires_at)".formatted(TABLE, TABLE))
					.postgresql("create index %s__index_1 on %s (expires_at)".formatted(TABLE, TABLE));

				if (!version.apply(db)) {
					Log.error("%s テーブルを作れませんでした".formatted(TABLE));
				}

				DBVersion tagVersion = new DBVersion(TAG_TABLE, "SQL結果キャッシュのタグ");

				tagVersion.add(1)
					.mysql("""
						create table %s (
							tag bigint not null
							, cache_key varchar(64) not null
							, primary key (tag, cache_key)
							, index %s__index_1 (cache_key)
						) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
						""".formatted(TAG_TABLE, TAG_TABLE, tagVersion.placeholder()))
					.postgresql("""
						create table %s (
							tag bigint not null
							, cache_key varchar(64) not null
							, primary key (tag, cache_key)
						)
						""".formatted(TAG_TABLE)
					, "create index %s__index_1 on %s (cache_key)".formatted(TAG_TABLE, TAG_TABLE));

				if (!tagVersion.apply(db)) {
					Log.error("%s テーブルを作れませんでした".formatted(TAG_TABLE));
				}

			}

			initialized = true;

		} finally {

			INIT_LOCK.unlock();

		}

	}

}
