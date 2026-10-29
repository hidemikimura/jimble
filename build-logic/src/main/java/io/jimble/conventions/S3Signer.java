package io.jimble.conventions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * S3 の API の署名（AWS Signature Version 4）
 *
 * <p>
 * Cloudflare R2 は S3 と同じ API・同じ署名で話す（リージョンは {@code auto}）。
 * <b>AWS の SDK は入れない。</b>使うのは PUT / GET / HEAD の3つだけで、署名も
 * 「正規化した要求を HMAC で2段に包む」だけなので、SDK を足すより読める（原則1・D-60 と同じ考え）。
 * 正しいかは、AWS が公開している例と突き合わせて確かめている（{@code S3SignerTest}）。
 * </p>
 *
 * <p>
 * クエリ文字列は使わない（このクラスは空のクエリしか署名しない）。
 * </p>
 */
final class S3Signer {

	/** 署名の方式 */
	static final String ALGORITHM = "AWS4-HMAC-SHA256";

	/* 鍵の ID */
	private final String accessKeyId;

	/* 秘密鍵 */
	private final String secretAccessKey;

	/* リージョン（R2 は auto） */
	private final String region;

	/* サービス（S3 / R2 は s3） */
	private final String service;

	/**
	 * @param accessKeyId		鍵の ID
	 * @param secretAccessKey	秘密鍵
	 * @param region			リージョン
	 * @param service			サービス
	 */
	S3Signer (String accessKeyId, String secretAccessKey, String region, String service) {

		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.region = region;
		this.service = service;

	}

	/**
	 * Authorization 見出しの値を作る
	 *
	 * @param method		HTTP のメソッド
	 * @param path			符号化したパス（{@link #encodePath(String)}）
	 * @param headers		署名に入れる見出し（名前は小文字。{@code host} と {@code x-amz-date} を含む）
	 * @param payloadHash	本文の SHA-256（16進）
	 * @param amzDate		{@code x-amz-date} の値（{@code yyyyMMdd'T'HHmmss'Z'}）
	 * @return Authorization 見出しの値
	 */
	String authorization (String method, String path, Map<String, String> headers, String payloadHash, String amzDate) {

		Map<String, String> sorted = new TreeMap<>(headers);

		StringBuilder canonicalHeaders = new StringBuilder();
		StringJoiner signedHeaders = new StringJoiner(";");

		for (Map.Entry<String, String> header : sorted.entrySet()) {
			canonicalHeaders.append(header.getKey()).append(':').append(header.getValue().trim()).append('\n');
			signedHeaders.add(header.getKey());
		}

		String canonicalRequest = method + "\n"
			+ path + "\n"
			+ "\n"
			+ canonicalHeaders + "\n"
			+ signedHeaders + "\n"
			+ payloadHash;

		String date = amzDate.substring(0, 8);
		String scope = date + "/" + region + "/" + service + "/aws4_request";

		String stringToSign = ALGORITHM + "\n"
			+ amzDate + "\n"
			+ scope + "\n"
			+ sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

		byte[] key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
		key = hmac(key, region);
		key = hmac(key, service);
		key = hmac(key, "aws4_request");

		String signature = HexFormat.of().formatHex(hmac(key, stringToSign));

		return ALGORITHM + " Credential=" + accessKeyId + "/" + scope
			+ ", SignedHeaders=" + signedHeaders
			+ ", Signature=" + signature;

	}

	/**
	 * パスを区切りごとに RFC 3986 で符号化する（{@code /} は残す）
	 *
	 * @param path	パス
	 * @return 符号化したパス
	 */
	static String encodePath (String path) {

		StringBuilder out = new StringBuilder();

		for (byte b : path.getBytes(StandardCharsets.UTF_8)) {

			char c = (char) (b & 0xff);

			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
				|| c == '-' || c == '.' || c == '_' || c == '~' || c == '/') {
				out.append(c);
			} else {
				out.append('%').append(HexFormat.of().withUpperCase().toHexDigits(b));
			}

		}

		return out.toString();

	}

	/**
	 * SHA-256 を16進で返す
	 *
	 * @param data	中身
	 * @return 16進
	 */
	static String sha256Hex (byte[] data) {

		return digestHex("SHA-256", data);

	}

	/**
	 * ハッシュを16進で返す
	 *
	 * @param algorithm	MD5 / SHA-1 / SHA-256 / SHA-512
	 * @param data		中身
	 * @return 16進
	 */
	static String digestHex (String algorithm, byte[] data) {

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException(algorithm + " が使えません", ex);
		}

	}

	private static byte[] hmac (byte[] key, String data) {

		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 が使えません", ex);
		}

	}

}
