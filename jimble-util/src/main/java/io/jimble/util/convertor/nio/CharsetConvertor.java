package io.jimble.util.convertor.nio;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.nio.charset.Charset;

/**
 * Charset変換クラス.
 * 
 * @author DN
 */
public class CharsetConvertor implements IConvertor<Charset> {

	/** インスタンス. */
	public static final CharsetConvertor INSTANCE = new CharsetConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Charset convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Charset) {
			return (Charset) obj;
		}

		try {
			return Charset.forName(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return null;
		}

	}

}
