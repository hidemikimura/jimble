package io.jimble.util.internal.convertor.lang.exception;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Exception変換クラス.
 * 
 * @author DN
 */
public class ExceptionConvertor implements IConvertor<Exception> {

	/** インスタンス. */
	public static final ExceptionConvertor INSTANCE = new ExceptionConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Exception convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Exception) {
			return (Exception) obj;
		}

		return new Exception(PropertyUtil.toString(obj));
	}

}
