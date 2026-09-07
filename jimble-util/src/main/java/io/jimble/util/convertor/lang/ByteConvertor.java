package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Byte変換クラス.
 *
 * @author DN
 */
public class ByteConvertor implements IConvertor<Byte> {

	/**
	 * インスタンス.
	 */
	public static final ByteConvertor INSTANCE = new ByteConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Byte convert (Configration conf, Object obj, Class<?>... destClasses) {

		if (obj instanceof Byte) {
			return (Byte) obj;
		}

		Byte res = 0;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Number) {
			res = ((Number) obj).byteValue();
		} else if (obj instanceof Boolean) {
			return ((Number) ((Boolean) obj ? 1 : 0)).byteValue();
		} else {
			try {
				res = Byte.parseByte(PropertyUtil.toString(obj).replaceAll(":", ""));
			} catch (Exception e) {
				res = isPrimitive ? Byte.valueOf("0") : null;
			}
		}

		return res;
	}

}
