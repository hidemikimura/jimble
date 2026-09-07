package io.jimble.util.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.ConvertorConfigKeys;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date変換クラス.
 *
 * @author DN
 */
public class DateConvertor implements IConvertor<Date> {

	/**
	 * インスタンス.
	 */
	public static final DateConvertor INSTANCE = new DateConvertor();

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Date convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof Date) {

			return (Date) obj;

		} else if (obj instanceof Number) {

			return new Date(((Number) obj).longValue());

		} else if (obj instanceof Calendar) {

			return ((Calendar) obj).getTime();

		} else if (obj instanceof LocalDateTime) {

			return Date.from(ZonedDateTime.of((LocalDateTime) obj, ZoneId.systemDefault()).toInstant());

		} else if (obj instanceof Instant) {

			return Date.from((Instant) obj);

		}

		String dateString = PropertyUtil.toString(obj);

		Object confObj;
		if (conf != null && (confObj = conf.get(ConvertorConfigKeys.CONVERT_STRING_TO_DATE)) != null) {

			if (confObj instanceof DateFormat) {

				try {
					return ((DateFormat) confObj).parse(dateString);
				} catch (Exception ex) {
					conf.remove(ConvertorConfigKeys.CONVERT_STRING_TO_DATE);
				}

			} else if (confObj instanceof String) {

				try {
					SimpleDateFormat sdf = new SimpleDateFormat(confObj.toString());
					conf.put(ConvertorConfigKeys.CONVERT_STRING_TO_DATE, sdf);
					return sdf.parse(dateString);
				} catch (Exception e) {
					conf.remove(ConvertorConfigKeys.CONVERT_STRING_TO_DATE);
				}

			} else if (confObj instanceof List<?>) {

				List<Object> objs = (List<Object>) confObj;
				Date res = null;
				for (Object o : objs) {
					if (o instanceof DateFormat) {
						try {
							res = ((DateFormat) o).parse(dateString);
							return res;
						} catch (Exception e) {
							res = null;
						}
					} else if (o instanceof String) {
						try {
							SimpleDateFormat sdf = new SimpleDateFormat(o.toString());
							return sdf.parse(dateString);
						} catch (Exception e) {
							res = null;
						}
					}
				}

			}

		}

		return parseDate(conf, dateString);

	}

	/**
	 * 日付文字列から日付を取得する.
	 *
	 * @param date 日付文字列
	 * @return 日付
	 */
	public static Date parseDate (Configration conf, String date) {

		Date d;
		for (String pt : DATE_PATTERN_LIST) {
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(pt);
				d = sdf.parse(date);
				if (conf != null) {
					conf.put(ConvertorConfigKeys.CONVERT_STRING_TO_DATE, sdf);
				}
			} catch (Exception e) {
				continue;
			}
			return d;
		}

		try {
			Matcher m = DATE_PATTERN_REGEX.matcher(date);
			if (m.find()) {
				String dd = m.group(1) + " " + m.group(2);
				SimpleDateFormat sdf = new SimpleDateFormat("d yyyy HH:mm:ss Z");
				d = sdf.parse(dd);
				return d;
			}
		} catch (Exception ex) {
		}

		return null;
	}

	/* 日付パターン正規表現 */
	private static final Pattern DATE_PATTERN_REGEX = Pattern.compile("[a-zA-Z]+,(\\d+) [a-zA-Z]+ (.*)");

	/**
	 * 日付パターン.
	 */
	private static final List<String> DATE_PATTERN_LIST = new ArrayList<String>();
	static {
		DATE_PATTERN_LIST.add("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		DATE_PATTERN_LIST.add("yyyy-MM-dd'T'HH:mm:ss");
		DATE_PATTERN_LIST.add("yyyy-MM-dd'T'HH:mm");
		DATE_PATTERN_LIST.add("yyyy.MM.dd G 'at' HH:mm:ss z");
		DATE_PATTERN_LIST.add("EEE, MMM d, ''yy");
		DATE_PATTERN_LIST.add("h:mm a");
		DATE_PATTERN_LIST.add("hh 'o''clock' a, zzzz");
		DATE_PATTERN_LIST.add("K:mm a, z");
		DATE_PATTERN_LIST.add("yyyyy.MMMMM.dd GGG hh:mm aaa");
		DATE_PATTERN_LIST.add("EEE, d MMM yyyy HH:mm:ss Z");
		DATE_PATTERN_LIST.add("yyMMddHHmmssZ");
		DATE_PATTERN_LIST.add("yyyy-MM-dd HH:mm:ss");
		DATE_PATTERN_LIST.add("yyyy-MM-dd HH:mm");
		DATE_PATTERN_LIST.add("yyyy-MM-dd HH");
		DATE_PATTERN_LIST.add("yyyy-MM-dd");
		DATE_PATTERN_LIST.add("yyyy/MM/dd HH:mm:ss");
		DATE_PATTERN_LIST.add("yyyy/MM/dd HH:mm");
		DATE_PATTERN_LIST.add("yyyy/MM/dd HH");
		DATE_PATTERN_LIST.add("yyyy/MM/dd");
		DATE_PATTERN_LIST.add("yyyyMMddHHmmss");
		DATE_PATTERN_LIST.add("yyyyMMddHHmm");
		DATE_PATTERN_LIST.add("yyyyMMddHH");
		DATE_PATTERN_LIST.add("yyyyMMdd");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH時mm分ss秒");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH時mm分");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH時");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH:mm:ss");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH:mm");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日 HH");
		DATE_PATTERN_LIST.add("yyyy年MM月dd日");
		DATE_PATTERN_LIST.add("yyyy年MM月");
	}

}
