package io.jimble.db.sql.query.parameter;

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
			res.add(data.getJsonString());
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
