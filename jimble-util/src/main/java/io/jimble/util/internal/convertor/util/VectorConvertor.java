package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.Vector;

/**
 * Vector変換クラス.
 * 
 * @author DN
 */
public class VectorConvertor implements IConvertor<Vector<Object>> {

	/** インスタンス. */
	public static final VectorConvertor INSTANCE = new VectorConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Vector<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Vector<Object> res = new Vector<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
