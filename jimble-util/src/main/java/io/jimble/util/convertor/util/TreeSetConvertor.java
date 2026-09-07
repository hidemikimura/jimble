package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.TreeSet;

/**
 * TreeSet変換クラス.
 * 
 * @author DN
 */
public class TreeSetConvertor implements IConvertor<TreeSet<Object>> {

	/** インスタンス. */
	public static final TreeSetConvertor INSTANCE = new TreeSetConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TreeSet<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		TreeSet<Object> res = new TreeSet<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
