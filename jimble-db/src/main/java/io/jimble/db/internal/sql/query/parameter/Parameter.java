package io.jimble.db.internal.sql.query.parameter;

import io.jimble.db.sql.SqlBuildException;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.util.data.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * parameter
 */
public class Parameter {

	/**
	 * リストをフラットにする
	 *
	 * @param params	リスト
	 * @return	フラットリスト
	 */
	public static List<Object> flatten (List<?> params) {

		List<Object> res = new ArrayList<>();

		for (Object o : params) {
			res.addAll(flattenObject(o));
		}

		return res;

	}

	/**
	 * 1つの値を受ける場所（{@code = ?} / {@code SET x = ?} / {@code VALUES (?)} など）の値を確かめる（D-204）
	 *
	 * <p>
	 * <b>リストや配列を断る。</b>{@link #flatten} は入れ子のリストを平らにするので、
	 * {@code ?} が1つの場所にリストが来ると、<b>値だけが増えて後ろのプレースホルダーとずれる</b>。
	 * MariaDB のドライバは<b>余った値を黙って捨てる</b>ので、
	 * {@code setRow({"nickname": ["x", 999]})} に {@code .where(id.eq(me))} を足した UPDATE が
	 * <b>id = 999 の行を書き換えていた</b>（PostgreSQL のドライバは例外を投げる）。
	 * リクエストの JSON や {@code a[]=} のフォームは、そのままリストになる。
	 * </p>
	 *
	 * <p>{@code byte[]}（バイナリ）と {@code Data}（JSON の列）は1つの値として通す。</p>
	 *
	 * @param value	値
	 * @return	そのままの値
	 * @throws SqlBuildException	リストや配列のとき
	 */
	public static Object single (Object value) {

		if (value instanceof Collection<?> || (value != null && value.getClass().isArray() && !(value instanceof byte[]))) {
			throw new SqlBuildException(
				"1つの値を書く場所に、リストや配列が来ました（%s）。IN で比べるなら in(...) を使ってください"
					.formatted(value.getClass().getSimpleName()));
		}

		return value;

	}

	/**
	 * IN / NOT IN の一覧の、一つひとつの値を確かめる（D-204）
	 *
	 * <p><b>一覧の中の入れ子を断る。</b>入れ子は平らにされて、プレースホルダーの数とずれる。</p>
	 *
	 * @param values	一覧
	 * @return	そのままの一覧
	 * @throws SqlBuildException	入れ子のリストや配列があるとき
	 */
	public static <T extends Collection<?>> T singles (T values) {

		for (Object value : values) {
			single(value);
		}

		return values;

	}

	/**
	 * オブジェクトをフラットリストにする
	 *
	 * @param o	オブジェクト
	 * @return	フラットリスト
	 */
	private static List<Object> flattenObject (Object o) {

		List<Object> res = new ArrayList<>();

		if (o == null) {
			res.add(null);
		} else if (o instanceof Data data) {
			/*
			 * ここで JSON 文字列にしてはいけない（要件 F-D-30）。
			 *
			 * <b>PostgreSQL の json / jsonb 列は varchar のパラメータを受け取らない。</b>
			 * 「column x is of type jsonb but expression is of type character varying」で落ちる。
			 * Data のまま渡し、DB#setParameters で製品ごとの渡し方
			 * （{@code Dialect#bindJson}）に任せる。
			 */
			res.add(data);
		} else if (o instanceof Collection<?> list) {
			for (Object listObj : list) {
				res.addAll(flattenObject(listObj));
			}
		} else if (o instanceof byte[]) {
			// バイナリは1つの値（D-204）。かつては1バイトずつに平らにされ、プレースホルダーとずれていた
			res.add(o);
		} else if (o.getClass().isArray()) {
			// 素の配列（long[] など）も通る（要件 D-162）
			for (Object arrObj : ArrayUtil.toList(o)) {
				res.addAll(flattenObject(arrObj));
			}
		} else {
			res.add(o);
		}

		return res;

	}

}
