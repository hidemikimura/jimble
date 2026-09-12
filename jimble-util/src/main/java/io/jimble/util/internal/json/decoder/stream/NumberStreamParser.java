package io.jimble.util.internal.json.decoder.stream;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.decoder.stream.util.IInputStream;

import java.math.BigDecimal;

/**
 * 数値解析クラス.
 *
 * @author DN
 */
public class NumberStreamParser implements IStreamParser {

	/**
	 * インスタンス.
	 */
	public static final NumberStreamParser INSTANCE_NO_KEY = new NumberStreamParser(false);

	/**
	 * インスタンス.
	 */
	public static final NumberStreamParser INSTANCE_KEY = new NumberStreamParser(true);
	
	private boolean isKey;
	
	public NumberStreamParser (boolean isKey) {
		
		this.isKey = isKey;
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	/*
	 * switch を<b>わざと落として</b>いる。
	 * 「区切り文字はどれも同じ扱い」「: のときだけ先にキーを切り替える」
	 * という書き方で、break を入れると<b>区切り文字が値の一部として読まれる</b>。
	 */
	@SuppressWarnings("fallthrough")
	public Object parse (Configration conf, IInputStream stream) {

		stream.returnPos();

		StringBuilder sb = new StringBuilder();

		boolean isBreak = false;
		boolean isDecimal = false;

		int functionCount = 0;
		boolean isFunction = false;

		int val = -1;
		while (!isBreak && (val = stream.readInt()) != -1) {

			switch (val) {
				case '}':
					if (isFunction) {
						sb.append((char) val);
						functionCount--;
						if (functionCount <= 0) {
							isBreak = true;
						}
					} else if (!isFunction) {
						stream.returnPos();
						isBreak = true;
					}
					break;
				case '{':
					if (isFunction) {
						sb.append((char) val);
						functionCount++;
					} else {
						isBreak = true;
					}
					break;
				case ':':
				case ']':
					if (!isFunction) {
						stream.returnPos();
					}
				case '(':
				case ')':
				case '[':
				case '"':
				case '\'':
				case '\r':
				case '\n':
				case '\t':
				case '\f':
				case '\b':
				case ' ':
				case ',':
				case 0xFEFF:
					if (!isFunction && sb.toString().trim().equals("function")) {
						isFunction = true;
					}
					if (isFunction) {
						sb.append((char) val);
					} else {
						isBreak = true;
					}
					break;
				case '.':
					if (!isFunction) {
						isDecimal = true;
					}
				default:
					sb.append((char) val);
					break;
			}

		}

		String s = sb.toString();

		if (STRING_TRUE.equals(s)) {
			return Boolean.TRUE;
		}

		if (STRING_FALSE.equals(s)) {
			return Boolean.FALSE;
		}

		if (STRING_NULL.equals(s)) {
			return null;
		}
		
		if (isKey) {
			return s;
		}

		BigDecimal bd = parseBigDecimal(s);

		if (bd == null) {
			return s;
		}

		if (isDecimal) {
			// 小数
			if (bd.compareTo(FLOAT_MAX_VALUE) < 1) {
				return Float.parseFloat(s);
			}
			if (bd.compareTo(DOUBLE_MAX_VALUE) < 1) {
				return Double.parseDouble(s);
			}
		} else {
			// 整数
			if (bd.compareTo(INT_MAX_VALUE) < 1) {
				return bd.intValue();
			}
			if (bd.compareTo(LONG_MAX_VALUE) < 1) {
				return bd.longValue();
			}
		}

		return bd;

	}

	/* int最大値. */
	private static final BigDecimal INT_MAX_VALUE = new BigDecimal(Integer.MAX_VALUE);

	/* long最大値. */
	private static final BigDecimal LONG_MAX_VALUE = new BigDecimal(Long.MAX_VALUE);

	/* float最大値. */
	private static final BigDecimal FLOAT_MAX_VALUE = new BigDecimal(Float.MAX_VALUE);

	/* double最大値. */
	private static final BigDecimal DOUBLE_MAX_VALUE = new BigDecimal(Double.MAX_VALUE);

	/* true */
	private static final String STRING_TRUE = "true";

	/* false */
	private static final String STRING_FALSE = "false";

	/* null */
	private static final String STRING_NULL = "null";

	/**
	 * 文字列をBigDecimalに変換する.
	 *
	 * @param s 文字列
	 * @return BigDecimal
	 */
	private BigDecimal parseBigDecimal (String s) {

		try {
			return new BigDecimal(s);
		} catch (Exception e) {
			return null;
		}
	}

}
