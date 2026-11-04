package io.jimble.web.captcha;

import io.jimble.util.json.Dson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ALTCHA の問題を作り、答えを確かめる（Web に依らない部分）
 *
 * <p>
 * ウィジェット（altcha 3.x）の形式に合わせる。参照実装は altcha-lib 2.x の {@code createChallenge} /
 * {@code verifySolution} で、そのうち<b>答えの数を先に決める形（deterministic）</b>だけを使う。
 * </p>
 *
 * <ol>
 *   <li>サーバーは答えの数 n を決め、{@code PBKDF2(nonce + n（4 バイト・ビッグエンディアン）, salt)} の鍵を作る。
 *       鍵の前半を {@code keyPrefix} として渡し、鍵そのものには署名（{@code keySignature}）だけ付けて渡す</li>
 *   <li>ブラウザは n を 0 から順に試し、前半が {@code keyPrefix} と合う鍵を探す</li>
 *   <li>サーバーは、送られた鍵の署名が {@code keySignature} と合うかを見る（計算し直さない）</li>
 * </ol>
 *
 * <p>
 * 問題の項目（{@code parameters}）には、キーを並べた JSON に HMAC で署名する（altcha-lib と同じ形）。
 * <b>確かめるときは、送られた JSON をそのまま使わず、決まった項目だけから組み立て直す。</b>
 * 知らない項目や型の違うものがあれば、そこで断る。
 * </p>
 */
final class AltchaPow {

	/** 鍵を作る方式（ウィジェットはこの名前で計算のしかたを選ぶ） */
	static final String ALGORITHM = "PBKDF2/SHA-256";

	/** 鍵の長さ（バイト） */
	static final int KEY_LENGTH = 32;

	/** ブラウザに渡す鍵の前半の長さ（バイト。altcha-lib の既定 = 鍵の半分） */
	static final int KEY_PREFIX_LENGTH = KEY_LENGTH / 2;

	/** nonce と salt の長さ（バイト） */
	private static final int RANDOM_LENGTH = 16;

	/** 送られてくる答えの長さの上限（文字。これより長いものは読まずに断る） */
	static final int MAX_PAYLOAD_LENGTH = 4096;

	/** 答えの数の上限（ウィジェットは 4 バイトの符号なしで送る） */
	private static final long MAX_COUNTER = 0xFFFFFFFFL;

	/* HMAC */
	private static final String HMAC = "HmacSHA256";

	/* 問題の項目（これより多くても少なくても断る） */
	private static final Set<String> PARAMETER_KEYS = Set.of(
		"algorithm", "cost", "expiresAt", "keyLength", "keyPrefix", "keySignature", "nonce", "salt");

	/* 16 進（小文字だけ。ウィジェットも altcha-lib も小文字で書く） */
	private static final Pattern HEX = Pattern.compile("(?:[0-9a-f]{2}){1,128}");

	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private AltchaPow () {}

	/**
	 * 署名に使う鍵（設定の鍵から 2 つ作る）
	 *
	 * @param signature		問題の項目に署名する鍵
	 * @param keySignature	答えの鍵に署名する鍵
	 */
	record Keys (byte[] signature, byte[] keySignature) {

		/**
		 * 設定の鍵から作る
		 *
		 * <p>
		 * 2 つの署名に同じ鍵を使わない（altcha-lib も別の鍵を求める）。
		 * ブラウザはどちらの鍵も知らないので、形はこちらで決めてよい。
		 * </p>
		 *
		 * @param secret	設定の鍵
		 * @return	鍵
		 */
		static Keys derive (String secret) {

			byte[] master = secret.getBytes(StandardCharsets.UTF_8);

			return new Keys(
				hmac(master, "jimble-altcha-signature".getBytes(StandardCharsets.UTF_8))
				, hmac(master, "jimble-altcha-key-signature".getBytes(StandardCharsets.UTF_8)));

		}

	}

	/**
	 * 問題
	 *
	 * @param parameters	項目（キーの順に並べてある）
	 * @param signature		項目の署名（16 進）
	 */
	record Challenge (SortedMap<String, Object> parameters, String signature) {

		/**
		 * ウィジェットに渡す JSON
		 *
		 * @return	JSON
		 */
		String toJson () {

			return "{\"parameters\":" + canonicalJson(parameters) + ",\"signature\":\"" + signature + "\"}";

		}

	}

	/**
	 * 確かめた結果
	 *
	 * @param result	結果
	 * @param signature	問題の署名（使い回しを見分ける印。形が正しくなければ null）
	 * @param expiresAt	問題の期限（秒。形が正しくなければ 0）
	 */
	record Checked (AltchaResult result, String signature, long expiresAt) {

