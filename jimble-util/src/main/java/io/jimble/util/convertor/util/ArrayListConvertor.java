package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.ArrayList;

/**
 * ArrayList変換クラス.
 * 
 * @author DN
 */
public class ArrayListConvertor implements IConvertor<ArrayList<Object>> {

	/** インスタンス. */
	public static final ArrayListConvertor INSTANCE = new ArrayListConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ArrayList<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		ArrayList<Object> res = new ArrayList<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}
}
