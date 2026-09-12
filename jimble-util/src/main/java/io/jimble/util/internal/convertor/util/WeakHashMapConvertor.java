package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.WeakHashMap;

/**
 * WeakHashMap変換クラス.
 * 
 * @author DN
 */
public class WeakHashMapConvertor implements IConvertor<WeakHashMap<Object, Object>> {

	/** インスタンス. */
	public static final WeakHashMapConvertor INSTANCE = new WeakHashMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public WeakHashMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		WeakHashMap<Object, Object> res = new WeakHashMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);
		return res;
	}

}
