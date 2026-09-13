package io.jimble.util.internal.convertor.util;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Listユーティリティクラス.
 *
 * @author DN
 */
public final class ListUtil {

	/**
	 * 値をListに変換する.
	 *
	 * @param conf 設定情報
	 * @param obj オブジェクト
	 * @param list 変換後List
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @throws Exception 例外
	 */
	public static void convertList(Configration conf, Object obj, Collection<Object> list, Class< ? >... destClasses) throws Exception {

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			return;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet().add(hash)) {
			return;
		}
		conf.hierarchy(conf.hierarchy() + 1);

		/** 変換処理. */

		Class< ? >[] args = null;
		if (destClasses.length > 1) {
			args = new Class< ? >[destClasses.length - 1];
			System.arraycopy(destClasses, 1, args, 0, args.length);
		} else {
			args = new Class< ? >[] { Object.class };
		}

		Class< ? >[] argsCommonBean = new Class< ? >[] { Data.class };

		if (obj instanceof Iterable< ? >) {

			for (Object o : (Iterable< ? >) obj) {
				list.add(Convertor.convert(conf, o, args));
			}

		} else if (obj instanceof Map< ? , ? >) {

			for (Object o : ((Map< ? , ? >) obj).values()) {
				list.add(Convertor.convert(conf, o, args));
			}

		} else if (obj.getClass().isArray()) {

			for (Object o : ArrayUtil.toList(obj)) {
				list.add(Convertor.convert(conf, o, args));
			}

		} else if (obj instanceof String) {

			String json = (String) obj;
			if (json.startsWith("[") && json.endsWith("]")) {

				/*
				 * <b>生の {@code List} が返る（D-173）。</b>
				 * {@code Class<T>} を受ける版が選ばれるので、{@code List.class} からは
				 * <b>型引数が分からない</b>——JSON の配列なので中身は何でも入りうる。
				 * ここは要素を1つずつ {@code Object} として回すだけなので、それでよい。
				 */
				List<?> jsonList = Dson.decodes(json, List.class);
				for (Object o : jsonList) {
					list.add(Convertor.convert(conf, o, args));
				}

			} else {

				list.add(Convertor.convert(conf, obj, args));

			}

		} else {

			list.add(Convertor.convert(conf, obj, args));

		}

		/** 循環参照後処理. */

		conf.hierarchy(conf.hierarchy() - 1);
		conf.hashSet().remove(hash);

	}

}
