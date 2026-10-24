package io.jimble.util.convertor;

import io.jimble.util.internal.convertor.Conversions;

/**
 * 変換クラス.
 *
 * <p>
 * <b>2.7.0 で deprecated。</b>型変換は {@code jimble-converter} の {@link io.jimble.converter.Converter} で行う
 * （2.7.0 からは、このクラスも中身は {@code Converter} である）。
 * </p>
 *
 * <ul>
 *   <li>{@code Convertor.convert(null, map, Order.class)} → {@code Converter.convert(map, Order.class)}</li>
 *   <li>{@code Convertor.convert(null, rows, List.class, Item.class)} → {@code Converter.convert(rows, new TypeRef<List<Item>>() {})}</li>
 *   <li>{@code Data} からは {@code data.convertClass(Order.class)} / {@code getIntList} などの getter</li>
 *   <li>{@link #addConvertor(Class, IConvertor)} → {@link io.jimble.converter.ConverterOptions.Builder#typeConverter(Class, io.jimble.converter.TypeConverter)}</li>
 * </ul>
 *
 * @author DN
 * @deprecated {@link io.jimble.converter.Converter} を使う（2.7.0。D-307）
 */
@Deprecated(since = "2.7.0")
public final class Convertor {

	/**
	 * オブジェクトを変換する.<br>
	 * 型パラメータを指定する場合は必ず全ての型パラメータを指定してください.<br>
	 * 例) {@code Map<String, Map<String, Integer>>} → {@code new Class<?>[]{ Map.class, String.class, Map.class, String.class, Integer.class }}
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @param <T>         変換先の型
	 * @return 変換後オブジェクト
	 * @throws RuntimeException 変換できなかったとき（2.0。1.x は検査例外の Exception。要件 D-197）
	 * @deprecated {@link io.jimble.converter.Converter#convert(Object, Class)} を使う
	 */
	@Deprecated(since = "2.7.0")
	public static <T> T convert (Configration conf, Object src, Class<?>... destClasses) {

		return Conversions.convert(conf, src, destClasses);

	}

	/**
	 * オブジェクトを変換する.<br>
	 * 型パラメータを指定する場合は必ず全ての型パラメータを指定してください.<br>
	 * 例) {@code Map<String, Map<String, Integer>>} → {@code new Class<?>[]{ Map.class, String.class, Map.class, String.class, Integer.class }}
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param useCustom   カスタム変換オブジェクト使用判定
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @param <T>         変換先の型
	 * @return 変換後オブジェクト
	 * @throws RuntimeException 変換できなかったとき（2.0。1.x は検査例外の Exception。要件 D-197）
	 * @deprecated {@link io.jimble.converter.Converter#convert(Object, Class)} を使う
	 */
	@Deprecated(since = "2.7.0")
	public static <T> T convert (Configration conf, Object src, boolean useCustom, Class<?>... destClasses) {

		return Conversions.convert(conf, src, useCustom, destClasses);

	}

	/**
	 * 変換インスタンスを取得する.
	 *
	 * @param conf      設定情報
	 * @param useCustom カスタム変換オブジェクト使用判定
	 * @param cls       変換希望クラス
	 * @return 変換インスタンス
	 * @deprecated 古い変換器を取り出す口。{@link io.jimble.converter.Converter} を使う
	 */
	@Deprecated(since = "2.7.0")
	public static IConvertor<?> getConvertor (Configration conf, boolean useCustom, Class<?> cls) {

		return Conversions.getConvertor(conf, useCustom, cls);

	}

	/**
	 * 変換定義を追加する.
	 *
	 * <p>足した型への変換は、{@code Data} の getter などからも、足した変換器で行う。</p>
	 *
	 * @param clz       変換対象クラス
	 * @param convertor 変換処理オブジェクト
	 * @deprecated {@link io.jimble.converter.ConverterOptions.Builder#typeConverter(Class, io.jimble.converter.TypeConverter)} で足し、
	 *             {@link io.jimble.converter.Converter} に渡す
	 */
	@Deprecated(since = "2.7.0")
	public static void addConvertor (Class<?> clz, IConvertor<?> convertor) {

		Conversions.addConvertor(clz, convertor);

	}

}
