package io.jimble.util.exception;

import io.jimble.util.data.Data;

/**
 * コード付き例外
 */
public class CodeException extends Exception {

	/* エラーコード */
	private String code;

	/**
	 * エラーコードを取得する
	 *
	 * @return  エラーコード
	 */
	public String getCode () {
		return code;
	}

	/**
	 * コンストラクタ
	 *
	 * @param message   メッセージ
	 */
	public CodeException(String message) {
		super(message);
		this.code = "Unknown_000";
	}

	/**
	 * コンストラクタ
	 *
	 * @param exception   Exception
	 */
	public CodeException(Exception exception) {
		super(exception.getMessage());
		this.code = "Unknown_000";
	}

	/**
	 * コンストラクタ
	 *
	 * @param code      コード
	 * @param message   メッセージ
	 */
	public CodeException(String code, String message) {
		super(message);
		this.code = code;
	}

	/**
	 * データを取得する
	 *
	 * @return  データ
	 */
	public Data getData () {

		return new Data()
			.putData("code", code)
			.putData("message", getMessage());

	}

}
