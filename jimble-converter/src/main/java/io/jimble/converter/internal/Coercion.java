package io.jimble.converter.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 値を、型にする（D-307。jimble-json の同じ名前のクラスを写し、JSON 以外の値（Date・java.time・enum など）も受けるようにしたもの）
 *
 * <p>
 * <b>意味がはっきりしているゆるさは受け付け、値が変わったり落ちたりするものは断る</b>（変換の決まりと同じ線）。
 * </p>
 * <ul>
 *   <li>数：前後の空白、全角の数字、3 桁ごとのカンマ（{@code "1,234"}）、小数点以下が 0 の小数（{@code 1.0} → int）は受け付ける。
 *       小数点以下のある小数を整数に（{@code 1.5} → int）、桁あふれ、読めない文字は断る</li>
 *   <li>真偽：{@code true/false/1/0/yes/no/on/off}（大文字小文字は問わない）。それ以外は断る</li>
 *   <li>enum：名前（大文字小文字は問わない）。合わなければ断る</li>
 *   <li>日時：{@code yyyy/MM/dd HH:mm:ss}・{@code yyyy-MM-dd HH:mm:ss}・ISO 8601（時差つきも）・{@code yyyy/MM/dd}・{@code HH:mm:ss}・月日時が 1 桁（{@code 2024/1/5 9:00}）・{@code yyyy年M月d日}。
 *       ありえない日付（{@code 2024-02-30}）と後ろの余分は断る。時差が無ければ、時間帯で補う</li>
 *   <li>空文字は、プリミティブなら 0 / false、それ以外は null</li>
 * </ul>
 * <p>断るときは {@link IllegalArgumentException}（呼んだ側が、場所を付けて JsonException にする）。</p>
 */
final class Coercion {

