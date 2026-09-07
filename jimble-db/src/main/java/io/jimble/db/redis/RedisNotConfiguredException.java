package io.jimble.db.redis;

/**
 * Redis が設定されていないのに Redis を使おうとした（要件 F-U-10）
 *
 * <p>
 * <b>Redis 未設定でもアプリは起動できる。</b>ただし Redis 前提の機能
 * （分散ロック・Redis キャッシュ・Redis セッション）を呼んだら、
 * <b>黙って成功させずにこの例外を投げる。</b>
 * </p>
 *
 * <p>
 * 分散ロックが「取れなかった」と「そもそも Redis が無い」を同じ扱いにすると、
 * <b>ロックしていないのに処理が進む</b>ことになる。区別する必要がある。
 * </p>
 */
public class RedisNotConfiguredException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public RedisNotConfiguredException (String message) {

		super(message);

	}

}
