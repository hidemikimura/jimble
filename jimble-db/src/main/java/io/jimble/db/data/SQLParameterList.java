package io.jimble.db.data;

import java.util.ArrayList;

/**
 * SQLパラメータリスト
 */
public class SQLParameterList extends ArrayList<Object> {

	/**
	 * コンストラクタ
	 *
	 * @param objs	引数一覧
	 */
	public SQLParameterList(Object...objs) {

		if (objs != null) {
			for (Object obj : objs) {
				add(obj);
			}
		}

	}

	public void addAll (Object...objs) {

		if (objs != null) {
			for (Object obj : objs) {
				add(obj);
			}
		}

	}

}
