package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.PriorityQueue;

/**
 * PriorityQueue変換クラス.
 * 
 * @author DN
 */
public class PriorityQueueConvertor implements IConvertor<PriorityQueue<Object>> {

	/** インスタンス. */
	public static final PriorityQueueConvertor INSTANCE = new PriorityQueueConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PriorityQueue<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		PriorityQueue<Object> res = new PriorityQueue<Object>();
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
