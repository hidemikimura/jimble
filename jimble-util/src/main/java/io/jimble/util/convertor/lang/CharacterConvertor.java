package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

/**
 * Character変換クラス.
 *
 * @author DN
 */
public class CharacterConvertor implements IConvertor<Character> {

	/**
	 * インスタンス.
	 */
	public static final CharacterConvertor INSTANCE = new CharacterConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Character convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj instanceof Character) {
			return (Character) obj;
		}

		Character res = '\0';

		boolean isPrimitive = (destClasses != null && destClasses.length > 0 && destClasses[0].isPrimitive());

		if (obj == null) {
			return isPrimitive ? res : null;
		}

		String str = PropertyUtil.toString(obj);

		if (str == null || str.length() == 0) {
			res = isPrimitive ? '\0' : null;
		} else {
			res = str.charAt(0);
		}

		return res;
	}

}