	/* 日時の形（日付の区切りは / か -、時刻は空白か T、秒・秒より細かいところ・時差は省ける） */
	private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
		.appendValue(ChronoField.YEAR, 4)
		.optionalStart().appendLiteral('/').optionalEnd()
		.optionalStart().appendLiteral('-').optionalEnd()
		.appendValue(ChronoField.MONTH_OF_YEAR, 2)
		.optionalStart().appendLiteral('/').optionalEnd()
		.optionalStart().appendLiteral('-').optionalEnd()
		.appendValue(ChronoField.DAY_OF_MONTH, 2)
		.optionalStart()
			.optionalStart().appendLiteral(' ').optionalEnd()
			.optionalStart().appendLiteral('T').optionalEnd()
			.appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2)
			.optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalEnd()
			.optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
		.optionalEnd()
		.optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
		.optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
		.toFormatter(Locale.ROOT)
		.withResolverStyle(ResolverStyle.STRICT)
		.withChronology(java.time.chrono.IsoChronology.INSTANCE);

	/* 時刻の形 */
	private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
		.appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2)
		.optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalEnd()
		.optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
		.toFormatter(Locale.ROOT)
		.withResolverStyle(ResolverStyle.STRICT);

	/* 月・日・時が 1 桁の日時（2024/1/5・2024-1-5 9:00）。後ろ（分から先）はそのまま DATE_TIME で読む */
	private static final Pattern UNPADDED_DATE = Pattern.compile("(\\d{4})([/-])(\\d{1,2})([/-])(\\d{1,2})(?:([ T])(\\d{1,2})(?=:))?");

	/* 書くときに日時の前に付けることのある文字（読むときは外す） */
	private static final String DATE_PREFIX = "date::";

	private Coercion () {
	}

	/**
	 * 値を型にする
	 *
	 * @param value	JSON の値（木で読んだもの。null も可）
	 * @param type	型
	 * @param zone	時差の無い日時を補う時間帯（null ならサーバーの時間帯）
	 * @return	型にした値
	 * @throws IllegalArgumentException	できないとき
	 */
	static Object coerce (Object value, Class<?> type, ZoneId zone) {

		if (value == null) {
			return defaultValue(type);
		}

		// よくある組み合わせは、そのまま（BigDecimal を作らない）
		if (value instanceof Integer i) {
			if (type == int.class || type == Integer.class) {
				return i;
			}
			if (type == long.class || type == Long.class) {
				return i.longValue();
			}
			if (type == double.class || type == Double.class) {
				return i.doubleValue();
			}
		} else if (value instanceof Long l && (type == long.class || type == Long.class)) {
			return l;
		} else if (value instanceof Double d && (type == double.class || type == Double.class)) {
			return d;
		} else if (value instanceof Boolean b && (type == boolean.class || type == Boolean.class)) {
			return b;
		} else if (value instanceof String && type == String.class) {
			return value;
		}

		if (type == Object.class) {
			return value;
		}

		// 型が合っていれば、そのまま（数は下で、型と範囲を見て作り直す）
		if (type.isInstance(value) && !(value instanceof Number)) {
			return value;
		}

		if (type == String.class || type == CharSequence.class) {
			return toText(value);
		}

		if (value instanceof String s && s.isBlank() && type != Character.class && type != char.class) {
			return defaultValue(type);
		}

		if (type == int.class || type == Integer.class) {
			return integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, "int").intValueExact();
		}
		if (type == long.class || type == Long.class) {
			return integral(value, Long.MIN_VALUE, Long.MAX_VALUE, "long").longValueExact();
		}
		if (type == short.class || type == Short.class) {
			return integral(value, Short.MIN_VALUE, Short.MAX_VALUE, "short").shortValueExact();
		}
		if (type == byte.class || type == Byte.class) {
			return integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte").byteValueExact();
		}
		if (type == BigInteger.class) {
			return integral(value, null, null, "BigInteger").toBigIntegerExact();
		}
		if (type == double.class || type == Double.class) {
			if (value instanceof String s && isPlainNumber(s)) {
				return Double.parseDouble(s);
			}
			return decimal(value).doubleValue();
		}
		if (type == float.class || type == Float.class) {
			return decimal(value).floatValue();
		}
		if (type == BigDecimal.class && value instanceof String s && isPlainNumber(s)) {
			return new BigDecimal(s);
		}
		if (type == BigDecimal.class || type == Number.class) {
			return value instanceof Double d ? BigDecimal.valueOf(d) : decimal(value).bigDecimal;
		}
		if (type == boolean.class || type == Boolean.class) {
			return bool(value);
		}
		if (type == char.class || type == Character.class) {
			String s = toText(value);
			if (s.length() != 1) {
				throw new IllegalArgumentException("1文字ではありません: " + quote(s));
			}
			return s.charAt(0);
		}
		if (type.isEnum()) {
			return enumConstant(value, type);
		}
		if (type == UUID.class) {
			return UUID.fromString(text(value).strip());
		}
		if (type == URI.class) {
			return URI.create(text(value).strip());
		}

		Object other = otherScalar(value, type);
		if (other != null) {
			return other;
		}

		return temporal(value, type, zone != null ? zone : ZoneId.systemDefault());

	}

	/**
	 * 文字から作る JDK のクラス（危ない型を作ってよいかは、呼んだ側が先に見ている）
	 *
	 * @return	作ったもの（ここで扱わない型なら null）
	 */
	private static Object otherScalar (Object value, Class<?> type) {

		try {
			if (type == java.nio.charset.Charset.class) {
				return java.nio.charset.Charset.forName(text(value).strip());
			}
			if (type == Locale.class) {
				return Locale.forLanguageTag(text(value).strip().replace('_', '-'));
			}
			if (type == java.util.Currency.class) {
				return java.util.Currency.getInstance(text(value).strip());
			}
			if (type == java.net.URL.class) {
				return URI.create(text(value).strip()).toURL();
			}
			if (type == java.io.File.class) {
				return new java.io.File(text(value));
			}
			if (type == java.nio.file.Path.class) {
				return java.nio.file.Path.of(text(value));
			}
			if (type == java.util.regex.Pattern.class) {
				return java.util.regex.Pattern.compile(text(value));
			}
			if (type == Class.class) {
				return Class.forName(text(value).strip());
			}
			if (type == java.net.InetAddress.class) {
				return java.net.InetAddress.getByName(text(value).strip());
			}
		} catch (Exception ex) {
			throw new IllegalArgumentException(type.getSimpleName() + " にできません: " + kind(value) + "（" + ex.getMessage() + "）");
		}

		return null;

	}

	/**
	 * 値が無いとき（null・空文字）の値
	 */
	static Object defaultValue (Class<?> type) {

		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == double.class) {
			return 0d;
		}
		if (type == float.class) {
			return 0f;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		return 0;

	}

	// region 文字と数

	/* 日時を文字にするときの形（JSON に書くときと同じ） */
	private static final DateTimeFormatter TEXT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private static String toText (Object value) {

		if (value instanceof String s) {
			return s;
		}
		if (value instanceof BigDecimal d) {
			return d.toPlainString();
		}
		if (value instanceof Enum<?> e) {
			return e.name();
		}
		if (value instanceof Date date) {
			return TEXT_DATE_TIME.format(Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()));
		}
		if (value instanceof LocalDateTime local) {
			return TEXT_DATE_TIME.format(local);
		}
		if (value instanceof java.util.Map || value instanceof java.util.Collection || value.getClass().isArray()) {
			throw new IllegalArgumentException("文字列ではありません: " + kind(value));
		}
		// 数・真偽・文字・UUID・URI・LocalDate など（toString がそのまま値の形）
		return value.toString();

	}

	private static String text (Object value) {

		if (value instanceof String s) {
			return s;
		}
		throw new IllegalArgumentException("文字列ではありません: " + kind(value));

	}

	/**
	 * 整数にする（小数点以下が 0 なら受け付ける。範囲を見る）
	 */
	private static BigDecimal integral (Object value, Number min, Number max, String name) {

		Decimal decimal = decimal(value);
		BigDecimal d = decimal.bigDecimal;

		if (d.signum() != 0 && d.stripTrailingZeros().scale() > 0) {
			throw new IllegalArgumentException("小数点以下があるので " + name + " にできません: " + decimal.text);
		}

		if (min != null && (d.compareTo(BigDecimal.valueOf(min.longValue())) < 0 || d.compareTo(BigDecimal.valueOf(max.longValue())) > 0)) {
			throw new IllegalArgumentException(name + " の範囲を超えています: " + decimal.text);
		}

		return d;

	}

	/**
	 * 読んだ数（BigDecimal と、例外に載せる元の字面）
	 */
	private record Decimal (BigDecimal bigDecimal, String text) {

		double doubleValue () {
			return bigDecimal == null ? Double.parseDouble(text) : bigDecimal.doubleValue();
		}

		float floatValue () {
			return (float) doubleValue();
		}

	}

	private static Decimal decimal (Object value) {

		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
			long v = ((Number) value).longValue();
			return new Decimal(BigDecimal.valueOf(v), Long.toString(v));
		}

		if (value instanceof Double d) {
			if (d.isNaN() || d.isInfinite()) {
				return new Decimal(null, d.toString());
			}
			return new Decimal(new BigDecimal(d.toString()), d.toString());
		}

		if (value instanceof Float f) {
			return new Decimal(new BigDecimal(f.toString()), f.toString());
		}

		if (value instanceof BigDecimal d) {
			return new Decimal(d, d.toPlainString());
		}

		if (value instanceof BigInteger i) {
			return new Decimal(new BigDecimal(i), i.toString());
		}

		// 真偽は 1 / 0（作り直す前の Convertor と同じ）
		if (value instanceof Boolean b) {
			return b ? new Decimal(BigDecimal.ONE, "true") : new Decimal(BigDecimal.ZERO, "false");
		}

		if (value instanceof String s) {
			String normalized = normalizeNumber(s);
			if (normalized.equals("NaN") || normalized.endsWith("Infinity")) {
				return new Decimal(null, normalized);
			}
			try {
				return new Decimal(new BigDecimal(normalized), s);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("数として読めません: " + quote(s));
			}
		}

		throw new IllegalArgumentException("数ではありません: " + kind(value));

	}

	/**
	 * 整えなくてよい、ふつうの数の文字か（{@code -12.5e3} の形。空白・全角・カンマ・{@code 1d} などは含まない）
	 */
	private static boolean isPlainNumber (String s) {

		int n = s.length();
		if (n == 0 || n > 64) {
			return false;
		}

		int i = s.charAt(0) == '-' || s.charAt(0) == '+' ? 1 : 0;
		int digits = 0;
		boolean dot = false;

		for (; i < n; i++) {
			char c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				digits++;
			} else if (c == '.' && !dot) {
				dot = true;
			} else if ((c == 'e' || c == 'E') && digits > 0) {
				i++;
				if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
					i++;
				}
				if (i == n) {
					return false;
				}
				for (; i < n; i++) {
					c = s.charAt(i);
					if (c < '0' || c > '9') {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		}

		return digits > 0;

	}

	/**
	 * 数の文字を整える（前後の空白・全角の数字と記号・3 桁ごとのカンマ）
	 */
	private static String normalizeNumber (String s) {

		String t = s.strip();

		StringBuilder sb = null;
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			char n = c >= '０' && c <= '９' ? (char) ('0' + (c - '０'))
				: c == '－' || c == 'ー' ? '-'
				: c == '＋' ? '+'
				: c == '．' ? '.'
				: c == '，' ? ','
				: c;
			if (n != c && sb == null) {
				sb = new StringBuilder(t.substring(0, i));
			}
			if (sb != null) {
				sb.append(n);
			}
		}
		if (sb != null) {
			t = sb.toString();
		}

		if (t.indexOf(',') >= 0) {
			if (!t.matches("[+-]?\\d{1,3}(,\\d{3})+(\\.\\d+)?")) {
				throw new IllegalArgumentException("数として読めません: " + quote(s));
			}
			t = t.replace(",", "");
		}

		return t;

	}

	// endregion

	// region 真偽と enum

	private static boolean bool (Object value) {

		if (value instanceof Boolean b) {
			return b;
		}

		if (value instanceof Number n) {
			double d = n.doubleValue();
			if (d == 1) {
				return true;
			}
			if (d == 0) {
				return false;
			}
			throw new IllegalArgumentException("真偽として読めません: " + n);
		}

		if (value instanceof String s) {
			switch (s.strip().toLowerCase(Locale.ROOT)) {
				case "true", "1", "yes", "on":
					return true;
				case "false", "0", "no", "off":
					return false;
				default:
					throw new IllegalArgumentException("真偽として読めません: " + quote(s));
			}
		}

		throw new IllegalArgumentException("真偽ではありません: " + kind(value));

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumConstant (Object value, Class<?> type) {

		String name = text(value).strip();

		try {
			return Enum.valueOf((Class) type, name);
		} catch (IllegalArgumentException ex) {
			for (Object constant : type.getEnumConstants()) {
				if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
					return constant;
				}
			}
		}

		throw new IllegalArgumentException(type.getSimpleName() + " に無い名前です: " + quote(name));

	}

	// endregion

	// region 日時

	private static Object temporal (Object value, Class<?> type, ZoneId zone) {

		// 日時から日時へ（Date → LocalDate など）
		Instant instant = value instanceof Date d ? Instant.ofEpochMilli(d.getTime())
			: value instanceof Calendar c ? Instant.ofEpochMilli(c.getTimeInMillis())
			: value instanceof Instant i ? i
			: value instanceof OffsetDateTime o ? o.toInstant()
			: value instanceof ZonedDateTime z ? z.toInstant()
			: null;
		if (instant != null) {
			return fromZoned(instant.atZone(zone), type, zone, value);
		}
		if (value instanceof LocalDateTime local) {
			return fromZoned(local.atZone(zone), type, zone, value);
		}
		if (value instanceof LocalDate date) {
			return fromZoned(date.atStartOfDay(zone), type, zone, value);
		}

		if (type == Duration.class) {
			return parse(() -> Duration.parse(text(value).strip()), value);
		}
		if (type == Period.class) {
			return parse(() -> Period.parse(text(value).strip()), value);
		}

		boolean wantsInstant = type == Instant.class || Date.class.isAssignableFrom(type) && type != java.sql.Date.class && type != java.sql.Time.class
			|| Calendar.class.isAssignableFrom(type);

		if (value instanceof Number n && wantsInstant) {
			return fromInstant(Instant.ofEpochMilli(n.longValue()), type, zone);
		}

		String s = text(value).strip();
		if (s.startsWith(DATE_PREFIX)) {
			s = s.substring(DATE_PREFIX.length());
		}
		String input = padDate(japaneseToIso(s));

		if (type == LocalTime.class || type == java.sql.Time.class) {
			LocalTime time = parse(() -> LocalTime.from(TIME.parse(input)), value);
			return type == LocalTime.class ? time : java.sql.Time.valueOf(time);
		}

		if (type == LocalDate.class || type == java.sql.Date.class) {
			LocalDate date = parse(() -> LocalDate.from(DATE_TIME.parse(input)), value);
			return type == LocalDate.class ? date : java.sql.Date.valueOf(date);
		}

		if (type != LocalDateTime.class && type != OffsetDateTime.class && type != ZonedDateTime.class && !wantsInstant) {
			throw new IllegalArgumentException(type.getName() + " にできません: " + kind(value));
		}

		TemporalAccessor parsed = parse(() -> DATE_TIME.parse(input), value);
		LocalDate date = LocalDate.from(parsed);
		LocalTime time = parsed.isSupported(ChronoField.HOUR_OF_DAY) ? LocalTime.from(parsed) : LocalTime.MIDNIGHT;
		LocalDateTime local = LocalDateTime.of(date, time);

		if (type == LocalDateTime.class) {
			// 時差があれば、時間帯の時刻にする（時差を捨てて、書かれた時刻のままにはしない）
			return parsed.isSupported(ChronoField.OFFSET_SECONDS)
				? OffsetDateTime.of(local, java.time.ZoneOffset.ofTotalSeconds(parsed.get(ChronoField.OFFSET_SECONDS))).atZoneSameInstant(zone).toLocalDateTime()
				: local;
		}

		OffsetDateTime offset = parsed.isSupported(ChronoField.OFFSET_SECONDS)
			? OffsetDateTime.of(local, java.time.ZoneOffset.ofTotalSeconds(parsed.get(ChronoField.OFFSET_SECONDS)))
			: local.atZone(zone).toOffsetDateTime();

		if (type == OffsetDateTime.class) {
			return offset;
		}
		if (type == ZonedDateTime.class) {
			return parsed.isSupported(ChronoField.OFFSET_SECONDS) ? offset.toZonedDateTime() : local.atZone(zone);
		}

		return fromInstant(offset.toInstant(), type, zone);

	}

	/**
	 * 年月日・時分秒で書いた日時を、/ と : の形にする（{@code 2024年01月15日 10時20分} → {@code 2024/01/15 10:20}）
	 */
	private static String japaneseToIso (String s) {

		if (s.indexOf('年') < 0) {
			return s;
		}

		String replaced = s.replace('年', '/').replace('月', '/').replace("日", "")
			.replace('時', ':').replace('分', ':').replace("秒", "").strip();
		return replaced.endsWith(":") ? replaced.substring(0, replaced.length() - 1) : replaced;

	}

	/**
	 * 月・日・時が 1 桁なら 0 を埋める（{@code 2024/1/5 9:00} → {@code 2024/01/05 09:00}。2.5.12 で読めた形）
	 */
	private static String padDate (String s) {

		if (isPadded(s)) {
			return s;
		}

		Matcher m = UNPADDED_DATE.matcher(s);
		if (!m.lookingAt()) {
			return s;
		}

		StringBuilder sb = new StringBuilder(s.length() + 3)
			.append(m.group(1)).append(m.group(2)).append(twoDigits(m.group(3))).append(m.group(4)).append(twoDigits(m.group(5)));
		if (m.group(6) != null) {
			sb.append(m.group(6)).append(twoDigits(m.group(7)));
		}
		return sb.append(s, m.end(), s.length()).toString();

	}

	/* 0 を埋めなくてよいなら true（区切りの無い形と、2024/01/05・2024/01/05 09:00 の形。よくある形で正規表現を使わない） */
	private static boolean isPadded (String s) {

		if (s.length() < 5 || !isDateSeparator(s.charAt(4))) {
			return true;
		}
		if (s.length() < 10 || !isDateSeparator(s.charAt(7))) {
			return false;
		}
		if (s.length() == 10) {
			return true;
		}
		char afterDate = s.charAt(10);
		return afterDate != ' ' && afterDate != 'T' || s.length() > 13 && s.charAt(13) == ':';

	}

	private static boolean isDateSeparator (char c) {

		return c == '/' || c == '-';

	}

	private static String twoDigits (String digits) {

		return digits.length() == 1 ? "0" + digits : digits;

	}

	/**
	 * 日時の値を、日時の型にする
	 */
	private static Object fromZoned (ZonedDateTime zoned, Class<?> type, ZoneId zone, Object original) {

		if (type == LocalDateTime.class) {
			return zoned.toLocalDateTime();
		}
		if (type == LocalDate.class) {
			return zoned.toLocalDate();
		}
		if (type == LocalTime.class) {
			return zoned.toLocalTime();
		}
		if (type == java.sql.Date.class) {
			return java.sql.Date.valueOf(zoned.toLocalDate());
		}
		if (type == java.sql.Time.class) {
			return java.sql.Time.valueOf(zoned.toLocalTime());
		}
		if (type == OffsetDateTime.class) {
			return original instanceof OffsetDateTime o ? o : zoned.toOffsetDateTime();
		}
		if (type == ZonedDateTime.class) {
			return original instanceof ZonedDateTime z ? z : zoned;
		}
		if (type == Instant.class || Date.class.isAssignableFrom(type) || Calendar.class.isAssignableFrom(type)) {
			return fromInstant(zoned.toInstant(), type, zone);
		}
		throw new IllegalArgumentException(type.getName() + " にできません: " + kind(original));

	}

	private static Object fromInstant (Instant instant, Class<?> type, ZoneId zone) {

		if (type == Instant.class) {
			return instant;
		}
		if (type == java.sql.Timestamp.class) {
			return java.sql.Timestamp.from(instant);
		}
		if (Calendar.class.isAssignableFrom(type)) {
			Calendar calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone(zone));
			calendar.setTimeInMillis(instant.toEpochMilli());
			return calendar;
		}
		return Date.from(instant);

	}

	private static <T> T parse (java.util.function.Supplier<T> parser, Object value) {

		try {
			return parser.get();
		} catch (java.time.DateTimeException ex) {
			throw new IllegalArgumentException("日時として読めません: " + kind(value));
		}

	}

	// endregion

	private static String kind (Object value) {

		if (value instanceof String s) {
			return quote(s);
		}
		if (value instanceof java.util.Map) {
			return "オブジェクト";
		}
		if (value instanceof java.util.List) {
			return "配列";
		}
		return String.valueOf(value);

	}

	private static String quote (String s) {

		return "\"" + (s.length() > 40 ? s.substring(0, 40) + "…" : s) + "\"";

	}

}
