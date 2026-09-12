package io.jimble.util.internal.convertor.time;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.internal.convertor.util.DateConvertor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * instant
 */
public class InstantConvertor implements IConvertor<Instant> {

	public static final InstantConvertor INSTANCE = new InstantConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant convert(Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Instant instant) {

			return instant;

		} else if (obj instanceof Date date) {

			return date.toInstant();

		} else if (obj instanceof Number number) {

			return new Date(number.longValue()).toInstant();

		} else if (obj instanceof Calendar) {

			return ((Calendar) obj).getTime().toInstant();

		} else if (obj instanceof LocalDateTime) {

			return ZonedDateTime.of((LocalDateTime) obj, ZoneId.systemDefault()).toInstant();

		}

		String dateString = PropertyUtil.toString(obj);
		Date d = DateConvertor.parseDate(conf, dateString);
		if (d != null) {
			return d.toInstant();
		}

		return null;

	}

}
