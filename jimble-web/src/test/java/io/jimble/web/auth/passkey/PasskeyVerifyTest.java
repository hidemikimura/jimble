package io.jimble.web.auth.passkey;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスキーの応答の確かめ（D-261。DB もセッションも使わない）
 */
class PasskeyVerifyTest {

	private static final String RP_ID = "example.com";

	private static final String ORIGIN = "https://example.com";

	private static final List<String> ORIGINS = List.of(ORIGIN);

	private static final String CHALLENGE = "Y2hhbGxlbmdlLWNoYWxsZW5nZS1jaGFsbGVuZ2UtMQ";

	/* UP + UV */
	private static final int OK = 0x01 | 0x04;

	// region 登録

	@ParameterizedTest(name = "alg {0}")
	@ValueSource(ints = { CoseKey.ES256, CoseKey.EDDSA, CoseKey.RS256 })
	@DisplayName("登録：3つのアルゴリズムの応答を受け付け、公開鍵と ID を取り出す")
	void registers (int algorithm) throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(algorithm, 0);

		Passkey.Registration registration = Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, authenticator.create(CHALLENGE, ORIGIN, RP_ID, OK));

		assertArrayEquals(authenticator.credentialId, registration.credentialId());
		assertArrayEquals(authenticator.keys.getPublic().getEncoded(), registration.publicKey());
		assertEquals(algorithm, registration.algorithm());

	}

	@Test
	@DisplayName("登録：type・チャレンジ・オリジン・rp_id が違えば断る")
	void registrationRejectsMismatch () throws Exception {

		FakeAuthenticator a = new FakeAuthenticator(CoseKey.ES256, 0);

		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create(CHALLENGE, ORIGIN, RP_ID, OK, "webauthn.get", a.coseKey())), "type");
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create("b3RoZXItY2hhbGxlbmdl", ORIGIN, RP_ID, OK)), "チャレンジ");
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create(CHALLENGE, "https://evil.example", RP_ID, OK)), "オリジン");
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create(CHALLENGE, ORIGIN, "evil.example", OK)), "rpIdHash");

	}

	@Test
	@DisplayName("登録：UP・UV が無い、BS なのに BE でない、rawId が違う、なら断る")
	void registrationRejectsFlags () throws Exception {

		FakeAuthenticator a = new FakeAuthenticator(CoseKey.ES256, 0);

		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE, a.create(CHALLENGE, ORIGIN, RP_ID, 0x04)), "UP");
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE, a.create(CHALLENGE, ORIGIN, RP_ID, 0x01)), "UV");
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE, a.create(CHALLENGE, ORIGIN, RP_ID, OK | 0x10)), "BS");

		Data credential = a.create(CHALLENGE, ORIGIN, RP_ID, OK);
		credential.putData("rawId", FakeAuthenticator.B64.encodeToString(new byte[] { 1, 2, 3 }));
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE, credential), "rawId");

		// 同期するパスキー（BE + BS）は受け付ける
		Passkey.Registration synced = Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE, a.create(CHALLENGE, ORIGIN, RP_ID, OK | 0x08 | 0x10));
		assertTrue(synced.backupEligible() && synced.backedUp());

	}

	@Test
	@DisplayName("登録：受け付けないアルゴリズム（ES384）と、曲線の上に無い点は断る")
	void registrationRejectsKeys () throws Exception {

		FakeAuthenticator a = new FakeAuthenticator(CoseKey.ES256, 0);

		Map<Object, Object> es384 = a.coseKey();
		es384.put(3L, -35L);
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create(CHALLENGE, ORIGIN, RP_ID, OK, "webauthn.create", es384)), "受け付けない鍵");

		Map<Object, Object> offCurve = a.coseKey();
		byte[] y = ((byte[]) offCurve.get(-3L)).clone();
		y[31] ^= 1;
		offCurve.put(-3L, y);
		rejects(() -> Passkey.verifyRegistration(RP_ID, ORIGINS, CHALLENGE
			, a.create(CHALLENGE, ORIGIN, RP_ID, OK, "webauthn.create", offCurve)), "曲線");

	}

	// endregion

	// region ログイン

	@ParameterizedTest(name = "alg {0}")
	@ValueSource(ints = { CoseKey.ES256, CoseKey.EDDSA, CoseKey.RS256 })
	@DisplayName("ログイン：保存した公開鍵で署名を確かめる")
	void asserts (int algorithm) throws Exception {

		FakeAuthenticator a = registered(algorithm, 0);

		Passkey.Assertion assertion = Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get(CHALLENGE, ORIGIN, RP_ID, OK)
			, key(a), algorithm, a.userHandle, 0);

		assertEquals(0, assertion.signCount());

	}

	@Test
	@DisplayName("ログイン：別の鍵の署名・書き換えた authenticatorData は断る")
	void assertionRejectsSignature () throws Exception {

		FakeAuthenticator a = registered(CoseKey.ES256, 0);

		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));

		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE
			, a.get(CHALLENGE, ORIGIN, RP_ID, OK, generator.generateKeyPair().getPrivate()), key(a), CoseKey.ES256, a.userHandle, 0), "署名");

		Data tampered = a.get(CHALLENGE, ORIGIN, RP_ID, OK);
		byte[] authData = java.util.Base64.getUrlDecoder().decode(tampered.getDataOptional("response").getString("authenticatorData"));
		authData[32] |= 0x10 | 0x08;                                  // 印を書き換える（署名の対象）
		Data response = tampered.getDataOptional("response");
		response.putData("authenticatorData", FakeAuthenticator.B64.encodeToString(authData));
		tampered.putData("response", response);
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, tampered, key(a), CoseKey.ES256, a.userHandle, 0), "署名");

	}

	@Test
	@DisplayName("ログイン：userHandle が無い・違う、チャレンジ・オリジンが違う、UV が無い、なら断る")
	void assertionRejectsMismatch () throws Exception {

		FakeAuthenticator a = registered(CoseKey.ES256, 0);
		PublicKey key = key(a);

		String handle = a.userHandle;
		a.userHandle = null;
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get(CHALLENGE, ORIGIN, RP_ID, OK), key, CoseKey.ES256, handle, 0), "userHandle");

		a.userHandle = FakeAuthenticator.B64.encodeToString(new byte[32]);
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get(CHALLENGE, ORIGIN, RP_ID, OK), key, CoseKey.ES256, handle, 0), "userHandle");
		a.userHandle = handle;

		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get("b3RoZXI", ORIGIN, RP_ID, OK), key, CoseKey.ES256, handle, 0), "チャレンジ");
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get(CHALLENGE, "https://evil.example", RP_ID, OK), key, CoseKey.ES256, handle, 0), "オリジン");
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, a.get(CHALLENGE, ORIGIN, RP_ID, 0x01), key, CoseKey.ES256, handle, 0), "UV");

	}

	@Test
	@DisplayName("ログイン：署名の回数が戻っていたら断る（複製の疑い）。どちらも 0 なら見ない")
	void signCount () throws Exception {

		FakeAuthenticator counting = registered(CoseKey.ES256, 5);

		// 6 になる → 保存した 5 より大きいので通る
		assertEquals(6, Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, counting.get(CHALLENGE, ORIGIN, RP_ID, OK)
			, key(counting), CoseKey.ES256, counting.userHandle, 5).signCount());

		// 7 になるが、保存したのが 7 なら戻ったのと同じ
		rejects(() -> Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, counting.get(CHALLENGE, ORIGIN, RP_ID, OK)
			, key(counting), CoseKey.ES256, counting.userHandle, 7), "署名の回数");

		FakeAuthenticator synced = registered(CoseKey.ES256, 0);
		assertEquals(0, Passkey.verifyAssertion(RP_ID, ORIGINS, CHALLENGE, synced.get(CHALLENGE, ORIGIN, RP_ID, OK)
			, key(synced), CoseKey.ES256, synced.userHandle, 0).signCount());

	}

	@Test
	@DisplayName("crossOrigin が true の clientDataJSON は断る")
	void crossOrigin () {

		byte[] json = "{\"type\":\"webauthn.get\",\"challenge\":\"%s\",\"origin\":\"%s\",\"crossOrigin\":true}"
			.formatted(CHALLENGE, ORIGIN).getBytes(java.nio.charset.StandardCharsets.UTF_8);

		rejects(() -> {
			Passkey.checkClientData(json, "webauthn.get", CHALLENGE, ORIGINS);
			return null;
		}, "crossOrigin");

		assertFalse(new String(json).isEmpty());

	}

	// endregion

	// region 小物

	private static FakeAuthenticator registered (int algorithm, long signCount) throws Exception {

		FakeAuthenticator a = new FakeAuthenticator(algorithm, signCount);
		a.userHandle = FakeAuthenticator.B64.encodeToString("user-handle-0123456789abcdef0123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return a;

	}

	private static PublicKey key (FakeAuthenticator a) {

		return CoseKey.restore(a.keys.getPublic().getEncoded(), a.algorithm);

	}

	interface Call {
		Object call () throws Exception;
	}

	private static void rejects (Call call, String reason) {

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, call::call, "断るはず: " + reason);
		assertTrue(ex.getMessage().contains(reason), "理由が違う: " + ex.getMessage() + "（期待: " + reason + "）");

	}

	// endregion

}
