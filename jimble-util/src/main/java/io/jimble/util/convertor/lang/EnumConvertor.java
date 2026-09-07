package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Enum変換クラス.
 * 
 * @author DN
 */
public class EnumConvertor implements IConvertor<Enum< ? >> {

	/** インスタンス. */
	public static final EnumConvertor INSTANCE = new EnumConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Enum< ? > convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		String name = PropertyUtil.toString(obj);

		Object[] enums = destClasses[0].getEnumConstants();
		for (Object e : enums) {

			if (e instanceof Enum< ? >) {
				if (((Enum< ? >) e).name().toLowerCase().equals(name.toLowerCase())) {
					return (Enum< ? >) e;
				}
			}

		}

		return null;
	}

}
