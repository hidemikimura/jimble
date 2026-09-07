package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ConcurrentLinkedQueue変換クラス.
 * 
 * @author DN
 */
public class ConcurrentLinkedQueueConvertor implements IConvertor<ConcurrentLinkedQueue<Object>> {

	/** インスタンス. */
	public static final ConcurrentLinkedQueueConvertor INSTANCE = new ConcurrentLinkedQueueConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConcurrentLinkedQueue<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		ConcurrentLinkedQueue<Object> res = new ConcurrentLinkedQueue<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
