package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.LinkedHashSet;

/**
 * LinkedHashSet変換クラス.
 * 
 * @author DN
 */
public class LinkedHashSetConvertor implements IConvertor<LinkedHashSet<Object>> {

	/** インスタンス. */
	public static final LinkedHashSetConvertor INSTANCE = new LinkedHashSetConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LinkedHashSet<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		LinkedHashSet<Object> res = new LinkedHashSet<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
