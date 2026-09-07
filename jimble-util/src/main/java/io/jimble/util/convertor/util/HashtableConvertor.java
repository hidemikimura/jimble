package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.Hashtable;

/**
 * Hashtable変換クラス.
 * 
 * @author DN
 */
public class HashtableConvertor implements IConvertor<Hashtable<Object, Object>> {

	/** インスタンス. */
	public static final HashtableConvertor INSTANCE = new HashtableConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hashtable<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Hashtable<Object, Object> res = new Hashtable<Object, Object>();
		MapUtil.convertMap(conf, obj, res, destClasses);
		return res;
	}

}
