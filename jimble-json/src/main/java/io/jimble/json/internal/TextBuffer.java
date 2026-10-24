package io.jimble.json.internal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 書き出しの溜め置き（D-307）
 *
 * <p>
 * <b>Latin-1 の文字だけのあいだは 1 文字 1 バイトで持つ</b>（JSON の多くは ASCII）。
 * それ以外の文字が来たら、そこから char の配列に移る。
 * {@code StringBuilder.append(char)} は 1 文字ごとに文字の幅と大きさを確かめるので、
 * 「,」「:」「"」を1つずつ足す書き出しでは、それが時間のいちばん大きな部分になっていた。
 * </p>
 */
@SuppressWarnings("deprecation")   // String.getBytes(int, int, byte[], int) は Latin-1 の文字を写すのにいちばん速い
final class TextBuffer {

	/* Latin-1 のあいだの中身 */
	private byte[] bytes;

	/* Latin-1 でない文字が来てからの中身 */
	private char[] chars;

	/* 書いた長さ */
	private int len;

	/**
	 * コンストラクタ
	 *
	 * @param capacity	はじめの大きさ
	 */
	TextBuffer (int capacity) {

		this.bytes = new byte[capacity];

	}

	/**
	 * 書いた長さ
	 */
	int length () {

		return len;

	}

	/**
	 * 空にする（大きさはそのまま）
	 */
	void clear () {

		len = 0;

	}

	/**
	 * 1文字足す
	 */
	void append (char c) {

		if (chars == null && c < 0x100) {
			if (len == bytes.length) {
				bytes = Arrays.copyOf(bytes, bytes.length * 2);
			}
			bytes[len++] = (byte) c;
			return;
		}

		appendWide(c);

	}

	private void appendWide (char c) {

		if (chars == null) {
			inflate(len + 1);
		}
		if (len == chars.length) {
			chars = Arrays.copyOf(chars, chars.length * 2);
		}
		chars[len++] = c;

	}

	/**
	 * ASCII だけと分かっている文字列を足す（数値・true / false など）
	 */
	void appendAscii (String s) {

		int n = s.length();

		if (chars == null) {
			ensureBytes(n);
			s.getBytes(0, n, bytes, len);
			len += n;
			return;
		}

		ensureChars(n);
		s.getChars(0, n, chars, len);
		len += n;

	}

	/**
	 * 文字列を足す（Latin-1 の文字だけかを確かめてから）
	 */
	void append (String s) {

		int n = s.length();
		boolean latin1 = true;
		if (chars == null) {
			for (int i = 0; i < n; i++) {
				if (s.charAt(i) >= 0x100) {
					latin1 = false;
					break;
				}
			}
		}
		append(s, 0, n, latin1);

	}

	/**
	 * 文字列の一部を足す
	 *
	 * @param latin1	その範囲が Latin-1 の文字だけと分かっている場合 = true
	 */
	void append (String s, int start, int end, boolean latin1) {

		int n = end - start;
		if (n <= 0) {
			return;
		}

		if (chars == null && latin1) {
			ensureBytes(n);
			s.getBytes(start, end, bytes, len);
			len += n;
			return;
		}

		if (chars == null) {
			inflate(len + n);
		}
		ensureChars(n);
		s.getChars(start, end, chars, len);
		len += n;

	}

	/**
	 * 整数を足す（文字列を作らない）
	 */
	void append (long value) {

		if (value == Long.MIN_VALUE) {
			appendAscii("-9223372036854775808");
			return;
		}

		int digits = 1;
		long v = value < 0 ? -value : value;
		for (long x = v; x >= 10; x /= 10) {
			digits++;
		}
		int total = value < 0 ? digits + 1 : digits;

		if (chars == null) {
			ensureBytes(total);
			int at = len;
			if (value < 0) {
				bytes[at++] = '-';
			}
			for (int i = at + digits - 1; i >= at; i--) {
				bytes[i] = (byte) ('0' + (v % 10));
				v /= 10;
			}
		} else {
			ensureChars(total);
			int at = len;
			if (value < 0) {
				chars[at++] = '-';
			}
			for (int i = at + digits - 1; i >= at; i--) {
				chars[i] = (char) ('0' + (v % 10));
				v /= 10;
			}
		}

		len += total;

	}

	/**
	 * 中身が ASCII だけなら、そのまま OutputStream に書く（UTF-8 でも Latin-1 でも同じバイトになる）
	 *
	 * @param stream	書き先
	 * @return	書いた場合 = true（ASCII でない文字があれば何もせずに false）
	 * @throws java.io.IOException	書き先の失敗
	 */
	boolean writeAsciiTo (java.io.OutputStream stream) throws java.io.IOException {

		if (chars != null) {
			return false;
		}

		for (int i = 0; i < len; i++) {
			if (bytes[i] < 0) {
				return false;
			}
		}

		stream.write(bytes, 0, len);
		return true;

	}

	/**
	 * 1文字読む
	 */
	char charAt (int i) {

		return chars == null ? (char) (bytes[i] & 0xFF) : chars[i];

	}

	/**
	 * 一部を写す
	 */
	void getChars (int start, int end, char[] dst, int dstBegin) {

		if (chars != null) {
			System.arraycopy(chars, start, dst, dstBegin, end - start);
			return;
		}

		for (int i = start; i < end; i++) {
			dst[dstBegin++] = (char) (bytes[i] & 0xFF);
		}

	}

	/**
	 * 一部を文字列にする
	 */
	String substring (int start) {

		return chars == null
			? new String(bytes, start, len - start, StandardCharsets.ISO_8859_1)
			: new String(chars, start, len - start);

	}

	@Override
	public String toString () {

		return substring(0);

	}

	private void ensureBytes (int n) {

		if (len + n > bytes.length) {
			bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, len + n));
		}

	}

	private void ensureChars (int n) {

		if (len + n > chars.length) {
			chars = Arrays.copyOf(chars, Math.max(chars.length * 2, len + n));
		}

	}

	/**
	 * char の配列に移る
	 */
	private void inflate (int need) {

		chars = new char[Math.max(bytes.length, need)];
		for (int i = 0; i < len; i++) {
			chars[i] = (char) (bytes[i] & 0xFF);
		}
		bytes = null;

	}

}
