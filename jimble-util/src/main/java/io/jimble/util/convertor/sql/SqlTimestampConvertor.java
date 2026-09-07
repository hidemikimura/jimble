package io.jimble.util.convertor.sql;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.util.DateConvertor;

import java.sql.Timestamp;

/**
 * Timestamp変換クラス.
 * 
 * @author DN
 */
public class SqlTimestampConvertor implements IConvertor<Timestamp> {

	/** インスタンス. */
	public static final SqlTimestampConvertor INSTANCE = new SqlTimestampConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Timestamp convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Timestamp) {
			return (Timestamp) obj;
		}

		try {
			return new Timestamp((DateConvertor.INSTANCE.convert(conf, obj, destClasses)).getTime());
		} catch (Exception e) {
			return null;
		}
	}

}
