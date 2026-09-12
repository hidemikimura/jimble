package io.jimble.util.internal.convertor.sql;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.internal.convertor.util.DateConvertor;

import java.sql.Date;

/**
 * Date変換クラス.
 * 
 * @author DN
 */
public class SqlDateConvertor implements IConvertor<Date> {

	/** インスタンス. */
	public static final SqlDateConvertor INSTANCE = new SqlDateConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Date convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Date) {
			return (Date) obj;
		}

		try {
			return new Date((DateConvertor.INSTANCE.convert(conf, obj, destClasses)).getTime());
		} catch (Exception e) {
			return null;
		}
	}
}
