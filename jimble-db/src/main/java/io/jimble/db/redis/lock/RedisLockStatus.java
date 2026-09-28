package io.jimble.db.redis.lock;

/**
 * ロックステータス
 */
public enum RedisLockStatus {

	/* RedisもDBもロック取得成功 */
	Success

	/**
	 * ロック取得失敗
	 *
	 * @deprecated 2.0 では返らない（取れなければ {@link RedisLockException} か空の {@code Optional}）。2.x で消す（要件 D-193）
	 */
	, @Deprecated(since = "2.0.0", forRemoval = true) Failed

	/* Redisはロック取得できたがDBはロック中ステータスだった */
	, Inconsistency

}
