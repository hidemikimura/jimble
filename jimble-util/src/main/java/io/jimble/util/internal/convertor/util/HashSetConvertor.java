package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.HashSet;

/**
 * HashSet変換クラス.
 * 
 * @author DN
 */
public class HashSetConvertor implements IConvertor<HashSet<Object>> {

	/** インスタンス. */
	public static final HashSetConvertor INSTANCE = new HashSetConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HashSet<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		HashSet<Object> res = new HashSet<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
