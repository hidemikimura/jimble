package io.jimble.web.ratelimit;

/**
 * 1回ぶん数えた結果
 *
 * @param allowed			通してよいか
 * @param remaining			あと何回通せるか
 * @param retryAfterMillis	通せないとき、次に1回ぶん戻るまでの時間（ミリ秒）
 */
public record RateLimitResult (boolean allowed, long remaining, long retryAfterMillis) {

	/**
	 * 通す
	 *
	 * @param remaining	残り
	 * @return	結果
	 */
	public static RateLimitResult allow (long remaining) {

		return new RateLimitResult(true, Math.max(0, remaining), 0);

	}

	/**
	 * 止める
	 *
	 * @param retryAfterMillis	待つ時間
	 * @return	結果
	 */
	public static RateLimitResult deny (long retryAfterMillis) {

		return new RateLimitResult(false, 0, Math.max(1, retryAfterMillis));

	}

}
