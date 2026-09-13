package io.jimble.util.internal.convertor.util;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.json.Dson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapユーティリティクラス.
 * 
 * @author DN
 */
public final class MapUtil {

	/**
	 * 値をMapに変換する.
	 * 
	 * @param conf 設定情報
	 * @param obj オブジェクト
	 * @param map 変換後Map
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @throws Exception 例外
	 */
	public static void convertMap(Configration conf, Object obj, Map<Object, Object> map, Class< ? >... destClasses) throws Exception {

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

		Class< ? >[] argsKey = null;
		if (destClasses.length > 1) {
			argsKey = getParamClasses(destClasses);
		} else {
			argsKey = new Class< ? >[] { Object.class };
		}

		Class< ? >[] argsValue = null;
		if (destClasses.length > argsKey.length + 1) {
			argsValue = new Class< ? >[destClasses.length - 1 - argsKey.length];
			System.arraycopy(destClasses, 1 + argsKey.length, argsValue, 0, argsValue.length);
		} else {
			argsValue = new Class< ? >[] { Object.class };
		}

		if (obj instanceof Map< ? , ? >) {

			Map< ? , ? > m = (Map< ? , ? >) obj;
			for (Object o : m.keySet()) {
				Object key = Convertor.convert(conf, o, argsKey);
				Object value = Convertor.convert(conf, m.get(o), argsValue);
				map.put(key, value);
			}

		} else if (obj instanceof Iterable< ? >) {

			Iterable< ? > it = (Iterable< ? >) obj;
			int index = 0;
			for (Object o : it) {
				Object key = Convertor.convert(conf, String.valueOf(index++), argsKey);
				Object value = Convertor.convert(conf, o, argsValue);
				map.put(key, value);
			}

		} else if (obj.getClass().isArray()) {

			int index = 0;
			for (Object o : ArrayUtil.toList(obj)) {
				Object key = Convertor.convert(conf, String.valueOf(index++), argsKey);
				Object value = Convertor.convert(conf, o, argsValue);
				map.put(key, value);
			}

		} else if (obj instanceof String) {

			String json = (String) obj;
			if (json.startsWith("{") && json.endsWith("}")) {

				Map<String, Object> jsonMap = Dson.decodes(json, Map.class, String.class, Object.class);
				for (String o : jsonMap.keySet()) {
					Object key = Convertor.convert(conf, o, argsKey);
					Object value = Convertor.convert(conf, jsonMap.get(o), argsValue);
					map.put(key, value);
				}

			}

		} else {

			List<PropertyUtil.MethodFieldInfo> names = PropertyUtil.getFieldNames(obj.getClass());
			for (PropertyUtil.MethodFieldInfo info : names) {
				Object v = info.getProperty(obj);
				map.put(info.getFieldName(), v);
			}

		}

		/** 循環参照後処理. */

		conf.hierarchy(conf.hierarchy() - 1);
		conf.hashSet().remove(hash);

	}

	/**
	 * Mapのキーに該当する型パラメータを取得する.
	 * 
	 * @param destClasses 型パラメータ一覧
	 * @return 型パラメータ
	 */
	public static Class< ? >[] getParamClasses(Class< ? >[] destClasses) {

		List<Class< ? >> res = new ArrayList<Class< ? >>();

		int count = 0;

		for (Class< ? > cls : destClasses) {

			if (count-- > 0) {
				res.add(cls);
			}

			if (PropertyUtil.isAssignableFrom(Map.class, cls)) {
				count += 2;
			} else if (Iterable.class.isAssignableFrom(cls)) {
				count += 1;
			}

			if (count <= 0) {
				break;
			}

		}

		return res.toArray(new Class< ? >[0]);

	}

}
