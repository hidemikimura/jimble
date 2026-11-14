package io.jimble.util.hash;


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

	/* XXH3（64 bit / 128 bit。seed は既定の 0） */
	private static final com.dynatrace.hash4j.hashing.Hasher64 XXH3_64 = com.dynatrace.hash4j.hashing.Hashing.xxh3_64();

	private static final com.dynatrace.hash4j.hashing.Hasher128 XXH3_128 = com.dynatrace.hash4j.hashing.Hashing.xxh3_128();

	private static final java.util.HexFormat HEX = java.util.HexFormat.of();

	/**
	 * XXH3（64 bit）を作る（3.0.0）
	 *
	 * <p>
	 * 文字列を UTF-8 にしてから XXH3_64bits（seed 0）を取る。{@link #sipHash(String)} と同じく、null と空文字は 0。
	 * 800 byte で SipHash の約 6 倍速い。jimble は 3.0.0 から、ロックのキー・SQL 結果キャッシュのタグ・流量制限の行に使う。
	 * <b>暗号のハッシュではない</b>（わざと衝突させる相手には強くない）。秘密や改ざんの検出には使わない。
	 * </p>
	 *
	 * @param key	キー
	 * @return	ハッシュ（null と空文字は 0）
	 */
	public static long xxh3 (String key) {

		if (key == null || key.isEmpty()) {
			return 0;
		}

		return XXH3_64.hashBytesToLong(key.getBytes(StandardCharsets.UTF_8));

	}

	/**
	 * XXH3（128 bit）を 16 進 32 文字で作る（3.0.0）
	 *
	 * <p>
	 * XXH3_128bits（seed 0）の上位 64 bit・下位 64 bit の順に 16 進でつなぐ。jimble は SQL 結果キャッシュのキーに使う。
	 * <b>暗号のハッシュではない。</b>
	 * </p>
	 *
	 * @param bytes	もとの byte 列（null は空と同じ）
	 * @return	16 進 32 文字
	 */
	public static String xxh3Hex128 (byte[] bytes) {

		com.dynatrace.hash4j.hashing.HashValue128 hash = XXH3_128.hashBytesTo128Bits(bytes == null ? new byte[0] : bytes);

		return HEX.toHexDigits(hash.getMostSignificantBits()) + HEX.toHexDigits(hash.getLeastSignificantBits());

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

			return SipHash.hash(bytes);

		} catch (Exception ex) {

			return 0;

		}

	}

	/**
	 * SipHashを作成（バイト列から。2.7.5）
	 *
	 * <p>
	 * {@link #sipHash(String)} と同じ値になる（オーバーロードにしないのは、{@code sipHash(null)} があいまいになるため）（文字列は UTF-8 のバイト列にしてから計算している）。
	 * バイト列をほかにも使うとき、UTF-8 にするのを 1 回で済ませるためにある。空なら 0。
	 * </p>
	 *
	 * @param bytes	バイト列
	 * @return	SipHash
	 * @since 2.7.5
	 */
	public static long sipHashOf (byte[] bytes) {

		if (bytes == null || bytes.length == 0) {
			return 0;
		}

		return SipHash.hash(bytes);

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
