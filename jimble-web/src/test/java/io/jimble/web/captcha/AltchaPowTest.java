package io.jimble.web.captcha;

import io.jimble.util.json.Dson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTCHA の問題を作り、答えを確かめる（Web に依らない部分）
 */
class AltchaPowTest {

	private static final HexFormat HEX = HexFormat.of();

	private static final AltchaPow.Keys KEYS = AltchaPow.Keys.derive("0123456789abcdef0123456789abcdef");

	/** 期限（秒）。テストの「いま」はこれより前 */
	private static final long EXPIRES_AT = 2_000_000_000L;

	private static final long NOW = EXPIRES_AT - 60;

	/* 解くのが速いよう、軽くする */
	private static final int COST = 10;

	private static final long COUNTER = 37;

	// region PBKDF2（期待値は Python の hashlib.pbkdf2_hmac で出した）

	@Test
	@DisplayName("PBKDF2-HMAC-SHA256 が RFC 7914 の例と合う")
	void pbkdf2Rfc7914 () {

		assertEquals(
			"55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
			HEX.formatHex(AltchaPow.pbkdf2(bytes("passwd"), bytes("salt"), 1, 64)));

		assertEquals(
			"4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
			HEX.formatHex(AltchaPow.pbkdf2(bytes("Password"), bytes("NaCl"), 80000, 64)));

	}

	@Test
	@DisplayName("ウィジェットと同じパスワード（nonce + 4 バイトの答え）で、同じ鍵になる")
	void pbkdf2WidgetPassword () {

		byte[] password = AltchaPow.password(HEX.parseHex("00112233445566778899aabbccddeeff"), 1234);

		assertEquals("4c316573545bb4eaa02dcb76355f9ec5a465fd3d046dcb5d21692bc405c7dc05",
			HEX.formatHex(AltchaPow.pbkdf2(password, HEX.parseHex("ffeeddccbbaa99887766554433221100"), 5000, 32)));

	}

	// endregion

	// region 問題

	@Test
	@DisplayName("問題は altcha-lib と同じ項目で、キーの順に並ぶ")
	void challengeShape () {

		AltchaPow.Challenge challenge = create();
		Map<?, ?> json = Dson.decodes(challenge.toJson(), Map.class);
		Map<?, ?> parameters = (Map<?, ?>) json.get("parameters");

		assertEquals("[algorithm, cost, expiresAt, keyLength, keyPrefix, keySignature, nonce, salt]", challenge.parameters().keySet().toString());
		assertEquals("PBKDF2/SHA-256", parameters.get("algorithm"));
		assertEquals(32, ((Number) parameters.get("keyLength")).intValue());
		assertEquals(32, ((String) parameters.get("keyPrefix")).length(), "鍵の半分（16 バイト）");
		assertTrue(((String) json.get("signature")).matches("[0-9a-f]{64}"));
		assertTrue(challenge.toJson().startsWith("{\"parameters\":{\"algorithm\":\"PBKDF2/SHA-256\",\"cost\":10,\"expiresAt\":2000000000,"),
			challenge.toJson());

	}

	// endregion

	// region 答え

	@Test
	@DisplayName("ブラウザと同じように解いた答えは通る")
	void solved () {

		assertEquals(AltchaResult.VERIFIED, AltchaPow.verify(solve(create()), KEYS, NOW).result());

	}

	@Test
	@DisplayName("期限を過ぎていれば EXPIRED")
	void expired () {

		assertEquals(AltchaResult.EXPIRED, AltchaPow.verify(solve(create()), KEYS, EXPIRES_AT + 1).result());

	}

	@Test
	@DisplayName("別の鍵で作った問題は INVALID")
	void otherSecret () {

		AltchaPow.Keys other = AltchaPow.Keys.derive("fedcba9876543210fedcba9876543210");

		assertEquals(AltchaResult.INVALID, AltchaPow.verify(solve(create()), other, NOW).result());

	}

	@Test
	@DisplayName("項目を書き換えると INVALID（期限を延ばす・前半を差し替える）")
	void tampered () {

		assertEquals(AltchaResult.INVALID, verify(create(), payload -> parameters(payload).put("expiresAt", EXPIRES_AT + 3600)));
		assertEquals(AltchaResult.INVALID, verify(create(), payload -> parameters(payload).put("keyPrefix", "00".repeat(16))));

	}

	@Test
	@DisplayName("解いていない鍵（でたらめ・前半だけ合わせた鍵）は INVALID")
	void notSolved () {

		assertEquals(AltchaResult.INVALID, verify(create(), payload -> solution(payload).put("derivedKey", "ab".repeat(32))));
		assertEquals(AltchaResult.INVALID, verify(create(), payload ->
			solution(payload).put("derivedKey", parameters(payload).get("keyPrefix") + "00".repeat(16))));

	}

