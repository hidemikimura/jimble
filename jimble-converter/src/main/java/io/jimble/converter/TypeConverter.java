package io.jimble.converter;

/**
 * 型ごとの変換のしかた（D-307）
 *
 * <p>
 * {@link ConverterOptions.Builder#typeConverter(Class, TypeConverter)} で足す。
 * 変換は、決まった変換（数・日時・Bean など）より先に、足した変換のしかたを見る。
 * 値がもうその型なら、呼ばずにそのまま返す。
 * </p>
 *
 * <pre>{@code
 * ConverterOptions options = ConverterOptions.builder()
 *     .typeConverter(Money.class, value -> Money.parse(value.toString()))
 *     .build();
 * }</pre>
 *
 * @param <T>	変換先の型
 */
@FunctionalInterface
public interface TypeConverter<T> {

	/**
	 * 値を型にする
	 *
	 * @param value	値（null ではない）
	 * @return	型にした値
	 * @throws IllegalArgumentException	できないとき（場所を付けた {@link ConversionException} になる）
	 */
	T convert (Object value);

}
