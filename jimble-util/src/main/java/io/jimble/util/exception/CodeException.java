package io.jimble.util.exception;

import io.jimble.util.data.Data;

/**
 * コード付き例外
 *
 * <p>
 * <b>非検査例外</b>（2.0。要件 D-197）。1.x は {@code Exception} の子だったので、
 * 投げるメソッドをラムダの中で呼べず、呼ぶ側に毎回 {@code throws} か {@code catch} を書かせていた。
 * {@code catch (CodeException e)} はそのまま書ける。
 * </p>
 */
public class CodeException extends RuntimeException {

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
		// 1.4 までは元の例外を捨てていた（スタックトレースに原因が出なかった。要件 D-190）
		super(exception.getMessage(), exception);
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
	 * コンストラクタ
	 *
	 * <p>包み直すときは<b>元の例外を必ず渡す</b>。渡さないと、ログに原因が出ない。</p>
	 *
	 * @param code      コード
	 * @param message   メッセージ
	 * @param cause     元の例外
	 * @since 1.5.0
	 */
	public CodeException(String code, String message, Throwable cause) {
		super(message, cause);
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
