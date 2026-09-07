package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap変換クラス.
 * 
 * @author DN
 */
public class ConcurrentHashMapConvertor implements IConvertor<ConcurrentHashMap<Object, Object>> {

	/** インスタンス. */
	public static final ConcurrentHashMapConvertor INSTANCE = new ConcurrentHashMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConcurrentHashMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		ConcurrentHashMap<Object, Object> res = new ConcurrentHashMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);
		return res;
	}

}
