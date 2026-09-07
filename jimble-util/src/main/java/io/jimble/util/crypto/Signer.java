package io.jimble.util.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 署名（改ざん検知）
 *
 * <p>
 * HMAC-SHA256 で値に署名する。<b>中身は隠れない。</b>読まれて困る値は
 * {@link Aead} で暗号化すること。
 * </p>
 *
 * <p>
 * 署名つきの値は {@code <署名>|<値>} の形になる。
 * </p>
 */
public final class Signer {

	/** アルゴリズム */
	private static final String ALGORITHM = "HmacSHA256";

	/** 署名と値の区切り */
	private static final char SEPARATOR = '|';

	private Signer () {}

	/**
	 * 署名する
	 *
	 * @param value		値
	 * @param secret	鍵
	 * @return	署名つきの値
	 */
	public static String sign (String value, String secret) {

		if (value == null) {
			return null;
		}

		return mac(value, secret) + SEPARATOR + value;

	}

	/**
	 * 署名を検証して値を取り出す
	 *
	 * <p><b>署名が合わなければ null を返す。</b>元の値は返さない。</p>
	 *
	 * @param signed	署名つきの値
	 * @param secret	鍵
	 * @return	値（検証に失敗したら null）
	 */
	public static String unsign (String signed, String secret) {

		if (signed == null) {
			return null;
		}

		int index = signed.indexOf(SEPARATOR);
		if (index < 0) {
			return null;
		}

		String signature = signed.substring(0, index);
		String value = signed.substring(index + 1);

		// タイミング攻撃を避けるため定数時間で比べる
		if (!MessageDigest.isEqual(
			signature.getBytes(StandardCharsets.UTF_8)
			, mac(value, secret).getBytes(StandardCharsets.UTF_8))) {
			return null;
		}

		return value;

	}

	/**
	 * HMAC を計算する
	 *
	 * @param value		値
	 * @param secret	鍵
	 * @return	Base64（URL 安全・パディングなし）
	 */
	private static String mac (String value, String secret) {

		try {

			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));

			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));

		} catch (Exception ex) {

			// 鍵が空などの設定ミス。黙って通すと署名なしと同じになるので落とす
			throw new IllegalStateException("署名を計算できませんでした", ex);

		}

	}

}
