package io.jimble.db.redis.lock;

import io.jimble.util.hash.Hash;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.version.DBVersion;
import io.jimble.db.redis.RedisClient;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * redis lock
 */
public class RedisLock {

	/**
	 * ロックする
	 *
	 * @param lockKey	ロックキー
	 * @return	結果
	 */
	public static RedisLockResult lock (String lockKey) {

		// Redis が無いのに「ロックできなかった」として返すと、
		// 呼び出し側がロック無しで進みうる（要件 F-U-10）
		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			lock.lock();
			return new RedisLockResult(RedisLockStatus.Success, lock);
		} catch (Exception ex) {
			try {
				lock.unlock();
			} catch (Exception ignore) {}
			return new RedisLockResult(RedisLockStatus.Failed, null);
		}

	}

	/**
	 * ロックする（DB楽観ロック付き）
	 *
	 * @param db		DB
	 * @param lockKey	ロックキー
	 * @return	結果
	 */
	public static RedisLockResult lock (DB db, String lockKey) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			lock.lock();

			long lockKeyHash = Hash.sipHash(lockKey);
			Data data = db.select("""
					SELECT
						lock_flg
					FROM
						redis_lock
					WHERE
						lock_key = ?
				"""
				, lockKeyHash
			);
			if (db.isError()) {
				throw db.getError();
			}
			if (data != null && data.getBoolean("lock_flg")) {
				return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Inconsistency, lock);
			}

			db.insert("""
					INSERT IGNORE INTO redis_lock (
						lock_key
						, lock_flg
					) VALUES (
						?
						, ?
					)
					ON DUPLICATE KEY UPDATE
						lock_flg = VALUES(lock_flg)
				"""
				, Hash.sipHash(lockKey)
				, 1
			);
			if (db.isError()) {
				throw db.getError();
			}

			return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Success, lock);
		} catch (Exception ex) {
			try {
				lock.unlock();
			} catch (Exception ignore) {}
			return new RedisLockResult(RedisLockStatus.Failed, null);
		}

	}

	/**
	 * ロックする
	 *
	 * @param lockKey		ロックキー
	 * @param waitTimeMs	ロック取得までの最大待ち時間(ms)
	 * @param maintainMs	ロックを維持する時間(ms)
	 * @return	結果
	 */
	public static RedisLockResult tryLock (String lockKey, int waitTimeMs, int maintainMs) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			if (!lock.tryLock(waitTimeMs, maintainMs, TimeUnit.MILLISECONDS)) {
				return new RedisLockResult(RedisLockStatus.Failed, null);
			}
			return new RedisLockResult(RedisLockStatus.Success, lock);
		} catch (Exception ex) {
			try {
				lock.unlock();
			} catch (Exception ignore) {}
			return new RedisLockResult(RedisLockStatus.Failed, null);
		}

	}

	/**
	 * ロックする
	 *
	 * @param db			DB
	 * @param lockKey		ロックキー
	 * @param waitTimeMs	ロック取得までの最大待ち時間(ms)
	 * @param maintainMs	ロックを維持する時間(ms)
	 * @return	結果
	 */
	public static RedisLockResult tryLock (DB db, String lockKey, int waitTimeMs, int maintainMs) {

		RLock lock = RedisClient.client().getLock(lockKey);
		try {
			long lockKeyHash = Hash.sipHash(lockKey);

			if (!lock.tryLock(waitTimeMs, maintainMs, TimeUnit.MILLISECONDS)) {
				return new RedisLockResult(null, lockKeyHash, RedisLockStatus.Failed, null);
			}

			Data data = db.select("""
					SELECT
						lock_flg
					FROM
						redis_lock
					WHERE
						lock_key = ?
				"""
				, lockKeyHash
			);
			if (db.isError()) {
				throw db.getError();
			}
			if (data != null && data.getBoolean("lock_flg")) {
				return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Inconsistency, lock);
			}

			db.insert("""
					INSERT IGNORE INTO redis_lock (
						lock_key
						, lock_flg
					) VALUES (
						?
						, ?
					)
					ON DUPLICATE KEY UPDATE
						lock_flg = VALUES(lock_flg)
				"""
				, Hash.sipHash(lockKey)
				, 1
			);
			if (db.isError()) {
				throw db.getError();
			}

			return new RedisLockResult(db, lockKeyHash, RedisLockStatus.Success, lock);
		} catch (Exception ex) {
			try {
				lock.unlock();
			} catch (Exception ignore) {}
			return new RedisLockResult(RedisLockStatus.Failed, null);
		}

	}

	/**
	 * 初期化
	 *
	 * @param db DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion("redis_lock", "Redisロック情報");
		dbVersion.add(1, """
				create table redis_lock (
					lock_key bigint not null primary key
					, lock_flg tinyint(1) not null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT '%s'
			""".formatted(dbVersion.placeholder())
		);
		dbVersion.apply(db);

	}

}
