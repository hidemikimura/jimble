package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Short変換クラス.
 *
 * @author DN
 */
public class ShortConvertor implements IConvertor<Short> {

	/**
	 * インスタンス.
	 */
	public static final ShortConvertor INSTANCE = new ShortConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Short convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj instanceof Short) {
			return (Short) obj;
		}

		Short res = 0;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			res = ((Number) obj).shortValue();
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).shortValue();
		} else {
			try {
				res = Short.parseShort(PropertyUtil.toString(obj).replaceAll(":", ""));
			} catch (Exception e) {
				res = isPrimitive ? Short.valueOf("0") : null;
			}
		}

		return res;
	}

}
