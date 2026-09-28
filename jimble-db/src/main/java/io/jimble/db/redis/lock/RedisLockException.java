package io.jimble.db.redis.lock;

/**
 * ロックを取れなかった（Redis に繋がらない、DB の印を書けないなど）
 *
 * <p>
 * 2.0 で足した（要件 D-193）。1.x は {@link RedisLockResult#status()} が {@code Failed} になるだけで、
 * {@code try (var r = RedisLock.lock(key)) { ... }} と書くと<b>鍵なしで中を走っていた</b>。
 * </p>
 *
 * @since 2.0.0
 */
public class RedisLockException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		元の例外
	 */
	public RedisLockException (String message, Throwable cause) {

		super(message, cause);

	}

}
