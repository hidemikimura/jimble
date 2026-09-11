package io.jimble.web.auth.oidc;

/**
 * OIDC の途中で断ったときの例外（要件 F-W-31）
 *
 * <p>
 * <b>これが出たら「ログインさせない」である。</b>
 * {@link Oidc#callback} が受け取って 401 に変える——
 * <b>理由は利用者に見せず、ログにだけ残す</b>。
 * 「nonce が合いません」と返すと、<b>どこまで通ったかを外から測れる</b>。
 * </p>
 */
public class OidcException extends RuntimeException {

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public OidcException (String message) {

		super(message);

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		原因
	 */
	public OidcException (String message, Throwable cause) {

		super(message, cause);

	}

}
