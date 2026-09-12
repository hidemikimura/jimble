package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * LinkedBlockingQueue変換クラス.
 * 
 * @author DN
 */
public class LinkedBlockingQueueConvertor implements IConvertor<LinkedBlockingQueue<Object>> {

	/** インスタンス. */
	public static final LinkedBlockingQueueConvertor INSTANCE = new LinkedBlockingQueueConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LinkedBlockingQueue<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		LinkedBlockingQueue<Object> res = new LinkedBlockingQueue<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
