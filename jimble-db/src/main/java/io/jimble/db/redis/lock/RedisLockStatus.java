package io.jimble.db.redis.lock;

/**
 * ロックステータス
 */
public enum RedisLockStatus {

	/* RedisもDBもロック取得成功 */
	Success

	/* ロック取得失敗 */
	, Failed

	/* Redisはロック取得できたがDBはロック中ステータスだった */
	, Inconsistency

}
