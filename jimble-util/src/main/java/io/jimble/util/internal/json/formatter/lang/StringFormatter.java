package io.jimble.util.internal.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.internal.json.formatter.FormatterConfigKeys;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * Stringフォーマットクラス.
 * 
 * @author DN
 */
public class StringFormatter implements IFormatter {

	/** インスタンス. */
	public static final StringFormatter INSTANCE = new StringFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		writer.write('"');

		String[] exEscapes = null;
		boolean isEscape = false;
		int exLength = 0;
		Object confObj = null;
		if (conf != null && (confObj = conf.get(FormatterConfigKeys.FORMAT_STRING_ESCAPE)) != null && confObj instanceof String[]) {
			exEscapes = (String[]) confObj;
			exLength = exEscapes.length;
			isEscape = true;
		}

		String s = PropertyUtil.toString(obj);
		if (conf != null && conf.isRemove4ByteCharacter) {
			s = s.replaceAll("[^\\u0000-\\uFFFF]", "");
		}
		boolean isOutputStrict = conf != null && conf.isOutputStrict;
		int length = s.length();
//		char[] charArray = s.toCharArray();
		int start = 0;
		for (int i = 0; i < length; i++) {
			char c = s.charAt(i);
			if (isEscape && c < exLength && exEscapes[c] != null) {
				String w = exEscapes[c];
				if (start < i) {
					writer.write(s, start, i);
				}
				if (w.length() > 0) {
					writer.write(w);
				}
				start = i + 1;
			} else if (c < ESCAPE_CHARS_LENGTH) {
				int x = ESCAPE_CHARS[c];
				if (x > 0) {
					if (start < i) {
						writer.write(s, start, i);
					}
					writer.write('\\');
					writer.write((char) x);
					start = i + 1;
				} else if (x == -1 || (x == -2 && isOutputStrict)) {
					if (start < i) {
						writer.write(s, start, i);
					}
					writer.write("\\u00");
					writer.write(STRICT_CALC_STRING.charAt(c / 16));
					writer.write(STRICT_CALC_STRING.charAt(c % 16));
					start = i + 1;
				}
			} else if (c == '\u2028') {
				if (start < i) {
					writer.write(s, start, i);
				}
				writer.write("\\u2028");
				start = i + 1;
			} else if (c == '\u2029') {
				if (start < i) {
					writer.write(s, start, i);
				}
				writer.write("\\u2029");
				start = i + 1;
			}
		}
		if (start < length) {
			writer.write(s, start, length);
		}

		writer.write('"');

	}

	/** エスケープ配列長. */
	private static final int ESCAPE_CHARS_LENGTH = 128;

	/** エスケープ配列. */
	private static final int[] ESCAPE_CHARS = new int[ESCAPE_CHARS_LENGTH];
	static {
		for (int i = 0; i < 32; i++) {
			ESCAPE_CHARS[i] = -1;
		}
		ESCAPE_CHARS['\b'] = 'b';
		ESCAPE_CHARS['\t'] = 't';
		ESCAPE_CHARS['\n'] = 'n';
		ESCAPE_CHARS['\f'] = 'f';
		ESCAPE_CHARS['\r'] = 'r';
		ESCAPE_CHARS['"'] = '"';
		ESCAPE_CHARS['\\'] = '\\';
		ESCAPE_CHARS['<'] = -2;
		ESCAPE_CHARS['>'] = -2;
		ESCAPE_CHARS[0x7F] = -1;
		ESCAPE_CHARS['/'] = '/';
	}

	private static final String STRICT_CALC_STRING = "0123456789ABCDEF";

	/**
	 * 文字列をJSONエスケープする
	 *
	 * @param src	文字列
	 * @return	JSONエスケープされた文字列
	 */
	public static String escape (String src) {

		if (src == null || src.length() == 0) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		for (char c : src.toCharArray()) {
			if (c < ESCAPE_CHARS_LENGTH) {
				int x = ESCAPE_CHARS[c];
				if (x > 0) {
					sb.append('\\');
					sb.append((char) x);
				} else if (x == -1 || x == -2) {
					sb.append("\\u00");
					sb.append(STRICT_CALC_STRING.charAt(c / 16));
					sb.append(STRICT_CALC_STRING.charAt(c % 16));
				} else {
					sb.append(c);
				}
			} else if (c == '\u2028') {
				sb.append("\\u2028");
			} else if (c == '\u2029') {
				sb.append("\\u2029");
			} else {
				sb.append(c);
			}

		}

		return sb.toString();

	}

}
