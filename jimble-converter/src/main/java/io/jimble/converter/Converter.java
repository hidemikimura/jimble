package io.jimble.converter;

import io.jimble.converter.internal.ConversionEngine;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 値を型にする（D-307）
 *
 * <pre>{@code
 * Order order = Converter.convert(map, Order.class);                       // Map → record / Bean
 * List<Item> items = Converter.convert(rows, new TypeRef<List<Item>>() {}); // List<Map> → List<record>
 * int qty = Converter.convert("１,２３４", int.class);                        // 1234
 * }</pre>
 *
 * <ul>
 *   <li>Map / Bean / record → Bean / record：キーで項目に入れる。知らないキーは飛ばす。
 *       Bean は setter（無ければフィールド）に、record は正準のコンストラクタに入れる</li>
 *   <li>Bean / record → Map：項目を宣言した順に入れる</li>
 *   <li>コレクション・配列：要素を1つずつ型にする</li>
 *   <li>1つの値：全角の数字・{@code "1,234"}・{@code 1.0} → int・{@code yes} / {@code on} → true・enum の名前（大文字小文字は問わない）・
 *       いろいろな形の日時を受け付ける。{@code 1.5} → int・桁あふれ・ありえない日付は {@link ConversionException}</li>
 * </ul>
 */
public final class Converter {

	private Converter () {
	}

	/**
	 * 値を型にする（既定の変換のしかた）
	 *
	 * @param value	値
	 * @param type	型
	 * @param <T>	型
	 * @return	型にした値（null なら null。プリミティブなら 0 / false）
	 * @throws ConversionException	できなかったとき
	 */
	public static <T> T convert (Object value, Class<T> type) {

		return convert(value, type, ConverterOptions.DEFAULT);

	}

	/**
	 * 値を型にする
	 *
	 * @param value		値
	 * @param type		型
	 * @param options	変換のしかた
	 * @param <T>		型
	 * @return	型にした値
	 * @throws ConversionException	できなかったとき
	 */
	@SuppressWarnings("unchecked")
	public static <T> T convert (Object value, Class<T> type, ConverterOptions options) {

		Object result = convert(value, (Type) type, options);
		return type.isPrimitive() ? (T) result : type.cast(result);

	}

	/**
	 * 値を、型の引数を持つ型にする（{@code new TypeRef<List<Item>>() {}}）
	 *
	 * @param value	値
	 * @param type	型
	 * @param <T>	型
	 * @return	型にした値
	 * @throws ConversionException	できなかったとき
	 */
	public static <T> T convert (Object value, TypeRef<T> type) {

		return convert(value, type, ConverterOptions.DEFAULT);

	}

	/**
	 * 値を、型の引数を持つ型にする
	 *
	 * @param value		値
	 * @param type		型
	 * @param options	変換のしかた
	 * @param <T>		型
	 * @return	型にした値
	 * @throws ConversionException	できなかったとき
	 */
	@SuppressWarnings("unchecked")
	public static <T> T convert (Object value, TypeRef<T> type, ConverterOptions options) {

		return (T) convert(value, type.type(), options);

	}

	/**
	 * 値を型（{@link Type}）にする
	 *
	 * @param value		値
	 * @param type		型
	 * @param options	変換のしかた
	 * @return	型にした値
	 * @throws ConversionException	できなかったとき
	 */
	public static Object convert (Object value, Type type, ConverterOptions options) {

		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(options, "options");

		return new ConversionEngine(options).convert(value, type);

	}

}
