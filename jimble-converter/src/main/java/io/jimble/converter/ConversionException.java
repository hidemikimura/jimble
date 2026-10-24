package io.jimble.converter;

/**
 * 変換できなかった（D-307）
 *
 * <p>
 * <b>どこで何ができなかったかを載せる。</b>場所は {@code $.items[1].qty} の形。
 * 作り直す前の型変換（Convertor）は、変換できない値を黙って null や 0 にしていた。
 * </p>
 */
public class ConversionException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/** 場所（{@code $.items[1].qty}。一番外なら {@code $}） */
	private final String path;

	/**
	 * コンストラクタ
	 *
	 * @param path		場所
	 * @param message	何ができなかったか
	 * @param cause		原因（無ければ null）
	 */
	public ConversionException (String path, String message, Throwable cause) {

		super(path + ": " + message, cause);
		this.path = path;

	}

	/**
	 * 場所
	 *
	 * @return	{@code $.items[1].qty} の形
	 */
	public String path () {

		return path;

	}

}
