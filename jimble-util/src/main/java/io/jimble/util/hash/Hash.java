package io.jimble.util.hash;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 汎用ハッシュ生成クラス
 */
public class Hash {

	/**
	 * XXHash32
	 *
	 * @param key   キー
	 * @return  XXHash32
	 */
	public static int xxHash32 (String key) {

		return XXHash.hash32(key.getBytes(StandardCharsets.UTF_8), 0);

	}

	/**
	 * XXHash64
	 *
	 * @param key   キー
	 * @return  XXHash64
	 */
	public static long xxHash64 (String key) {

		return XXHash.hash64(key.getBytes(StandardCharsets.UTF_8), 0);

	}

	/**
	 * SipHashを作成.
	 *
	 * @param key	キー
	 * @return	SipHash
	 */
	public static long sipHash (String key) {

		if (key == null || key.isEmpty()) {
			return 0;
		}

		try {

			byte[] bytes = key.getBytes(StandardCharsets.UTF_8);

			return Hashing.sipHash24().hashBytes(bytes).asLong();

		} catch (Exception ex) {

			return 0;

		}

	}

	/**
	 * SHA256を作成.
	 *
	 * @param key	キー
	 * @return	SHA256
	 */
	public static String sha256 (String key) {

		return hash(key, "SHA-256");

	}

	/**
	 * MD5を作成.
	 *
	 * @param key	キー
	 * @return	MD5
	 */
	public static String md5 (String key) {

		return hash(key, "MD5");

	}

	/**
	 * ハッシュ化
	 *
	 * @param key           キー
	 * @param digestType    アルゴリズム
	 * @return  ハッシュ値
	 */
	private static String hash (String key, String digestType) {

		try {

			// バイト文字列のMD5ハッシュを作る。
			MessageDigest digest = MessageDigest.getInstance(digestType);
			byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));

			// バイト文字列を一文字ずつ変換
			StringBuilder res = new StringBuilder();
			for (byte h : hash) {
				int b = h & 0xFF;
				if (b <= 0xF) {
					res.append("0");
				}
				res.append(Integer.toHexString(b));
			}

			return res.toString();

		} catch (NoSuchAlgorithmException e) {

			return "";

		}

	}

}
