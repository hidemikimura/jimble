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
		long durationMillis = duration.toMillis();

		MAX_DURATION.accumulateAndGet(durationMillis, Math::max);

		purgeIfDue();

		try (DB db = DBUtil.getMainDB()) {

			for (int attempt = 0; ; attempt++) {

				RateLimitResult result = db.transactionResult(tx -> {

					Data row = db.select(
						"SELECT tokens, updated_at FROM %s WHERE rate_key = ? FOR UPDATE".formatted(TABLE)
						, hash).orElse(null);

					if (row == null || row.isEmpty()) {
						// 行が無い。ここでは作らない（下で、トランザクションの外で作ってからやり直す）
						return null;
					}

					long now = System.currentTimeMillis();
					long at = row.getLong("updated_at");

					double tokens = Math.min(limit
						, row.getDouble("tokens") + (double) (now - at) * limit / durationMillis);

					RateLimitResult decided;

					if (tokens >= 1) {
						tokens -= 1;
						decided = RateLimitResult.allow((long) tokens);
					} else {
						decided = RateLimitResult.deny(
							(long) Math.ceil((1 - tokens) * durationMillis / limit));
					}

					db.execute("UPDATE %s SET tokens = ?, updated_at = ? WHERE rate_key = ?".formatted(TABLE)
						, tokens, now, hash);

					return decided;

				});

				if (result != null || attempt >= 2) {
					return result != null ? result : RateLimitResult.allow(limit - 1);
				}

				/*
				 * <b>初めてのキーは、トランザクションの外で満タンの行を作ってから数え直す</b>（D-285）。
				 * 2.5.1 までは「無い行の SELECT ... FOR UPDATE」のあと同じトランザクションで入れていた。
				 * MySQL は無い行を押さえるときに隙間（ギャップロック）を押さえるので、近いキーへ同時に初めて来た人どうしが
				 * 互いの隙間を待って<b>行き詰まり（デッドロック）</b>えた。満タンの行は「行が無い」と同じ意味なので、作っても数え方は変わらない
				 */
				db.execute(Sqls.insertIgnoreInto(db.dialect(), TABLE)
						+ " (rate_key, tokens, updated_at) VALUES (?, ?, ?)"
						+ Sqls.insertIgnoreTail(db.dialect())
					, hash, (double) limit, System.currentTimeMillis());

			}

		}

	}

	/** 掃除の間隔 */
	static final long PURGE_INTERVAL_MILLIS = 10 * 60 * 1000L;

	/** 消すのは、最後に数えてからこれより長く経った行だけ（ほかの台が長い宣言を使っていても消しすぎない） */
	static final long PURGE_MIN_IDLE_MILLIS = 24 * 60 * 60 * 1000L;

	/** 1回に消す件数 */
	static final int PURGE_BATCH = 1000;

	/* この台で見た、いちばん長い宣言の時間 */
	private static final java.util.concurrent.atomic.AtomicLong MAX_DURATION = new java.util.concurrent.atomic.AtomicLong(0);

	/* 最後に掃除を始めた時刻 */
	private static final java.util.concurrent.atomic.AtomicLong LAST_PURGE = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

	/**
	 * 満タンに戻った行を消す（{@value #PURGE_INTERVAL_MILLIS} ミリ秒に1回。別のスレッドで）
	 *
	 * <p>
	 * <b>2.5.1 までは誰も消していなかった</b>（D-285）。送信元ごとに1行できるので、表は増える一方だった。
	 * 最後に数えてから宣言の時間が過ぎた行は満タンで、「行が無い」と同じなので、消しても数え方は変わらない。
	 * </p>
	 */
	private static void purgeIfDue () {

		long now = System.currentTimeMillis();
		long last = LAST_PURGE.get();

		if (now - last < PURGE_INTERVAL_MILLIS || !LAST_PURGE.compareAndSet(last, now)) {
			return;
		}

		Thread.ofVirtual().name("jimble-rate-limit-purge").start(() -> {
			try {
				purgeIdle(now - Math.max(MAX_DURATION.get(), PURGE_MIN_IDLE_MILLIS));
			} catch (Exception ex) {
				Log.warn("流量制限の掃除に失敗しました: " + ex.getMessage());
			}
		});

	}

	/**
	 * 最後に数えたのがこれより前の行を消す
	 *
	 * @param before	時刻（ミリ秒）
	 * @return	消した件数
	 */
	static int purgeIdle (long before) {

		int total = 0;

		try (DB db = DBUtil.getMainDB()) {

			String sql = db.dialect() instanceof io.jimble.db.dialect.PostgreSqlDialect
				? "DELETE FROM %s WHERE ctid IN (SELECT ctid FROM %s WHERE updated_at < ? LIMIT %d)".formatted(TABLE, TABLE, PURGE_BATCH)
				: "DELETE FROM %s WHERE updated_at < ? LIMIT %d".formatted(TABLE, PURGE_BATCH);

			for (int round = 0; round < 100; round++) {

				int deleted = db.delete(sql, before);

				total += deleted;

				if (deleted < PURGE_BATCH) {
					break;
				}

			}

		}

		return total;

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

				// 満タンに戻った行を消すときの順路（D-285）
				version.add(2)
					.mysql("create index %s__index_1 on %s (updated_at)".formatted(TABLE, TABLE))
					.postgresql("create index %s__index_1 on %s (updated_at)".formatted(TABLE, TABLE));

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
