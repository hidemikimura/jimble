package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * LinkedBlockingDeque変換クラス.
 * 
 * @author DN
 */
public class LinkedBlockingDequeConvertor implements IConvertor<LinkedBlockingDeque<Object>> {

	/** インスタンス. */
	public static final LinkedBlockingDequeConvertor INSTANCE = new LinkedBlockingDequeConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LinkedBlockingDeque<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		LinkedBlockingDeque<Object> res = new LinkedBlockingDeque<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
