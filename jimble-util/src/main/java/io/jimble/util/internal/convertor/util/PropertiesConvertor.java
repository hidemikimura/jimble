package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.Properties;

/**
 * Properties変換クラス.
 * 
 * @author DN
 */
public class PropertiesConvertor implements IConvertor<Properties> {

	/** インスタンス. */
	public static final PropertiesConvertor INSTANCE = new PropertiesConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Properties convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Properties res = new Properties();
		MapUtil.convertMap(conf, obj, res, destClasses);

		return res;
	}

}
