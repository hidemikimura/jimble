package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.Stack;

/**
 * Stack変換クラス.
 * 
 * @author DN
 */
public class StackConvertor implements IConvertor<Stack<Object>> {

	/** インスタンス. */
	public static final StackConvertor INSTANCE = new StackConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Stack<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Stack<Object> res = new Stack<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
