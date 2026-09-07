package io.jimble.util.convertor.array;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.IConvertor;

import java.lang.reflect.Array;
import java.util.Map;

/**
 * 配列変換クラス.
 * 
 * @author DN
 */
public class ArrayConvertor implements IConvertor<Object> {

	/** インスタンス. */
	public static final ArrayConvertor INSTANCE = new ArrayConvertor();

	/**
	 * {@inheritDoc}.
	 */
	@Override
	public Object convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Class< ? > arrayCls = null;
		if (destClasses.length > 0) {
			arrayCls = destClasses[0].getComponentType();
		} else {
			arrayCls = Object[].class;
		}

		Object res = Array.newInstance(arrayCls, getSize(obj));

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			return res;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet.add(hash)) {
			return res;
		}
		conf.Hierarchy++;

		/** 変換処理. */

		Class< ? >[] args = null;
		if (destClasses.length > 0) {
			Class< ? > cCls = destClasses[0].getComponentType();
			args = new Class< ? >[] { cCls == null ? Object.class : cCls };
		} else {
			args = new Class< ? >[] { Object.class };
		}

		if (obj instanceof Iterable< ? >) {

			int index = 0;
			for (Object o : (Iterable< ? >) obj) {
				Array.set(res, index++, Convertor.convert(conf, o, args));
			}

		} else if (obj instanceof Map< ? , ? >) {

			int index = 0;
			for (Object o : ((Map< ? , ? >) obj).values()) {
				Array.set(res, index++, Convertor.convert(conf, o, args));
			}

		} else if (obj.getClass().isArray()) {

			for (int i = 0; i < getSize(obj); i++) {
				Object o = Array.get(obj, i);
				Array.set(res, i, Convertor.convert(conf, o, args));
			}

		} else {

			Array.set(res, 0, Convertor.convert(conf, obj, args));

		}

		/** 循環参照後処理. */

		conf.Hierarchy--;
		conf.hashSet.remove(hash);

		return res;
	}

	/**
	 * オブジェクトサイズを取得する.
	 * 
	 * @param obj オブジェクト
	 * @return オブジェクトサイズ
	 */
	@SuppressWarnings("unused")
	public static int getSize(Object obj) {

		int res = 0;

		if (obj instanceof Iterable< ? >) {

			for (Object o : (Iterable< ? >) obj) {
				res++;
			}

		} else if (obj instanceof Map< ? , ? >) {

			res = ((Map< ? , ? >) obj).size();

		} else if (obj.getClass().isArray()) {

			res = Array.getLength(obj);

		} else {

			res = 1;

		}

		return res;

	}

}
