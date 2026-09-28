package io.jimble.db.redis.lock;

import io.jimble.db.DB;
import org.redisson.api.RLock;

import io.jimble.util.log.Log;

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
	 * 鍵を外す（DB の印も戻す）
	 *
	 * <p>
	 * <b>検査例外を投げない</b>（2.0。1.x は {@code IOException}）。外すときの失敗はログに出す——
	 * 鍵は保持時間が過ぎれば Redis の側で外れる。
	 * </p>
	 */
	@Override
	public void close () {

		if (lock == null) {
			return;
		}

		try {
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
			}
		} catch (Exception ex) {
			Log.error(ex, "ロックの DB の印を戻せませんでした");
		} finally {
			db = null;
			try {
				lock.unlock();
			} catch (Exception ex) {
				Log.error(ex, "ロックを外せませんでした");
			}
			lock = null;
		}

	}

}
