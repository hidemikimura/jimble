package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Double変換クラス.
 *
 * @author DN
 */
public class DoubleConvertor implements IConvertor<Double> {

	/**
	 * インスタンス.
	 */
	public static final DoubleConvertor INSTANCE = new DoubleConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Double convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj instanceof Double) {
			return (Double) obj;
		}

		Double res = 0d;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			try {
				res = Double.valueOf(obj.toString());
			} catch (Exception e) {
				res = ((Number) obj).doubleValue();
			}
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).doubleValue();
		} else {
			try {
				res = Double.parseDouble(PropertyUtil.toString(obj));
			} catch (Exception e) {
				res = isPrimitive ? 0d : null;
			}
		}

		return res;
	}

}
