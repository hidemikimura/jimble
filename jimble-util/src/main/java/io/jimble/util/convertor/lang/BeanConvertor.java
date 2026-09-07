package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.convertor.array.ArrayConvertor;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/**
 * Bean変換クラス.
 *
 * @author DN
 */
public class BeanConvertor<E> implements IConvertor<E> {

	/**
	 * インスタンス.
	 */
	public static final BeanConvertor<Object> INSTANCE = new BeanConvertor<Object>();

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public E convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (destClasses[0].isArray()) {
			return (E) ArrayConvertor.INSTANCE.convert(conf, obj, destClasses);
		}

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			return null;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet.add(hash)) {
			return null;
		}
		conf.Hierarchy++;

		/** 変換処理. */

		Object res = PropertyUtil.newInstance(destClasses[0]);

		if (obj instanceof Map<?, ?>) {

			Map<?, ?> map = (Map<?, ?>) obj;
			for (Object k : map.keySet()) {
				Object v = map.get(k);
				if (conf.isOutputNullValue || v != null) {
					PropertyUtil.setProperty(conf, res, PropertyUtil.createFieldName(PropertyUtil.toString(k), conf), v);
				}
			}

		} else {

			boolean isConverted = false;
			if (obj != null && obj.getClass().isArray() && Array.getLength(obj) > 0) {
				Object _obj = Array.get(obj, 0);
				if (_obj instanceof String) {
					String jsonString = (String) _obj;
					try {
						res = Dson.decodes(jsonString, Data.class);
						isConverted = true;
					} catch (Exception ex) {
					}
				}
			}

			if (!isConverted) {
				List<PropertyUtil.MethodFieldInfo> names = PropertyUtil.getFieldNames(obj.getClass());

				for (PropertyUtil.MethodFieldInfo info : names) {
					Object v = info.getProperty(obj);
					if (conf.isOutputNullValue || v != null) {
						PropertyUtil.setProperty(conf, res, info.getFieldName(), v);
					}
				}
			}

		}

		/** 循環参照後処理. */

		conf.Hierarchy--;
		conf.hashSet.remove(hash);

		return (E) res;
	}

}
