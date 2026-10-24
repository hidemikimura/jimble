package io.jimble.json;

/**
 * 型ごとの書き方（D-307）
 *
 * <p>
 * {@link JsonWriteOptions.Builder#typeWriter(Class, JsonTypeWriter)} で足す。
 * 書き手は、決まった型（文字列・数値・Map・List・日時など）より先に、足した書き方を見る。
 * </p>
 *
 * <pre>{@code
 * JsonWriteOptions options = JsonWriteOptions.builder()
 *     .typeWriter(Money.class, (value, out) -> out.string(((Money) value).format()))
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface JsonTypeWriter {

	/**
	 * 値を書く
	 *
	 * @param value	書く値（null ではない。足したときの型のインスタンス）
	 * @param out	書き先
	 */
	void write (Object value, Output out);

	/**
	 * 書き先
	 */
	interface Output {

		/**
		 * 値を書く（ふつうの書き方で。入れ子の Map や Bean も書ける）
		 *
		 * @param value	値（null も可）
		 */
		void value (Object value);

		/**
		 * 値を、足した書き方を通さずに書く（決まった書き方で。自分の型を受け取った書き方から、同じ値を書くとき）
		 *
		 * @param value	値（null も可）
		 */
		void plain (Object value);

		/**
		 * 文字列を書く（クォートで囲み、エスケープする）
		 *
		 * @param value	文字列（null なら null）
		 */
		void string (CharSequence value);

		/**
		 * JSON をそのまま書く（エスケープしない。正しい JSON であること）
		 *
		 * @param json	JSON
		 */
		void raw (CharSequence json);

	}

}
