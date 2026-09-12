package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.ArrayDeque;

/**
 * ArrayDeque変換クラス.
 * 
 * @author DN
 */
public class ArrayDequeConvertor implements IConvertor<ArrayDeque<Object>> {

	/** インスタンス. */
	public static final ArrayDequeConvertor INSTANCE = new ArrayDequeConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ArrayDeque<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		ArrayDeque<Object> res = new ArrayDeque<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
