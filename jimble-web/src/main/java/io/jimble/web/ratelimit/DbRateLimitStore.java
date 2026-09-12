package io.jimble.web.ratelimit;

import io.jimble.db.FrameworkTables;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DB に置く（要件 F-R-15）
 *
 * <p>
 * <b>Redis が無いところで台をまたいで数えたいとき</b>のためのもの。
 * 1回ごとに {@code SELECT ... FOR UPDATE} を取るので、
 * <b>高い頻度で叩かれるところには向かない</b>（ログインの試行回数などに使う）。
 * </p>
 *
 * <p>
 * テーブルは<b>初めて使うときに作る</b>。使わないアプリに作らないためである。
 * </p>
 */
public final class DbRateLimitStore implements RateLimitStore {

	/** テーブル名 */
	public static final String TABLE = FrameworkTables.RATE_LIMIT;

	/* テーブルを作る排他 */
	private static final ReentrantLock INIT_LOCK = new ReentrantLock();

	/* 作ったか */
	private static volatile boolean initialized = false;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimitResult consume (String key, long limit, Duration duration) throws Exception {

		initialize();

		long hash = Hash.sipHash(key);
		long now = System.currentTimeMillis();
		long durationMillis = duration.toMillis();

		try (DB db = DBUtil.getMainDB()) {

			db.beginTransaction();

			try {

				Data row = db.select(
					"SELECT tokens, updated_at FROM %s WHERE rate_key = ? FOR UPDATE".formatted(TABLE)
					, hash);

				double tokens = limit;

				if (row != null && !row.isEmpty()) {

					long at = row.getLong("updated_at");

					tokens = Math.min(limit
						, row.getDouble("tokens") + (double) (now - at) * limit / durationMillis);

				}

				RateLimitResult result;

				if (tokens >= 1) {
					tokens -= 1;
					result = RateLimitResult.allow((long) tokens);
				} else {
					result = RateLimitResult.deny(
						(long) Math.ceil((1 - tokens) * durationMillis / limit));
				}

				db.execute("""
					INSERT INTO %s (rate_key, tokens, updated_at) VALUES (?, ?, ?)
					""".formatted(TABLE)
					+ Sqls.upsert(db.dialect(), List.of("rate_key"), "tokens", "updated_at")
					, hash, tokens, now);

				db.commitEndTransaction();

				return result;

			} catch (Exception ex) {

				db.rollbackEndTransaction();
				throw ex;

			}

		}

	}

	/**
	 * テーブルを作る
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

			try (DB db = DBUtil.getMainDB()) {

				DBVersion version = new DBVersion(TABLE, "流量制限");

				version.add(1)
					.mysql("""
						create table %s (
							rate_key bigint not null primary key
							, tokens double not null
							, updated_at bigint not null
						) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
						""".formatted(TABLE, version.placeholder()))
					.postgresql("""
						create table %s (
							rate_key bigint not null primary key
							, tokens double precision not null
							, updated_at bigint not null
						)
						""".formatted(TABLE));

				if (!version.apply(db)) {
					Log.error("%s テーブルを作れませんでした".formatted(TABLE));
				}

			}

			initialized = true;

		} finally {

			INIT_LOCK.unlock();

		}

	}

}
