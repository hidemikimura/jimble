package io.jimble.util.convertor.net;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.net.URI;

/**
 * URI変換クラス.
 * 
 * @author DN
 */
public class URIConvertor implements IConvertor<URI> {

	/** インスタンス. */
	public static final URIConvertor INSTANCE = new URIConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URI convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof URI) {
			return (URI) obj;
		}

		try {
			return new URI(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return null;
		}
	}

}
