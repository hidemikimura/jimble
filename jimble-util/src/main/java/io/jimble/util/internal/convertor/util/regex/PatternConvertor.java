package io.jimble.util.internal.convertor.util.regex;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.util.regex.Pattern;

/**
 * Pattern変換クラス.
 * 
 * @author DN
 */
public class PatternConvertor implements IConvertor<Pattern> {

	/** インスタンス. */
	public static final PatternConvertor INSTANCE = new PatternConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Pattern convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Pattern) {
			return (Pattern) obj;
		}

		try {

			return Pattern.compile(PropertyUtil.toString(obj));

		} catch (Exception e) {

			return null;

		}

	}

}
