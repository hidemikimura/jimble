package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Long変換クラス.
 *
 * @author DN
 */
public class LongConvertor implements IConvertor<Long> {

	/**
	 * インスタンス.
	 */
	public static final LongConvertor INSTANCE = new LongConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Long convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj instanceof Long) {
			return (Long) obj;
		}

		Long res = 0L;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			res = ((Number) obj).longValue();
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).longValue();
		} else {
			try {
				res = Long.parseLong(PropertyUtil.toString(obj).replaceAll(":", ""));
			} catch (Exception e) {
				res = isPrimitive ? 0L : null;
			}
		}

		return res;
	}

}
