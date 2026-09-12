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
				"SELECT content, expires_at FROM %s WHERE cache_key = ?".formatted(TABLE), key);

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

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {

			db.beginTransaction();

			try {

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

				db.commitEndTransaction();

			} catch (Exception ex) {

				db.rollbackEndTransaction();
				throw ex;

			}

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

			if (rows == null || rows.isEmpty()) {
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
