package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Boolean変換クラス.
 * 
 * @author DN
 */
public class BooleanConvertor implements IConvertor<Boolean> {

	/** インスタンス. */
	public static final BooleanConvertor INSTANCE = new BooleanConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Boolean convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj instanceof Boolean) {
			return (Boolean) obj;
		}

		Boolean res = Boolean.FALSE;

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		} else if (obj instanceof Boolean) {
			res = (Boolean) obj;
		} else if (obj instanceof Number) {
			res = ((Number) obj).intValue() == 1;
		} else {
			try {
				String str = PropertyUtil.toString(obj);
				res = (str.length() == 0 || "0".equals(str) || "false".equalsIgnoreCase(str)) ? Boolean.FALSE : Boolean.TRUE;
			} catch (Exception e) {
				res = isPrimitive ? Boolean.FALSE : null;
			}
		}

		return res;
	}

}
