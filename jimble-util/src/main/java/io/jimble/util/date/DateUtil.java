package io.jimble.util.date;

import io.jimble.util.convertor.Convertor;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日時ユーティリティ.
 */
public class DateUtil {

	public static void main (String[] args) {

		String duration = formatDuration(2 * 60 * 60 * 1000, "H:mm:ss");

		System.out.println();

	}

	/**
	 * 月曜日FROMを取得する
	 *
	 * @return	月曜日FROM
	 */
	public static Date getFromWeek (int add) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(getTodayFrom());

		int todayWeek = calendar.get(Calendar.DAY_OF_WEEK);
		if (todayWeek == Calendar.SUNDAY) {
			calendar.add(Calendar.DAY_OF_MONTH, (add * 7) - 6);
		} else {
			calendar.add(Calendar.DAY_OF_MONTH, (add * 7) - (todayWeek - 2));
		}

		return calendar.getTime();

	}

	/**
	 * 先週日曜日TOを取得する
	 *
	 * @return	先週日曜日TO
	 */
	public static Date getToWeek (int add) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(getTodayTo());

		int todayWeek = calendar.get(Calendar.DAY_OF_WEEK);
		if (todayWeek == Calendar.SUNDAY) {
			calendar.add(Calendar.DAY_OF_MONTH, add * 7);
		} else {
			if (add == 0) {
				calendar.add(Calendar.DAY_OF_MONTH, 7 - (todayWeek - 1));
			} else if (add < 0) {
				calendar.add(Calendar.DAY_OF_MONTH, add * 7 + (7 - (todayWeek - 1)));
			} else {
				calendar.add(Calendar.DAY_OF_MONTH, (add + 1) * 7 - (todayWeek - 1));
			}
		}

		return calendar.getTime();

	}

	/**
	 * 年月数値を取得する
	 *
	 * @return	年月数値
	 */
	public static int getY (Date date) {

		if (date == null) {
			return 0;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(Calendar.YEAR);

	}

	/**
	 * 年月数値を取得する
	 *
	 * @return	年月数値
	 */
	public static int getM (Date date) {

		if (date == null) {
			return 0;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(Calendar.MONTH) + 1;

	}

	/**
	 * 年月数値を取得する
	 *
	 * @return	年月数値
	 */
	public static int getD (Date date) {

		if (date == null) {
			return 0;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(Calendar.DAY_OF_MONTH);

	}

	/**
	 * 年月数値を取得する
	 *
	 * @return	年月数値
	 */
	public static int getYYYYMM () {

		Calendar calendar = Calendar.getInstance();
		return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1;

	}

	/**
	 * 年月数値を取得する
	 *
	 * @param date	日時
	 * @return	年月数値
	 */
	public static int getYYYYMM (Date date) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1;

	}

	/**
	 * 年月日数値を取得する
	 *
	 * @return	年月日数値
	 */
	public static int getYYYYMMDD () {

		Calendar calendar = Calendar.getInstance();
		return calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH);

	}

	/**
	 * 年月日数値を取得する
	 *
	 * @param date	日時
	 * @return	年月日数値
	 */
	public static int getYYYYMMDD (Date date) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH);

	}

	/**
	 * 日付を文字列にする
	 *
	 * @param date		日付
	 * @param format	フォーマット
	 * @return	文字列
	 */
	public static String format (Date date, String format) {

		try {

			return new SimpleDateFormat(format).format(date);

		} catch (Exception ex) {

			return "";

		}

	}

	/**
	 * 日付を文字列にする
	 *
	 * @param date		日付
	 * @param format	フォーマット
	 * @return	文字列
	 */
	public static String formatJP (Date date, String format) {

		try {

			return new SimpleDateFormat(format, Locale.JAPAN).format(date);

		} catch (Exception ex) {

			return "";

		}

	}

	/**
	 * 日時文字列を解析する
	 *
	 * @param dateString	日時文字列
	 * @param format		フォーマット
	 * @return	日時
	 */
	public static Date parseDate (String dateString, String format) {

		try {

			return new SimpleDateFormat(format).parse(dateString);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 日時文字列を解析する
	 *
	 * @param dateString	日時文字列
	 * @return	日時
	 */
	public static Date parseDate (String dateString) {

		try {

			return Convertor.convert(null, dateString, Date.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 日時文字列を取得する
	 *
	 * @param date		日時
	 * @param format	フォーマット
	 * @return	日時文字列
	 */
	public static String getDateString (Date date, String format) {

		try {

			return new SimpleDateFormat(format).format(date);

		} catch (Exception ex) {

			return "";

		}

	}

	public static long getPeriodDay (Date date1, Date date2) {

		return Duration.between(date1.toInstant(), date2.toInstant()).toDays();

	}

	/**
	 * 期間を取得する.
	 *
	 * @param date	日付
	 * @return	期間
	 */
	public static String getPeriodString (Date date) {

		return getPeriodString(date, new Date());

	}

	/**
	 * 期間を取得する.
	 *
	 * @param date1	日付
	 * @param date2	日付
	 * @return	期間
	 */
	public static String getPeriodString (Date date1, Date date2) {

		Period period = Period.between(date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

		StringBuilder sb = new StringBuilder();
		int y = Math.abs(period.getYears());
		if (y > 0) {
			sb.append(y);
			sb.append("年");
		}

		int m = Math.abs(period.getMonths());
		if (m > 0) {
			sb.append(m);
			sb.append("ヶ月");
		}

		int d = Math.abs(period.getDays());
		if (d > 0) {
			sb.append(d);
			sb.append("日");
		}

		return sb.toString();

	}

	/**
	 * 日付を取得する.
	 *
	 * @param yyyy	年
	 * @param mm	月
	 * @param dd	日
	 * @return	日付
	 */
	public static Date getDate (int yyyy, int mm, int dd) {

		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, yyyy);
		calendar.set(Calendar.MONTH, mm - 1);
		calendar.set(Calendar.DAY_OF_MONTH, dd);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();

	}

	/**
	 * 現在日時に指定分加算した日時を取得する
	 *
	 * @param add	加算する分
	 * @return	日時
	 */
	public static Date getDateAddMinute (int add) {

		return getDateAddMinute(new Date(), add);

	}

	/**
	 * 現在日時に指定分加算した日時を取得する
	 *
	 * @param add	加算する分
	 * @return	日時
	 */
	public static Date getDateAddMinute (Date date, int add) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.add(Calendar.MINUTE, add);

		return calendar.getTime();

	}

	/**
	 * 現在日時に指定日加算した日時を取得する
	 *
	 * @param add	加算する日数
	 * @return	日時
	 */
	public static Date getDateAddDay (int add) {

		return getDateAddDay(new Date(), add);

	}

	/**
	 * 現在日時に指定日加算した日時を取得する
	 *
	 * @param add	加算する日数
	 * @return	日時
	 */
	public static Date getDateAddDay (Date date, int add) {

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.add(Calendar.DAY_OF_MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 当日FROMを取得する.
	 *
	 * @return	当日FROM
	 */
	public static Date getTodayFrom () {

		return getFrom(new Date());

	}

	/**
	 * FROMを取得する.
	 *
	 * @param date	日時
	 * @return	FROM
	 */
	public static Date getFrom (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();

	}

	/**
	 * FROMを取得する.
	 *
	 * @param date	日時
	 * @return	FROM
	 */
	public static Date getFromSecond (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();

	}

	/**
	 * 指定日加算した日のFROMを取得する.
	 *
	 * @param add	加算する日数
	 * @return	FROM
	 */
	public static Date getFromAddDay (int add) {

		Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		calendar.add(Calendar.DAY_OF_MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 指定日加算した日のFROMを取得する.
	 *
	 * @param add	加算する日数
	 * @return	FROM
	 */
	public static Date getFromAddDay (Date date, int add) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		calendar.add(Calendar.DAY_OF_MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 指定月加算した日のFROMを取得する.
	 *
	 * @param add	加算する月数
	 * @return	FROM
	 */
	public static Date getFromAddMonth (int add) {

		Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		calendar.add(Calendar.MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 指定月加算した日のFROMを取得する.
	 *
	 * @param add	加算する月数
	 * @return	FROM
	 */
	public static Date getFromAddMonth (Date date, int add) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		calendar.add(Calendar.MONTH, add);

		return calendar.getTime();

	}

	/**
	 * FROMを取得する.
	 *
	 * @param date	日時
	 * @return	FROM
	 */
	public static Date getFromMonth (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();

	}

	/**
	 * TOを取得する.
	 *
	 * @param date	日時
	 * @return	FROM
	 */
	public static Date getToMonth (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));

		return calendar.getTime();

	}

	/**
	 * 当日TOを取得する.
	 *
	 * @return	当日TO
	 */
	public static Date getTodayTo () {

		return getTo(new Date());

	}

	/**
	 * TOを取得する.
	 *
	 * @param date	日時
	 * @return	TO
	 */
	public static Date getTo (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		return calendar.getTime();

	}

	/**
	 * TOを取得する.
	 *
	 * @param date	日時
	 * @return	TO
	 */
	public static Date getToSecond (Date date) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		return calendar.getTime();

	}

	/**
	 * 指定日加算した日のTOを取得する.
	 *
	 * @param add	加算する日数
	 * @return	FROM
	 */
	public static Date getToAddDay (int add) {

		Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		calendar.add(Calendar.DAY_OF_MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 指定日加算した日のTOを取得する.
	 *
	 * @param add	加算する日数
	 * @return	TO
	 */
	public static Date getToAddDay (Date date, int add) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		calendar.add(Calendar.DAY_OF_MONTH, add);

		return calendar.getTime();

	}


	/**
	 * 指定月加算した月初のFROMを取得する.
	 *
	 * @param add	加算する月数
	 * @return	FROM
	 */
	public static Date getFromMonthAddMonth (int add) {

		Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		calendar.add(Calendar.MONTH, add);

		return calendar.getTime();

	}

	/**
	 * 指定月加算した月末のTOを取得する.
	 *
	 * @param add	加算する月数
	 * @return	FROM
	 */
	public static Date getToMonthAddMonth (int add) {

		Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		calendar.add(Calendar.MONTH, add);

		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));

		return calendar.getTime();

	}

	public static Date getToMonthAddMonth (Date date, int add) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		calendar.add(Calendar.MONTH, add);

		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));

		return calendar.getTime();

	}



	public static Date getFrom (Date date, int type) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		switch (type) {
			case Calendar.MONTH:
				calendar.set(Calendar.MONTH, 0);
			case Calendar.DAY_OF_MONTH:
				calendar.set(Calendar.DAY_OF_MONTH, 1);
			case Calendar.HOUR_OF_DAY:
				calendar.set(Calendar.HOUR_OF_DAY, 0);
			case Calendar.MINUTE:
				calendar.set(Calendar.MINUTE, 0);
			case Calendar.SECOND:
				calendar.set(Calendar.SECOND, 0);
			case Calendar.MILLISECOND:
				calendar.set(Calendar.MILLISECOND, 0);
				break;
		}

		return calendar.getTime();

	}

	public static Date getTo (Date date, int type) {

		if (date == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		switch (type) {
			case Calendar.MONTH:
				calendar.set(Calendar.MONTH, calendar.getActualMaximum(Calendar.MONTH));
			case Calendar.DAY_OF_MONTH:
				calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
			case Calendar.HOUR_OF_DAY:
				calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
			case Calendar.MINUTE:
				calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
			case Calendar.SECOND:
				calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
			case Calendar.MILLISECOND:
				calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
				break;
		}

		return calendar.getTime();

	}

	public static String formatDuration (long ms) {

		String format;

		if (ms >= 60 * 60 * 1000) {
			format = "H:mm:SS";
		} else if (ms >= 60 * 1000) {
			format = "m:ss";
		} else {
			format = "s";
		}

		return formatDuration(ms, format);

	}

	public static String formatDuration (long ms, String format) {

		try {

			Duration duration = Duration.of(ms, ChronoUnit.MILLIS);
			LocalTime time = LocalTime.MIDNIGHT.plus(duration);
			return DateTimeFormatter.ofPattern(format).format(time);

		} catch (Exception ex) {

			return String.valueOf(ms);

		}

	}

}
