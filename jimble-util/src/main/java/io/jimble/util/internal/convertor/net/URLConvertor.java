package io.jimble.util.internal.convertor.net;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.net.URI;
import java.net.URL;

/**
 * URL変換クラス.
 * 
 * @author DN
 */
public class URLConvertor implements IConvertor<URL> {

	/** インスタンス. */
	public static final URLConvertor INSTANCE = new URLConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URL convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof URL) {
			return (URL) obj;
		}

		try {
			return URI.create(PropertyUtil.toString(obj)).toURL();
		} catch (Exception e) {
			return null;
		}
	}

}