		static Checked of (AltchaResult result) {

			return new Checked(result, null, 0);

		}

	}

	/**
	 * 問題を作る
	 *
	 * @param keys		署名の鍵
	 * @param cost		1 回の計算の重さ
	 * @param counter	答えの数
	 * @param expiresAt	期限（秒）
	 * @param random	乱数
	 * @return	問題
	 */
	static Challenge create (Keys keys, int cost, long counter, long expiresAt, SecureRandom random) {

		byte[] nonce = new byte[RANDOM_LENGTH];
		byte[] salt = new byte[RANDOM_LENGTH];
		random.nextBytes(nonce);
		random.nextBytes(salt);

		byte[] derivedKey = pbkdf2(password(nonce, counter), salt, cost, KEY_LENGTH);

		SortedMap<String, Object> parameters = new TreeMap<>();
		parameters.put("algorithm", ALGORITHM);
		parameters.put("cost", (long) cost);
		parameters.put("expiresAt", expiresAt);
		parameters.put("keyLength", (long) KEY_LENGTH);
		parameters.put("keyPrefix", HEX_FORMAT.formatHex(derivedKey, 0, KEY_PREFIX_LENGTH));
		parameters.put("keySignature", HEX_FORMAT.formatHex(hmac(keys.keySignature(), derivedKey)));
		parameters.put("nonce", HEX_FORMAT.formatHex(nonce));
		parameters.put("salt", HEX_FORMAT.formatHex(salt));

		return new Challenge(parameters, sign(keys, parameters));

	}

