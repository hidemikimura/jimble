package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * ConcurrentSkipListMap変換クラス.
 * 
 * @author DN
 */
public class ConcurrentSkipListMapConvertor implements IConvertor<ConcurrentSkipListMap<Object, Object>> {

	/** インスタンス. */
	public static final ConcurrentSkipListMapConvertor INSTANCE = new ConcurrentSkipListMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConcurrentSkipListMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		ConcurrentSkipListMap<Object, Object> res = new ConcurrentSkipListMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);

		return res;
	}

}
