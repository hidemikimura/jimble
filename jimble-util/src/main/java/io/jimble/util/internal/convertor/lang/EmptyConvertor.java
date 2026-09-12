package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

/**
 * 空
 */
public class EmptyConvertor implements IConvertor<String> {

	/* インスタンス */
	public static final EmptyConvertor INSTANCE = new EmptyConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String convert(Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		return obj.getClass().getName();

	}

}
