package io.jimble.util.json.decoder.stream;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.json.decoder.DecodeConfigKeys;
import io.jimble.util.json.decoder.stream.util.IInputStream;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * 文字列解析クラス.
 *
 * @author DN
 */
public class StringStreamParser implements IStreamParser {

	/**
	 * インスタンス.
	 */
	public static final StringStreamParser INSTANCE1 = new StringStreamParser('\'');

	/**
	 * インスタンス.
	 */
	public static final StringStreamParser INSTANCE2 = new StringStreamParser('"');

	/**
	 * 終了文字列.
	 */
	private char end;

	/**
	 * コンストラクタ.
	 *
	 * @param end 終了文字列
	 */
	public StringStreamParser (char end) {

		this.end = end;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object parse (Configration conf, IInputStream stream) {

		String[] unescape = null;
		boolean isEscape = false;
		Object confObj = null;
		if (conf != null && (confObj = conf.get(DecodeConfigKeys.DECODE_STRING_UNESCAPE)) != null && confObj instanceof String[]) {
			unescape = (String[]) confObj;
			isEscape = true;
		}

		StringBuilder sb = new StringBuilder();

		int val = -1;
		while ((val = stream.readInt()) != -1) {

			if (val == end) {
				break;
			}

			if (isEscape && val < unescape.length && unescape[val] != null) {
				int len = unescape[val].length();
				if (len > 0) {
					sb.append(unescape[val], 0, len);
				}
			} else if (val == '\\') {
				int tval = stream.readInt();
				if (tval == -1) {
					break;
				}
				int eval;
				if (tval < ESCAPE_CHARS_LENGTH) {
					if ((eval = ESCAPE_CHARS[tval]) > 0) {
						sb.append((char) eval);
					} else {
						int escape = '\0';
						int point = 2;
						while (point < 6) {
							tval = stream.readInt();
							if (tval == -1) {
								point = 6;
							} else {
								int hex = (tval >= CHAR_0 && tval <= CHAR_9) ? tval - 48 :
									(tval >= CHAR_A && tval <= CHAR_F) ? tval - 65 + 10 :
										(tval >= CHAR_a && tval <= CHAR_f) ? tval - 97 + 10 : -1;
								escape |= (hex << ((5 - point) * 4));
								point++;
							}
						}
						if (tval == -1) {
							break;
						}
						sb.append((char) escape);
					}
				}
			} else {
				sb.append((char) val);
			}

		}

		String s = sb.toString();

		if (conf != null && conf.isOutputDateType && s.startsWith("date::")) {
			s = s.substring(6);
			if (conf.outputDateTypeFormat != null) {
				try {
					LocalDateTime d = LocalDateTime.parse(s, conf.outputDateTypeFormat);
					Calendar calendar = Calendar.getInstance();
					calendar.set(Calendar.YEAR, d.getYear());
					calendar.set(Calendar.MONTH, d.getMonth().ordinal());
					calendar.set(Calendar.DAY_OF_MONTH, d.getDayOfMonth());
					calendar.set(Calendar.HOUR_OF_DAY, d.getHour());
					calendar.set(Calendar.MINUTE, d.getMinute());
					calendar.set(Calendar.SECOND, d.getSecond());
					calendar.set(Calendar.MILLISECOND, 0);
					return calendar.getTime();
				} catch (Exception ex) {
				}
			}
			try {
				return Convertor.convert(null, s, Date.class);
			} catch (Exception ex) {
				return s;
			}
		}

		if (STRING_TRUE.equals(s)) {
			return Boolean.TRUE;
		}

		if (STRING_FALSE.equals(s)) {
			return Boolean.FALSE;
		}

		return s;

	}

	private static final char CHAR_0 = '0';
	private static final char CHAR_9 = '9';
	private static final char CHAR_A = 'A';
	private static final char CHAR_F = 'F';
	private static final char CHAR_a = 'a';
	private static final char CHAR_f = 'f';

	/* true */
	private static final String STRING_TRUE = "true";

	/* false */
	private static final String STRING_FALSE = "false";

	/* エスケープ配列長 */
	private static final int ESCAPE_CHARS_LENGTH = 128;

	/* エスケープ配列 */
	private static final int[] ESCAPE_CHARS = new int[ESCAPE_CHARS_LENGTH];
	static {
		ESCAPE_CHARS['b'] = '\b';
		ESCAPE_CHARS['t'] = '\t';
		ESCAPE_CHARS['n'] = '\n';
		ESCAPE_CHARS['f'] = '\f';
		ESCAPE_CHARS['r'] = '\r';
		ESCAPE_CHARS['"'] = '"';
		ESCAPE_CHARS['\\'] = '\\';
		ESCAPE_CHARS['/'] = '/';
	}

}
