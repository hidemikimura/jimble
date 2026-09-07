package io.jimble.web.http;

/**
 * HTTPステータスコードを持つ例外
 *
 * <p>
 * エラーハンドラにはここから解決したステータスコードが渡る。
 * </p>
 */
public class HttpException extends RuntimeException {

	/* ステータスコード */
	private final int statusCode;

	/**
	 * コンストラクタ
	 *
	 * @param statusCode	ステータスコード
	 * @param message		メッセージ
	 */
	public HttpException (int statusCode, String message) {

		super(message);
		this.statusCode = statusCode;

	}

	/**
	 * コンストラクタ
	 *
	 * @param statusCode	ステータスコード
	 * @param message		メッセージ
	 * @param cause			原因
	 */
	public HttpException (int statusCode, String message, Throwable cause) {

		super(message, cause);
		this.statusCode = statusCode;

	}

	/**
	 * ステータスコード
	 *
	 * @return	ステータスコード
	 */
	public int statusCode () {

		return statusCode;

	}

}
