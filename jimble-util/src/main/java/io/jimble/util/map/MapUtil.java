package io.jimble.util.map;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.data.Data;

import java.util.*;

public class MapUtil {

	public static void main (String[] args) {

		Data src = Data.fromJsonString("""
		{
			"a": "1"
			, "b": {
				"b-1": "1"
				, "b-2": [1, 2]
			}
		}
		""");
		Data add = Data.fromJsonString("""
		{
			"a": "2"
			, "b": {
				"b-1": "2"
				, "b-2": [3, 4]
			}
		}
		""");

		/**
		 * {
		 *     "a": ["1", "2"]
		 *     , "b": {
		 *         "b-1": ["1", "2"]
		 *         , "b-2": [1, 2, 3, 4]
		 *     }
		 * }
		 */
		Data merge = mergeData(src, add);

		System.out.println(merge);

	}

	/**
	 * Dataをマージする
	 *
	 * @param src   Data
	 * @param add   Data
	 * @return  Data
	 */
	public static Data mergeData (Data src, Data add) {

		Map<?, ?> merge = merge(src, add);
		try {
			return Convertor.convert(null, merge, Data.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * Mapをマージする
	 *
	 * @param src   Map
	 * @param add   Map
	 * @return  Map
	 */
	public static Map<?, ?> merge (Map<?, ?> src, Map<?, ?> add) {

		/*
		 * LinkedHashMap にする。移送元は HashMap だったので、
		 * Data（挿入順を保つ。要件 F-D-20）をマージすると順番が失われていた。
		 */
		Map<Object, Object> newSrc = new LinkedHashMap<>();
		for (Object key : add.keySet()) {

			if (!src.containsKey(key)) {
				newSrc.put(key, add.get(key));
				continue;
			}

			Object srcValue = src.get(key);
			Object addValue = add.get(key);
			newSrc.put(key, mergeObject(srcValue, addValue));

		}
		for (Object key : src.keySet()) {

			if (!add.containsKey(key)) {
				newSrc.put(key, src.get(key));
			}

		}

		return newSrc;

	}

	/**
	 * Objectをマージする
	 *
	 * @param src   Object
	 * @param add   Object
	 * @return  Object
	 */
	private static Object mergeObject (Object src, Object add) {

		if (src == null) {
			return add;
		} else if (add == null) {
			return src;
		} else if (src instanceof Map<?, ?> srcMap) {
			return mergeMap(srcMap, add);
		} else if (src instanceof Collection<?> srcCollection) {
			return mergeCollection(srcCollection, add);
		} else if (src.getClass().isArray()) {
			return mergeArray(ArrayUtil.toList(src), add);
		} else {
			List<Object> newList = new ArrayList<>();
			newList.add(src);
			newList.add(add);
			return newList;
		}

	}

	/**
	 * Mapにマージする
	 *
	 * @param src   Map
	 * @param add   Object
	 * @return  Map
	 */
	@SuppressWarnings("unchecked")
	private static Object mergeMap (Map<?, ?> src, Object add) {

		Map<Object, Object> srcMap = (Map<Object, Object>) src;
		if (add instanceof Map<?, ?> addMap) {
			for (Object key : addMap.keySet()) {
				if (!srcMap.containsKey(key)) {
					srcMap.put(key, addMap.get(key));
				} else {
					srcMap.put(key, mergeObject(srcMap.get(key), addMap.get(key)));
				}
			}
		}

		return srcMap;

	}

	/**
	 * Collectionにマージする
	 *
	 * @param src   Collection
	 * @param add   Object
	 * @return  Collection
	 */
	@SuppressWarnings("unchecked")
	private static Object mergeCollection (Collection<?> src, Object add) {

		Collection<Object> srcCollection = (Collection<Object>) src;
		if (add instanceof Collection<?> addCollection) {
			srcCollection.addAll(addCollection);
		} else if (add.getClass().isArray()) {
			srcCollection.addAll(ArrayUtil.toList(add));
		} else {
			srcCollection.add(add);
		}

		return srcCollection;

	}

	/**
	 * Arrayにマージする
	 *
	 * @param src   Array
	 * @param add   Object
	 * @return  Array
	 */
	private static List<Object> mergeArray (List<Object> src, Object add) {

		List<Object> newList = new ArrayList<>(src);
		if (add.getClass().isArray()) {
			newList.addAll(ArrayUtil.toList(add));
		} else if (add instanceof Collection<?> addCollection) {
			newList.addAll(addCollection);
		} else {
			newList.add(add);
		}

		return newList;

	}

}
