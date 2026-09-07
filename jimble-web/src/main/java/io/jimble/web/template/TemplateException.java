package io.jimble.web.template;

/**
 * テンプレートの描画に失敗した
 */
public class TemplateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public TemplateException (String message) {

		super(message);

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		原因
	 */
	public TemplateException (String message, Throwable cause) {

		super(message, cause);

	}

}
