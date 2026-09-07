package io.jimble.web.router;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * パスセグメントのパーセントデコード
 *
 * <p>
 * {@code URLDecoder} は "+" を空白に変換する（フォームの規則）。
 * パスセグメントでは "+" は "+" のままでなければならないため、自前で持つ。
 * </p>
 *
 * <p>
 * 壊れたエスケープ（"%zz" や末尾の "%"）は例外にせず、そのまま通す。
 * </p>
 */
final class PercentDecoder {

	/**
	 * コンストラクタ
	 */
	private PercentDecoder () {

	}

	/**
	 * デコードする
	 *
	 * @param value	値
	 * @return	デコード結果
	 */
	static String decode (String value) {

		if (value.indexOf('%') < 0) {
			return value;
		}

		int length = value.length();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(length);

		int index = 0;
		while (index < length) {

			char c = value.charAt(index);

			if (c == '%' && index + 2 < length) {
				int high = hex(value.charAt(index + 1));
				int low = hex(value.charAt(index + 2));
				if (high >= 0 && low >= 0) {
					buffer.write((high << 4) + low);
					index += 3;
					continue;
				}
			}

			buffer.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
			index++;

		}

		return buffer.toString(StandardCharsets.UTF_8);

	}

	/**
	 * 16進数1桁を数値にする
	 *
	 * @param c	文字
	 * @return	数値。16進数でなければ -1
	 */
	private static int hex (char c) {

		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}
		if (c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}
		return -1;

	}

}