	@Test
	@DisplayName("知らない項目・違う方式・型の違う値は MALFORMED（組み立て直せないものは見ない）")
	void unknownParameters () {

		assertEquals(AltchaResult.MALFORMED, verify(create(), payload -> parameters(payload).put("data", "x")));
		assertEquals(AltchaResult.MALFORMED, verify(create(), payload -> parameters(payload).put("algorithm", "SHA-256")));
		assertEquals(AltchaResult.MALFORMED, verify(create(), payload -> parameters(payload).put("cost", "10")));
		assertEquals(AltchaResult.MALFORMED, verify(create(), payload -> parameters(payload).put("nonce", "ZZ")));
		assertEquals(AltchaResult.MALFORMED, verify(create(), payload -> solution(payload).put("counter", -1)));

	}

	@Test
	@DisplayName("空は MISSING、base64 でも JSON でもないもの・ウィジェットのテスト用の答え・長すぎるものは MALFORMED")
	void malformed () {

		assertEquals(AltchaResult.MISSING, AltchaPow.verify(null, KEYS, NOW).result());
		assertEquals(AltchaResult.MISSING, AltchaPow.verify(" ", KEYS, NOW).result());
		assertEquals(AltchaResult.MALFORMED, AltchaPow.verify("not base64!", KEYS, NOW).result());
		assertEquals(AltchaResult.MALFORMED, AltchaPow.verify(base64("not json"), KEYS, NOW).result());
		assertEquals(AltchaResult.MALFORMED, AltchaPow.verify(base64("{\"challenge\":null,\"solution\":null,\"test\":true}"), KEYS, NOW).result());
		assertEquals(AltchaResult.MALFORMED, AltchaPow.verify("A".repeat(AltchaPow.MAX_PAYLOAD_LENGTH + 1), KEYS, NOW).result());

	}

	// endregion

	// region ヘルパー

	private static AltchaPow.Challenge create () {

		return AltchaPow.create(KEYS, COST, COUNTER, EXPIRES_AT, new SecureRandom());

	}

	/**
	 * ブラウザ（ウィジェット）と同じに解いて、送る値を作る
	 *
	 * @param challenge	問題
	 * @return	送る値（base64 の JSON）
	 */
	static String solve (AltchaPow.Challenge challenge) {

		return encode(solution(challenge.toJson()));

	}

	/**
	 * 問題の JSON を解いて、送る JSON（challenge と solution）を作る
	 *
	 * @param challengeJson	問題の JSON
	 * @return	送る JSON
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> solution (String challengeJson) {

		Map<String, Object> challenge = new LinkedHashMap<>((Map<String, Object>) Dson.decodes(challengeJson, Map.class));
		Map<String, Object> parameters = new LinkedHashMap<>((Map<String, Object>) challenge.get("parameters"));
		challenge.put("parameters", parameters);

		byte[] nonce = HEX.parseHex((String) parameters.get("nonce"));
		byte[] salt = HEX.parseHex((String) parameters.get("salt"));
		byte[] prefix = HEX.parseHex((String) parameters.get("keyPrefix"));
		int cost = ((Number) parameters.get("cost")).intValue();
		int keyLength = ((Number) parameters.get("keyLength")).intValue();

		for (long counter = 0; ; counter++) {

			byte[] key = AltchaPow.pbkdf2(AltchaPow.password(nonce, counter), salt, cost, keyLength);

			if (Arrays.equals(Arrays.copyOf(key, prefix.length), prefix)) {
				Map<String, Object> solution = new LinkedHashMap<>();
				solution.put("counter", counter);
				solution.put("derivedKey", HEX.formatHex(key));
				solution.put("time", 1.5);
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("challenge", challenge);
				payload.put("solution", solution);
				return payload;
			}

		}

	}

	static String encode (Map<String, Object> payload) {

		return base64(Dson.encodes(payload));

	}

	private static AltchaResult verify (AltchaPow.Challenge challenge, Consumer<Map<String, Object>> tamper) {

		Map<String, Object> payload = solution(challenge.toJson());
		tamper.accept(payload);

		return AltchaPow.verify(encode(payload), KEYS, NOW).result();

	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parameters (Map<String, Object> payload) {

		return (Map<String, Object>) ((Map<String, Object>) payload.get("challenge")).get("parameters");

	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> solution (Map<String, Object> payload) {

		return (Map<String, Object>) payload.get("solution");

	}

	private static String base64 (String text) {

		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));

	}

	private static byte[] bytes (String text) {

		return text.getBytes(StandardCharsets.US_ASCII);

	}

	// endregion

}
