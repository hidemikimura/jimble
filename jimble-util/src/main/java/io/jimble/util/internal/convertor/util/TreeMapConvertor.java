package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.TreeMap;

/**
 * TreeMap変換クラス.
 * 
 * @author DN
 */
public class TreeMapConvertor implements IConvertor<TreeMap<Object, Object>> {

	/** インスタンス. */
	public static final TreeMapConvertor INSTANCE = new TreeMapConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TreeMap<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		TreeMap<Object, Object> res = new TreeMap<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);

		return res;
	}

}
