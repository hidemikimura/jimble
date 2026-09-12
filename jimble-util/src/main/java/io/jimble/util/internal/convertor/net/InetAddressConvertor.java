package io.jimble.util.internal.convertor.net;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.net.InetAddress;

/**
 * InetAddress変換クラス.
 * 
 * @author DN
 */
public class InetAddressConvertor implements IConvertor<InetAddress> {

	/** インスタンス. */
	public static final InetAddressConvertor INSTANCE = new InetAddressConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InetAddress convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof InetAddress) {
			return (InetAddress) obj;
		}

		try {
			return InetAddress.getByName(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return null;
		}
	}

}
