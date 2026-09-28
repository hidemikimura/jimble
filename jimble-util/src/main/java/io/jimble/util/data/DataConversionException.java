package io.jimble.util.data;

/**
 * {@link Data} の値を、求めた型に変えられなかった（要件 D-191）
 *
 * <p>
 * {@link Data} の取り出し（{@code getInt} / {@code getDate} / {@code getEnum} / {@code getData} ほか）が投げる。
 * <b>「無い」と「読めない」を分ける</b>ためにある——既定値（0 / false / null）を返すのは無いときだけで、
 * {@code "abc"} や {@code "1.5"} を int として読もうとしたら、黙って既定値にせずここで止まる。
 * 1.5 は既定値つきの版だけ、2.0 は全部の取り出しがこう読む（要件 D-195）。
 * </p>
 *
 * <p>
 * <b>利用者の入力を読むなら、先に検査する</b>（{@code ValidationRules}）。検査せずに読んで例外になると 500 になる。
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
