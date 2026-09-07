package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.LinkedHashMap;

/**
 * LinkedHashMap変換クラス.
 * 
 * @author DN
 */
public class LinkedHashMapConvertor implements IConvertor<LinkedHashMap<Object, Object>> {

	/** インスタンス. */
	public static final LinkedHashMapConvertor INSTANCE = new LinkedHashMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LinkedHashMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		LinkedHashMap<Object, Object> res = new LinkedHashMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);

		return res;
	}

}
