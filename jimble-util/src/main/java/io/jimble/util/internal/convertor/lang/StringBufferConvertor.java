package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

/**
 * StringBuffer変換クラス.
 * 
 * @author DN
 */
public class StringBufferConvertor implements IConvertor<StringBuffer> {

	/** インスタンス. */
	public static final StringBufferConvertor INSTANCE = new StringBufferConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StringBuffer convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof StringBuffer) {

			return (StringBuffer) obj;

		}

		return new StringBuffer(StringConvertor.INSTANCE.convert(conf, obj, destClasses));
	}

}
