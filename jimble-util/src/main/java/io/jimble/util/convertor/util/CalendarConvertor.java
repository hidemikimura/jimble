package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;

import java.util.Calendar;
import java.util.Date;

/**
 * Calendar変換クラス.
 * 
 * @author DN
 */
public class CalendarConvertor implements IConvertor<Calendar> {

	/** インスタンス. */
	public static final CalendarConvertor INSTANCE = new CalendarConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Calendar convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Calendar cal = Calendar.getInstance();
		Date date = DateConvertor.INSTANCE.convert(conf, obj, destClasses);
		if (date != null) {
			cal.setTime(date);
		}

		return cal;
	}

}