	/**
	 * 答えを確かめる（使い回しは見ない。それは呼ぶ側が印で見る）
	 *
	 * @param payload		ウィジェットが送ってきた値（base64 の JSON）
	 * @param keys			署名の鍵
	 * @param nowSeconds	いまの時刻（秒）
	 * @return	結果
	 */
	static Checked verify (String payload, Keys keys, long nowSeconds) {

		if (payload == null || payload.isBlank()) {
			return Checked.of(AltchaResult.MISSING);
		}

		if (payload.length() > MAX_PAYLOAD_LENGTH) {
			return Checked.of(AltchaResult.MALFORMED);
		}

		Map<?, ?> json;

		try {
			json = Dson.decodes(new String(Base64.getDecoder().decode(payload.trim()), StandardCharsets.UTF_8), Map.class);
		} catch (RuntimeException ex) {
			return Checked.of(AltchaResult.MALFORMED);
		}

		if (json == null
			|| !(json.get("challenge") instanceof Map<?, ?> challenge)
			|| !(json.get("solution") instanceof Map<?, ?> solution)
			|| !(challenge.get("parameters") instanceof Map<?, ?> received)
			|| !(challenge.get("signature") instanceof String signature)) {
			return Checked.of(AltchaResult.MALFORMED);
		}

		SortedMap<String, Object> parameters = parameters(received);

		if (parameters == null || !HEX.matcher(signature).matches()) {
			return Checked.of(AltchaResult.MALFORMED);
		}

		long expiresAt = (Long) parameters.get("expiresAt");

		// 期限は署名を確かめてから見る（書き換えた期限で「期限切れ」と答えない）
		if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII), sign(keys, parameters).getBytes(StandardCharsets.US_ASCII))) {
			return Checked.of(AltchaResult.INVALID);
		}

		if (expiresAt < nowSeconds) {
			return new Checked(AltchaResult.EXPIRED, signature, expiresAt);
		}

		Long counter = integer(solution.get("counter"));

		if (counter == null || counter < 0 || counter > MAX_COUNTER
			|| !(solution.get("derivedKey") instanceof String derivedKey) || !HEX.matcher(derivedKey).matches()) {
			return new Checked(AltchaResult.MALFORMED, signature, expiresAt);
		}

		String expected = HEX_FORMAT.formatHex(hmac(keys.keySignature(), HEX_FORMAT.parseHex(derivedKey)));
		boolean solved = MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.US_ASCII)
			, ((String) parameters.get("keySignature")).getBytes(StandardCharsets.US_ASCII));

		return new Checked(solved ? AltchaResult.VERIFIED : AltchaResult.INVALID, signature, expiresAt);

	}

	/**
	 * 送られた問題の項目を、決まった項目と型だけで組み立て直す
	 *
	 * @param received	送られた項目
	 * @return	項目（知らない項目がある・型が違う場合は null）
	 */
	private static SortedMap<String, Object> parameters (Map<?, ?> received) {

		Set<String> keys = received.keySet().stream().map(String::valueOf).collect(Collectors.toSet());

		if (!keys.equals(PARAMETER_KEYS) || !ALGORITHM.equals(received.get("algorithm"))) {
			return null;
		}

		SortedMap<String, Object> parameters = new TreeMap<>();
		parameters.put("algorithm", ALGORITHM);

		for (String key : Set.of("cost", "expiresAt", "keyLength")) {
			Long value = integer(received.get(key));
			if (value == null || value < 1) {
				return null;
			}
			parameters.put(key, value);
		}

		for (String key : Set.of("keyPrefix", "keySignature", "nonce", "salt")) {
			if (!(received.get(key) instanceof String value) || !HEX.matcher(value).matches()) {
				return null;
			}
			parameters.put(key, value);
		}

		return parameters;

	}

	/**
	 * 整数として読む（JSON の数は Long か Double で来る）
	 *
	 * @param value	値
	 * @return	整数（整数でなければ null）
	 */
	private static Long integer (Object value) {

		if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
			return ((Number) value).longValue();
		}

		if (value instanceof Number number) {
			double d = number.doubleValue();
			if (d == Math.rint(d) && Math.abs(d) < 0x1p53) {
				return (long) d;
			}
		}

		return null;

	}

	/* 項目に署名する（キーを並べた JSON の HMAC） */
	private static String sign (Keys keys, SortedMap<String, Object> parameters) {

		return HEX_FORMAT.formatHex(hmac(keys.signature(), canonicalJson(parameters).getBytes(StandardCharsets.UTF_8)));

	}

	/**
	 * キーを並べた JSON（JavaScript の {@code JSON.stringify(sortKeys(...))} と同じ文字列）
	 *
	 * <p>値は、16 進と方式の名前（{@code [A-Za-z0-9/-]}）と整数だけなので、エスケープは要らない。</p>
	 *
	 * @param parameters	項目
	 * @return	JSON
	 */
	static String canonicalJson (SortedMap<String, Object> parameters) {

		return parameters.entrySet().stream()
			.map(entry -> "\"" + entry.getKey() + "\":"
				+ (entry.getValue() instanceof String text ? "\"" + text + "\"" : String.valueOf(entry.getValue())))
			.collect(Collectors.joining(",", "{", "}"));

	}

	/**
	 * パスワード（nonce のうしろに、答えの数を 4 バイトのビッグエンディアンで付ける）
	 *
	 * @param nonce		nonce
	 * @param counter	答えの数
	 * @return	パスワード
	 */
	static byte[] password (byte[] nonce, long counter) {

		return ByteBuffer.allocate(nonce.length + Integer.BYTES).put(nonce).putInt((int) counter).array();

	}

	/**
	 * PBKDF2-HMAC-SHA256（RFC 8018）
	 *
	 * <p>
	 * JDK の {@code PBKDF2WithHmacSHA256} は、パスワードを文字（{@code char[]}）で受け取るので使えない。
	 * ここのパスワードは乱数のバイト列で、文字にすると別のバイト列になる。
	 * </p>
	 *
	 * @param password		パスワード
	 * @param salt			salt
	 * @param iterations	繰り返し回数
	 * @param length		鍵の長さ（バイト）
	 * @return	鍵
	 */
	static byte[] pbkdf2 (byte[] password, byte[] salt, int iterations, int length) {

		try {

			Mac mac = Mac.getInstance(HMAC);
			mac.init(new SecretKeySpec(password, HMAC));

			int blockLength = mac.getMacLength();
			byte[] result = new byte[length];

			for (int block = 1, offset = 0; offset < length; block++, offset += blockLength) {

				mac.update(salt);
				byte[] u = mac.doFinal(ByteBuffer.allocate(Integer.BYTES).putInt(block).array());
				byte[] t = u.clone();

				for (int i = 1; i < iterations; i++) {
					u = mac.doFinal(u);
					for (int j = 0; j < t.length; j++) {
						t[j] ^= u[j];
					}
				}

				System.arraycopy(t, 0, result, offset, Math.min(blockLength, length - offset));

			}

			return result;

		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("PBKDF2 を計算できません", ex);
		}

	}

	/* HMAC-SHA256 */
	private static byte[] hmac (byte[] key, byte[] data) {

		try {
			Mac mac = Mac.getInstance(HMAC);
			mac.init(new SecretKeySpec(key, HMAC));
			return mac.doFinal(data);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC を計算できません", ex);
		}

	}

}
