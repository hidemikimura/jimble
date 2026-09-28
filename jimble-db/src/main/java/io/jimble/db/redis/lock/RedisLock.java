package io.jimble.db.redis.lock;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.db.FrameworkTables;
import io.jimble.util.hash.Hash;
import io.jimble.db.DB;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.db.redis.RedisClient;
import org.redisson.api.RLock;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis のロック
 *
 * <pre>
 * try (RedisLockResult lock = RedisLock.lock("order:" + id)) {
 *     ...                       // ここは鍵を持っている
 * }
 *
 * Optional&lt;RedisLockResult&gt; lock = RedisLock.tryLock("report", 1000, 60000);
 * if (lock.isEmpty()) { return 「ほかで動いています」; }
 * try (RedisLockResult held = lock.get()) { ... }
 * </pre>
 *
 * <p>
 * <b>取れなければ例外か空の {@link Optional}</b>（2.0。要件 D-193）。1.x は {@code status()} が {@code Failed} になるだけで、
 * 見なければ鍵なしで中を走っていた。
 * </p>
 */
public class RedisLock {

	/**
	 * ロックする（取れるまで待つ）
	 *
	 * @param lockKey	ロックキー
	 * @return	結果（{@code status()} は {@code Success}）
	 * @throws RedisLockException	取れなかったとき（Redis に繋がらないなど）
	 */
	@CheckReturnValue
	public static RedisLockResult lock (String lockKey) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			lock.lock();
			return new RedisLockResult(RedisLockStatus.Success, lock);
		} catch (Exception ex) {
			throw failed(lock, lockKey, ex);
		}

	}

	/**
	 * ロックする（取れるまで待つ。DB の印つき）
	 *
	 * <p>
	 * DB にも「使用中」の印を付ける。印がすでに立っていたら（前の持ち主が印を戻さずに落ちたなど）
	 * {@code status()} が {@code Inconsistency} になる——<b>鍵は持っている</b>ので、確かめてから続けるか閉じる。
	 * </p>
	 *
	 * @param db		DB
	 * @param lockKey	ロックキー
	 * @return	結果（{@code Success} か {@code Inconsistency}）
	 * @throws RedisLockException	取れなかったとき、DB の印を読み書きできなかったとき
	 */
	@CheckReturnValue
	public static RedisLockResult lock (DB db, String lockKey) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			lock.lock();
			return mark(db, lockKey, lock);
		} catch (Exception ex) {
			throw failed(lock, lockKey, ex);
		}

	}

	/**
	 * ロックする（待つ時間を決める）
	 *
	 * @param lockKey		ロックキー
	 * @param waitTimeMs	ロック取得までの最大待ち時間(ms)
	 * @param maintainMs	ロックを維持する時間(ms)
	 * @return	結果。待っても取れなければ空
	 * @throws RedisLockException	Redis に繋がらないなど
	 */
	@CheckReturnValue
	public static Optional<RedisLockResult> tryLock (String lockKey, int waitTimeMs, int maintainMs) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			if (!lock.tryLock(waitTimeMs, maintainMs, TimeUnit.MILLISECONDS)) {
				return Optional.empty();
			}
			return Optional.of(new RedisLockResult(RedisLockStatus.Success, lock));
		} catch (Exception ex) {
			throw failed(lock, lockKey, ex);
		}

	}

	/**
	 * ロックする（待つ時間を決める。DB の印つき）
	 *
	 * @param db			DB
	 * @param lockKey		ロックキー
	 * @param waitTimeMs	ロック取得までの最大待ち時間(ms)
	 * @param maintainMs	ロックを維持する時間(ms)
	 * @return	結果（{@code Success} か {@code Inconsistency}）。待っても取れなければ空
	 * @throws RedisLockException	Redis に繋がらない、DB の印を読み書きできなかったなど
	 */
	@CheckReturnValue
	public static Optional<RedisLockResult> tryLock (DB db, String lockKey, int waitTimeMs, int maintainMs) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			if (!lock.tryLock(waitTimeMs, maintainMs, TimeUnit.MILLISECONDS)) {
				return Optional.empty();
			}
			return Optional.of(mark(db, lockKey, lock));
		} catch (Exception ex) {
			throw failed(lock, lockKey, ex);
		}

	}

	/**
	 * DB に「使用中」の印を付ける
	 *
	 * @param db		DB
	 * @param lockKey	ロックキー
	 * @param lock		取れた鍵
	 * @return	結果
	 */
	private static RedisLockResult mark (DB db, String lockKey, RLock lock) {

		long lockKeyHash = Hash.sipHash(lockKey);

		boolean marked = db.select("""
				SELECT
					lock_flg
				FROM
					redis_lock
				WHERE
					lock_key = ?
			"""
			, lockKeyHash
		).map(row -> row.getBoolean("lock_flg")).orElse(false);

		if (marked) {
			return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Inconsistency, lock);
		}

		db.insert("""
				INSERT INTO redis_lock (
					lock_key
					, lock_flg
				) VALUES (
					?
					, ?
				)
			""" + Sqls.upsert(db.dialect(), List.of("lock_key"), "lock_flg")
			, lockKeyHash
			, true
		);

		return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Success, lock);

	}

	/**
	 * 取れなかった。持っていれば外してから例外にする
	 *
	 * @param lock		鍵
	 * @param lockKey	ロックキー
	 * @param ex		元の例外
	 * @return	投げる例外
	 */
	private static RedisLockException failed (RLock lock, String lockKey, Exception ex) {

		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		} catch (Exception ignore) {}

		return new RedisLockException("ロックを取れませんでした: " + lockKey + ": " + ex.getMessage(), ex);

	}

	/**
	 * 初期化
	 *
	 * @param db DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.REDIS_LOCK, "Redisロック情報");
		dbVersion.add(1)
			.mysql("""
				create table redis_lock (
					lock_key bigint not null primary key
					, lock_flg tinyint(1) not null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT '%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table redis_lock (
					lock_key bigint not null primary key
					, lock_flg boolean not null
				)
			""");
		dbVersion.apply(db);

	}

}
