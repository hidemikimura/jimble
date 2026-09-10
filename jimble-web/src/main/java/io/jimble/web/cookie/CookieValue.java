package io.jimble.web.cookie;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Cookie の値を、ヘッダに載せられる形に直す
 *
 * <h2>なぜ要るか</h2>
 * <p>
 * <b>HTTP のヘッダは ASCII である。</b>日本語をそのまま {@code Set-Cookie} に書くと、
 * <b>例外も警告も出ないまま {@code ?} に置き換わって届く</b>。
 * </p>
 * <pre>
 * flash__message=&lt;署名&gt;|????ID???????????
 * </pre>
 * <p>
 * これは {@code examples/approval-auth} を書いていて見つけた。
 * {@code flash().put("message", "登録しました")} は<b>日本語のアプリでいちばん最初に書かれる形</b>
 * なのに、そこが壊れていた。{@code examples/blog} の結合テストが
 * <b>「{@code id="flash"} があるか」しか見ていなかった</b>ので、壊れたまま緑だった。
 * </p>
 *
 * <h2>やり方</h2>
 * <p>
 * <b>percent-encode する。</b>{@link java.net.URLEncoder} は使わない——
 * あれは空白を {@code +} にするので、<b>復号のときに {@code +} を空白に戻してしまう</b>。
 * Cookie の値には署名（Base64）や区切りの {@code |} が入るので、
 * <b>{@code +} を特別扱いする方式は使えない</b>。
 * </p>
 *
 * <h2>そのまま通す文字</h2>
 * <p>
 * RFC 6265 の {@code cookie-octet}（印字できる ASCII から
 * 空白・{@code "}・{@code ,}・{@code ;}・{@code \}・DEL を除いたもの）<b>から {@code %} を除いた</b>もの。
 * {@code %} を通すと、<b>復号のときに元から入っていた {@code %} と区別が付かない</b>。
 * </p>
 * <p>
 * <b>{@code |} も {@code =} も {@code +} も、そのまま通る。</b>
 * つまり<b>いま出ている署名つき Cookie の見た目は1文字も変わらない</b>——
 * 変わるのは、いままで壊れていた非 ASCII と {@code %} だけである。
 * </p>
 *
 * <h2>直しても救えないもの</h2>
 * <p>
 * <b>すでにブラウザにある Cookie で、値に {@code %} が入っているもの。</b>
 * 直したあとの版はそれを「符号化されたもの」として読むので、
 * {@code %41} のような並びが {@code A} になる。<b>そういう値を書いているアプリは、
 * 版を上げたときに一度だけ読み違える</b>（次に書き直された時点で直る）。
 * </p>
 */
final class CookieValue {

	/** 16進の文字 */
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private CookieValue () {
	}

	/**
	 * ヘッダに載せられる形にする
	 *
	 * @param value	値。null なら null
	 * @return	符号化した値
	 */
	static String encode (String value) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		/*
		 * <b>そのまま通せるなら、何も作らない。</b>ほとんどの Cookie は ASCII なので、
		 * ここで作ると1リクエストあたりの割り当てが黙って増える（要件 NF-P-05）
		 */
		boolean plain = true;

		for (byte b : bytes) {
			if (!isPlain(b)) {
				plain = false;
				break;
			}
		}

		if (plain) {
			return value;
		}

		StringBuilder builder = new StringBuilder(bytes.length + 16);

		for (byte b : bytes) {

			if (isPlain(b)) {
				builder.append((char) (b & 0xff));
				continue;
			}

			builder.append('%')
				.append(HEX[(b >> 4) & 0x0f])
				.append(HEX[b & 0x0f]);

		}

		return builder.toString();

	}

	/**
	 * 受け取った値を元に戻す
	 *
	 * <p>
	 * <b>壊れた並びは、そのまま通す。</b>{@code %} で終わっていたり
	 * {@code %zz} だったりしたときに例外にすると、
	 * <b>他人が置いた Cookie 1つでリクエストが落ちる</b>。
	 * </p>
	 *
	 * @param value	値。null なら null
	 * @return	元に戻した値
	 */
	static String decode (String value) {

		if (value == null || value.indexOf('%') < 0) {
			return value;
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());

		for (int index = 0; index < value.length(); index++) {

			char c = value.charAt(index);

			if (c != '%' || index + 2 >= value.length()) {
				append(bytes, c);
				continue;
			}

			int high = hex(value.charAt(index + 1));
			int low = hex(value.charAt(index + 2));

			if (high < 0 || low < 0) {
				append(bytes, c);
				continue;
			}

			bytes.write((high << 4) + low);
			index += 2;

		}

		return bytes.toString(StandardCharsets.UTF_8);

	}

	/**
	 * 16進1文字を数にする
	 *
	 * @param c	文字
	 * @return	0〜15。16進でなければ -1
	 */
	private static int hex (char c) {

		if (c >= '0' && c <= '9') {
			return c - '0';
		}

		if (c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}

		if (c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}

		return -1;

	}

	/**
	 * そのまま通してよい byte か
	 *
	 * @param b	byte
	 * @return	通してよい場合 = true
	 */
	private static boolean isPlain (byte b) {

		int value = b & 0xff;

		// 印字できない・非 ASCII
		if (value <= 0x20 || value >= 0x7f) {
			return false;
		}

		return switch ((char) value) {
			// RFC 6265 が Cookie の値に許していない文字
			case '"', ',', ';', '\\' -> false;
			// 元から入っていた % と、符号化した % を区別できなくなる
			case '%' -> false;
			default -> true;
		};

	}

	/**
	 * 1文字を書き足す
	 *
	 * @param bytes	行き先
	 * @param c		文字
	 */
	private static void append (ByteArrayOutputStream bytes, char c) {

		if (c < 0x80) {
			bytes.write(c);
			return;
		}

		/*
		 * <b>符号化されていない非 ASCII が来ることもある。</b>
		 * 直す前の版が書いた Cookie や、他所が置いたものである
		 */
		byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
		bytes.write(raw, 0, raw.length);

	}

}
