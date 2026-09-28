package io.jimble.util.data;

/**
 * {@link Data} の値を、求めた型に変えられなかった（要件 D-191）
 *
 * <p>
 * 既定値つきの取り出し（{@link Data#getInt(String, int)} など）が投げる。
 * <b>「無い」と「読めない」を分ける</b>ためにある——既定値を返すのは無いときだけで、
 * {@code "abc"} や {@code "1.5"} を int として読もうとしたら、黙って既定値にせずここで止まる。
 * </p>
 *
 * @since 1.5.0
 */
public class DataConversionException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/* キー */
	private final String key;

	/**
	 * コンストラクタ
	 *
	 * @param key		キー
	 * @param value		値
	 * @param type		求めた型
	 */
	public DataConversionException (String key, Object value, Class<?> type) {

		super("%s の値を %s として読めません: %s（%s）".formatted(
			key, type.getSimpleName(), abbreviate(value), value == null ? "null" : value.getClass().getSimpleName()));
		this.key = key;

	}

	/**
	 * キー
	 *
	 * @return	キー
	 */
	public String key () {

		return key;

	}

	private static String abbreviate (Object value) {

		String text = String.valueOf(value);
		return text.length() > 80 ? text.substring(0, 80) + "…" : text;

	}

}
