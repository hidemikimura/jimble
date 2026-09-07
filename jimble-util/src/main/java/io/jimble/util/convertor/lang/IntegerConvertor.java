package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Integer変換クラス.
 *
 * @author DN
 */
public class IntegerConvertor implements IConvertor<Integer> {

	/**
	 * インスタンス.
	 */
	public static final IntegerConvertor INSTANCE = new IntegerConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Integer convert (Configration conf, Object obj, Class<?>... destClasses) {

		if (obj instanceof Integer) {
			return (Integer) obj;
		}

		Integer res = 0;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			res = ((Number) obj).intValue();
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).intValue();
		} else {
			try {
				res = Integer.parseInt(PropertyUtil.toString(obj).replaceAll(":", ""));
			} catch (Exception e) {
				res = isPrimitive ? 0 : null;
			}
		}

		return res;
	}

}
