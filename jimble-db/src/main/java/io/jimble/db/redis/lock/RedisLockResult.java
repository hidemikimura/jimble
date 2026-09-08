package io.jimble.db.redis.lock;

import io.jimble.db.DB;
import org.redisson.api.RLock;

import java.io.IOException;

/**
 * Redisロック結果
 */
public class RedisLockResult implements AutoCloseable {

	/* DB */
	private DB db;

	/* ロックキーハッシュ */
	private final long lockKeyHash;

	/* ステータス */
	public final RedisLockStatus status;

	/**
	 * ステータス
	 *
	 * @return	ステータス
	 */
	public RedisLockStatus status () {
		return status;
	}

	/* ロック */
	private RLock lock;

	/**
	 * コンストラクタ
	 *
	 * @param status		ステータス
	 * @param lock			ロック
	 */
	public RedisLockResult (RedisLockStatus status, RLock lock) {
		this.db = null;
		this.lockKeyHash = 0;
		this.status = status;
		this.lock = lock;
	}

	/**
	 * コンストラクタ
	 *
	 * @param db			DB
	 * @param lockKeyHash	ロックキーハッシュ
	 * @param status		ステータス
	 * @param lock			ロック
	 */
	public RedisLockResult (DB db, long lockKeyHash, RedisLockStatus status, RLock lock) {
		this.db = db;
		this.lockKeyHash = lockKeyHash;
		this.status = status;
		this.lock = lock;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {

		if (lock != null) {
			if (db != null) {
				db.update("""
						UPDATE redis_lock SET
							lock_flg = ?
						WHERE
							lock_key = ?
					"""
					, false
					, lockKeyHash
				);
				db = null;
			}
			try {
				lock.unlock();
				lock = null;
			} catch (Exception ex) {}
		}

	}

}
