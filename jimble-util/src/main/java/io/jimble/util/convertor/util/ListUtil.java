package io.jimble.util.convertor.util;

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

		if (!conf.hashSet.add(hash)) {
			return;
		}
		conf.Hierarchy++;

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

			for (Object o : (Object[]) obj) {
				list.add(Convertor.convert(conf, o, args));
			}

		} else if (obj instanceof String) {

			String json = (String) obj;
			if (json.startsWith("[") && json.endsWith("]")) {

				List<Object> jsonList = Dson.decodes(json, List.class);
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

		conf.Hierarchy--;
		conf.hashSet.remove(hash);

	}

}
