package io.jimble.web.auth.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 時刻に基づく使い捨てコード（TOTP。RFC 6238。要件 F-W-32）
 *
 * <p>
 * 認証アプリ（Google Authenticator など）が出す6桁である。
 * <b>中身は HMAC-SHA1 なので、JDK だけで書ける</b>——外部のライブラリは要らない。
 * </p>
 *
 * <p>
 * <b>ここは入出力を持たない。</b>DB も設定もセッションも触らないので、
 * <b>RFC の試験ベクタでそのまま確かめられる</b>（{@code TotpTest}）。
 * </p>
 *
 * <h2>ずれを許す</h2>
 * <p>
 * 手元の時計とサーバーの時計は必ず少しずれる。
 * <b>前後いくつかの窓も見る</b>（既定で前後1つ＝およそ ±30 秒）。
 * <b>広げすぎない</b>——窓を1つ増やすたびに、総当たりで当たる確率も増える。
 * </p>
 */
public final class Totp {

	/** 合わなかったとき */
	public static final long NO_MATCH = -1;

	/** base32 の文字（RFC 4648） */
	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	/** 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	private Totp () {
	}

	// region 作る

	/**
	 * 秘密鍵を作る
	 *
	 * @return	秘密鍵（20 バイト。SHA-1 の出力と同じ長さ）
	 */
	public static byte[] secret () {

		byte[] secret = new byte[20];
		RANDOM.nextBytes(secret);

		return secret;

	}

	/**
	 * その時刻のコード
	 *
	 * @param secret		秘密鍵
	 * @param epochSecond	時刻（秒）
	 * @param period		1つの窓の長さ（秒）
	 * @param digits		桁数
	 * @return	コード
	 */
	public static String at (byte[] secret, long epochSecond, int period, int digits) {

		return generate(secret, Math.floorDiv(epochSecond, period), digits);

	}

	/**
	 * その窓のコード（HOTP。RFC 4226）
	 *
	 * @param secret	秘密鍵
	 * @param counter	窓の番号
	 * @param digits	桁数
	 * @return	コード
	 */
	public static String generate (byte[] secret, long counter, int digits) {

		byte[] mac = hmac(secret, counter);

		/*
		 * <b>末尾4ビットが「どこから読むか」を指す</b>（動的切り出し）。
		 * 固定の位置から読むと、HMAC の一部しか使わないことになる。
		 */
		int offset = mac[mac.length - 1] & 0x0F;

		int binary = ((mac[offset] & 0x7F) << 24)
			| ((mac[offset + 1] & 0xFF) << 16)
			| ((mac[offset + 2] & 0xFF) << 8)
			| (mac[offset + 3] & 0xFF);

		int modulus = (int) Math.pow(10, digits);

		return String.format("%0" + digits + "d", binary % modulus);

	}

	// endregion

	// region 確かめる

	/**
	 * 合っているか。合っていればその窓の番号を返す
	 *
	 * <p>
	 * <b>番号を返すのは、使い回しを弾くためである。</b>
	 * 同じコードは30秒のあいだ何度でも通ってしまうので、
	 * <b>呼び出し側が「前より新しい窓か」を見る</b>（{@link Mfa}）。
	 * </p>
	 *
	 * @param secret		秘密鍵
	 * @param code			入力されたコード
	 * @param epochSecond	いま（秒）
	 * @param period		1つの窓の長さ（秒）
	 * @param digits		桁数
	 * @param window		前後いくつの窓まで許すか
	 * @return	窓の番号。合わなければ {@link #NO_MATCH}
	 */
	public static long verify (
		byte[] secret, String code, long epochSecond, int period, int digits, int window) {

		if (code == null) {
			return NO_MATCH;
		}

		String trimmed = code.replaceAll("[\\s-]", "");

		if (trimmed.length() != digits) {
			return NO_MATCH;
		}

		long current = Math.floorDiv(epochSecond, period);

		long matched = NO_MATCH;

		/*
		 * <b>合った時点で抜けない。</b>抜けると、
		 * <b>どの窓で合ったかが処理時間に出る</b>（前の窓ほど早く返る）。
		 * 窓はせいぜい数個なので、全部回しても安い。
		 */
		for (long counter = current - window; counter <= current + window; counter++) {

			if (equalsConstantTime(generate(secret, counter, digits), trimmed)) {
				matched = counter;
			}

		}

		return matched;

	}

	// endregion

	// region base32

	/**
	 * base32 にする（認証アプリに渡す形）
	 *
	 * @param bytes	バイト列
	 * @return	base32（詰め物なし）
	 */
	public static String toBase32 (byte[] bytes) {

		StringBuilder out = new StringBuilder();

		int buffer = 0;
		int bits = 0;

		for (byte value : bytes) {

			buffer = (buffer << 8) | (value & 0xFF);
			bits += 8;

			while (bits >= 5) {
				out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
				bits -= 5;
			}

		}

		if (bits > 0) {
			out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
		}

		return out.toString();

	}

	/**
	 * base32 を戻す
	 *
	 * @param value	base32
	 * @return	バイト列
	 */
	public static byte[] fromBase32 (String value) {

		String cleaned = value.replaceAll("[\\s=-]", "").toUpperCase(java.util.Locale.ROOT);

		byte[] out = new byte[cleaned.length() * 5 / 8];

		int buffer = 0;
		int bits = 0;
		int index = 0;

		for (char letter : cleaned.toCharArray()) {

			int position = BASE32.indexOf(letter);

			if (position < 0) {
				throw new IllegalArgumentException("base32 ではありません: " + letter);
			}

			buffer = (buffer << 5) | position;
			bits += 5;

			if (bits >= 8) {
				out[index++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
				bits -= 8;
			}

		}

		return out;

	}

	// endregion

	// region 中身

	/**
	 * HMAC-SHA1
	 *
	 * @param secret	秘密鍵
	 * @param counter	窓の番号
	 * @return	MAC
	 */
	private static byte[] hmac (byte[] secret, long counter) {

		byte[] message = new byte[8];

		for (int i = 7; i >= 0; i--) {
			message[i] = (byte) (counter & 0xFF);
			counter >>>= 8;
		}

		try {

			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret, "HmacSHA1"));

			return mac.doFinal(message);

		} catch (Exception cause) {
			throw new IllegalStateException("HMAC-SHA1 が使えません", cause);
		}

	}

	/**
	 * 時間を測られない比較
	 *
	 * @param left	左
	 * @param right	右
	 * @return	同じ場合 = true
	 */
	private static boolean equalsConstantTime (String left, String right) {

		return MessageDigest.isEqual(
			left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));

	}

	// endregion

}
