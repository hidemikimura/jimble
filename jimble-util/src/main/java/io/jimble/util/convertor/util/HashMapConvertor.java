package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.HashMap;

/**
 * HashMap変換クラス.
 * 
 * @author DN
 */
public class HashMapConvertor implements IConvertor<HashMap<Object, Object>> {

	/** インスタンス. */
	public static final HashMapConvertor INSTANCE = new HashMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HashMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		HashMap<Object, Object> res = new HashMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);

		return res;
	}

}
