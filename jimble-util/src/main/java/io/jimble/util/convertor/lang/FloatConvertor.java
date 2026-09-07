package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Float変換クラス.
 *
 * @author DN
 */
public class FloatConvertor implements IConvertor<Float> {

	/**
	 * インスタンス.
	 */
	public static final FloatConvertor INSTANCE = new FloatConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Float convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj instanceof Float) {
			return (Float) obj;
		}

		Float res = 0f;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			try {
				res = Float.valueOf(obj.toString());
			} catch (Exception e) {
				res = ((Number) obj).floatValue();
			}
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).floatValue();
		} else {
			try {
				res = Float.parseFloat(PropertyUtil.toString(obj));
			} catch (Exception e) {
				res = isPrimitive ? 0f : null;
			}
		}

		return res;
	}

}
