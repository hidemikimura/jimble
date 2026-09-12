package io.jimble.util.internal.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Class変換クラス.
 * 
 * @author DN
 */
public class ClassConvertor implements IConvertor<Class< ? >> {

	/** インスタンス. */
	public static final ClassConvertor INSTANCE = new ClassConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class< ? > convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Class< ? >) {
			return (Class< ? >) obj;
		}

		try {
			return Class.forName(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return null;
		}

	}

}
