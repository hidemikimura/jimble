package io.jimble.db.internal.generator;

/**
 * コード生成失敗
 *
 * <p>
 * <b>この例外は握りつぶさない。</b>移送元は出力の失敗を {@code Log.error} して黙って戻っていたため、
 * <b>生成物が欠けたままビルドが進んでいた。</b>
 * </p>
 */
public class GeneratorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public GeneratorException (String message) {

		super(message);

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		原因
	 */
	public GeneratorException (String message, Throwable cause) {

		super(message, cause);

	}

}
