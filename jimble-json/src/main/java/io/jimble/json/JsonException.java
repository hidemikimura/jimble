package io.jimble.json;

/**
 * JSON を読めなかった（D-307）
 *
 * <p>
 * <b>どこで何がおかしいかを載せる。</b>行と文字の位置は 1 から数える。
 * 作り直す前の読み手（Dson）は位置を持たず、入力の先頭 80 文字を載せるだけだったので、
 * 長い JSON では、どこが壊れているのかを探すところから始めることになっていた。
 * </p>
 */
public class JsonException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/* 行（1 から。位置が無ければ 0） */
	private final int line;

	/* 行の中の文字の位置（1 から。位置が無ければ 0） */
	private final int column;

	/**
	 * コンストラクタ
	 *
	 * @param message	何がおかしいか
	 * @param line		行（1 から。位置が無ければ 0）
	 * @param column	行の中の文字の位置（1 から。位置が無ければ 0）
	 */
	public JsonException (String message, int line, int column) {

		super(line > 0 ? message + "（" + line + " 行 " + column + " 文字目）" : message);

		this.line = line;
		this.column = column;

	}

	/**
	 * コンストラクタ（読み元の失敗など、位置の無いもの）
	 *
	 * @param message	何がおかしいか
	 * @param cause		原因
	 */
	public JsonException (String message, Throwable cause) {

		super(message, cause);

		this.line = 0;
		this.column = 0;

	}

	/**
	 * 行
	 *
	 * @return	1 から数えた行。位置が無ければ 0
	 */
	public int line () {

		return line;

	}

	/**
	 * 行の中の文字の位置
	 *
	 * @return	1 から数えた位置。位置が無ければ 0
	 */
	public int column () {

		return column;

	}

}
