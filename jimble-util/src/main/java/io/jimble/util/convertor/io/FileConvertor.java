package io.jimble.util.convertor.io;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.io.File;

/**
 * File変換クラス.
 * 
 * @author DN
 */
public class FileConvertor implements IConvertor<File> {

	/** インスタンス. */
	public static final FileConvertor INSTANCE = new FileConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public File convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof File) {
			return (File) obj;
		}

		try {
			return new File(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return null;
		}
	}

}
