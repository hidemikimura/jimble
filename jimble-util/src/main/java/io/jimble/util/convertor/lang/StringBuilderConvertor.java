package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

/**
 * StringBuilder変換クラス.
 * 
 * @author DN
 */
public class StringBuilderConvertor implements IConvertor<StringBuilder> {

	/** インスタンス. */
	public static final StringBuilderConvertor INSTANCE = new StringBuilderConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StringBuilder convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof StringBuilder) {

			return (StringBuilder) obj;

		}

		return new StringBuilder(StringConvertor.INSTANCE.convert(conf, obj, destClasses));
	}

}
