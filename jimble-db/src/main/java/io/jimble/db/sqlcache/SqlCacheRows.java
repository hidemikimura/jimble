package io.jimble.db.sqlcache;

import io.jimble.util.data.Data;
import io.jimble.util.internal.JsonArrayList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL の結果を、呼んだ側だけのものとして複製する（D-296）
 *
 * <p>
 * メモリの置き場は、結果を<b>書き出さずにそのまま持つ</b>。そのかわり、入れるときと渡すときに複製する。
 * 呼んだ側が受け取った行を書き換えても、置き場の中身や、ほかのリクエストに渡した行は変わらない
 * （書き出して読み戻していたころと同じ）。
 * </p>
 *
 * <p>
 * <b>中身まで複製するのは、書き換えられる型だけ</b>（{@link Data}・リスト・日時・{@code byte[]}）。
 * 書き換えられない型（文字列・数値・{@code java.time}）はそのまま使い回す。
 * <b>知らない型が入っていたら複製しない</b>（{@code null} を返す）。そのときは呼ぶ側が書き出す形に戻る。
 * </p>
 */
final class SqlCacheRows {

	/* 複製できなかったしるし */
	private static final Object UNCOPYABLE = new Object();

	private SqlCacheRows () {
	}

	/**
	 * 複製する
	 *
	 * @param rows	結果
	 * @return	複製。知らない型が入っていれば null
	 */
	static List<Data> copy (List<Data> rows) {

		List<Data> copied = new ArrayList<>(rows.size());

		for (Data row : rows) {

			if (!(copyValue(row) instanceof Data data)) {
				return null;
			}

			copied.add(data);

		}

		return copied;

	}

	/**
	 * 値を1つ複製する
	 *
	 * @param value	値
	 * @return	複製（書き換えられない型はそのもの）。複製できなければ {@link #UNCOPYABLE}
	 */
	private static Object copyValue (Object value) {

		if (value == null || isImmutable(value)) {
			return value;
		}

		if (value instanceof Data data) {
			return copyData(data);
		}

		// 型を変えない（JSON の配列の列は JsonArrayList で返る）
		if (value.getClass() == JsonArrayList.class || value.getClass() == ArrayList.class) {
			return copyList((List<?>) value);
		}

		// java.sql.Timestamp などの子も、clone で同じ型のまま複製される
		if (value instanceof Date date) {
			return date.clone();
		}

		if (value instanceof byte[] bytes) {
			return bytes.clone();
		}

		return UNCOPYABLE;

	}

	/**
	 * Data を複製する
	 *
	 * @param data	Data
	 * @return	複製。複製できなければ {@link #UNCOPYABLE}
	 */
	private static Object copyData (Data data) {

		Data copied = new Data();

		for (Map.Entry<String, Object> entry : data.entrySet()) {

			Object value = copyValue(entry.getValue());

			if (value == UNCOPYABLE) {
				return UNCOPYABLE;
			}

			copied.put(entry.getKey(), value);

		}

		return copied;

	}

	/**
	 * リストを複製する
	 *
	 * @param list	リスト（ArrayList か JsonArrayList）
	 * @return	複製。複製できなければ {@link #UNCOPYABLE}
	 */
	private static Object copyList (List<?> list) {

		List<Object> copied = list instanceof JsonArrayList ? new JsonArrayList() : new ArrayList<>(list.size());

		for (Object element : list) {

			Object value = copyValue(element);

			if (value == UNCOPYABLE) {
				return UNCOPYABLE;
			}

			copied.add(value);

		}

		return copied;

	}

	/**
	 * 書き換えられない型か
	 *
	 * @param value	値（null ではない）
	 * @return	書き換えられない場合 = true
	 */
	private static boolean isImmutable (Object value) {

		return value instanceof String
			|| value instanceof Integer
			|| value instanceof Long
			|| value instanceof Boolean
			|| value instanceof BigDecimal
			|| value instanceof Double
			|| value instanceof Float
			|| value instanceof Short
			|| value instanceof Byte
			|| value instanceof Character
			|| value instanceof BigInteger
			|| value instanceof UUID
			|| value instanceof Enum<?>
			|| value.getClass().getPackageName().equals("java.time");

	}

}
