package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * PriorityBlockingQueue変換クラス.
 * 
 * @author DN
 */
public class PriorityBlockingQueueConvertor implements IConvertor<PriorityBlockingQueue<Object>> {

	/** インスタンス. */
	public static final PriorityBlockingQueueConvertor INSTANCE = new PriorityBlockingQueueConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PriorityBlockingQueue<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		PriorityBlockingQueue<Object> res = new PriorityBlockingQueue<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
