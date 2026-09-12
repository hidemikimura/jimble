package io.jimble.db.internal.sql.query.parameter;

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
		} else if (o.getClass().isArray()) {
			Object[] arr = (Object[]) o;
			for (Object arrObj : arr) {
				res.addAll(flattenObject(arrObj));
			}
		} else {
			res.add(o);
		}

		return res;

	}

}
