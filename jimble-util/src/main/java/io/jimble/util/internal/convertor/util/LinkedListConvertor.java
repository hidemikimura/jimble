package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.LinkedList;

/**
 * LinkedList変換クラス.
 * 
 * @author DN
 */
public class LinkedListConvertor implements IConvertor<LinkedList<Object>> {

	/** インスタンス. */
	public static final LinkedListConvertor INSTANCE = new LinkedListConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LinkedList<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		LinkedList<Object> res = new LinkedList<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
