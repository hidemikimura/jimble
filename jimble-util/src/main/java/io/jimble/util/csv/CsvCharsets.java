package io.jimble.util.csv;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * CSV の文字コードの判定
 */
final class CsvCharsets {

	private CsvCharsets () {
	}

	/**
	 * UTF 系か（BOM を持ちうる文字コードか）
	 *
	 * <p>UTF-8 / UTF-16 / UTF-16BE / UTF-16LE / UTF-32 / UTF-32BE / UTF-32LE。</p>
	 *
	 * @param charset	文字コード
	 * @return	UTF 系の場合 = true
	 */
	static boolean isUtf (Charset charset) {

		return charset != null && charset.name().toUpperCase(Locale.ROOT).startsWith("UTF-");

	}

	/**
	 * 符号化するときに、Java が自分で BOM を書く文字コードか
	 *
	 * <p>
	 * <b>{@code UTF-16}（向きの指定なし）は、書き始めに BOM を自分で書く</b>（ビッグエンディアン）。
	 * ここへさらに BOM を書くと2つになる。
	 * </p>
	 *
	 * @param charset	文字コード
	 * @return	自分で書く場合 = true
	 */
	static boolean encoderWritesBom (Charset charset) {

		return charset != null && "UTF-16".equalsIgnoreCase(charset.name());

	}

}
