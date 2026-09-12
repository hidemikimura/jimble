package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.util.*;

/**
 * Object変換クラス.
 *
 * @author DN
 */
public class ObjectConvertor implements IConvertor<Object> {

	/** インスタンス. */
	public static final ObjectConvertor INSTANCE = new ObjectConvertor();

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Object convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Collection< ? >) {

			Collection<Object> res = null;
			try {
				res = (Collection<Object>) PropertyUtil.newInstance(obj.getClass());
				for (Object k : (List< ? >) obj) {
					res.add(Convertor.convert(conf, k, Object.class));
				}
			} catch (Exception e) {
				res = new ArrayList<Object>();
				for (Object k : (List< ? >) obj) {
					res.add(Convertor.convert(conf, k, Object.class));
				}
			}

			return res;

		} else if (obj instanceof Map< ? , ? >) {

			Map<Object, Object> res = null;
			try {
				res = (Map<Object, Object>) PropertyUtil.newInstance(obj.getClass());
			} catch (Exception e) {
				res = new LinkedHashMap<Object, Object>();
			}

			Map< ? , ? > map = (Map< ? , ? >) obj;
			for (Object k : map.keySet()) {
				Object key = Convertor.convert(conf, k, Object.class);
				Object value = Convertor.convert(conf, map.get(k), Object.class);
				res.put(key, value);
			}
			return res;

		}

		return obj;
	}

}
