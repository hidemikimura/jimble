package io.jimble.util.convertor;

/**
 * 設定情報名称クラス.
 * 
 * @author DN
 * @deprecated 変換のしかたは {@link io.jimble.converter.ConverterOptions} で決める（2.7.0。D-307）
 */
@Deprecated(since = "2.7.0")
public final class ConvertorConfigKeys {

	/** Date > String 変換ルール. */
	public static final String CONVERT_DATE_TO_STRING = "CONVERT_DATE_TO_STRING";

	/** String > Date 変換ルール. */
	public static final String CONVERT_STRING_TO_DATE = "CONVERT_STRING_TO_DATE";

	/** フィールド名無視文字ルール. */
	public static final String CONVERT_FIELDNAME_DELIMITER = "CONVERT_FIELDNAME_DELIMITER";

}
