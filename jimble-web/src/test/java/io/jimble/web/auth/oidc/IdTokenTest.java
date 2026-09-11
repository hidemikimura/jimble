package io.jimble.web.auth.oidc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ID トークンの検証（要件 F-W-31）
 *
 * <h2>ここで見ているもの</h2>
 * <p>
 * <b>通ることより、落ちること。</b>JWT の検証で破られるところは決まっていて、
 * どれも<b>「見ていない」だけで通ってしまう</b>——
 * 通ってしまったことは、外からは何も起きていないように見える。
 * </p>
 *
 * <p>
 * 署名の計算そのものは JDK がやる。ここが持っているのは<b>方針</b>で、
 * 方針は<b>書き忘れても動く</b>ので、1つずつ落として確かめる。
 * </p>
 */
class IdTokenTest {

	/** プロバイダの名前 */
	private static final String PROVIDER = "test";

	/** issuer */
	private static final String ISSUER = "https://issuer.example.com";

	/** クライアント ID */
	private static final String CLIENT_ID = "client-1";

	/** こちらが送った nonce */
	private static final String NONCE = "nonce-1";

	/** base64url */
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	/* 元の設定 */
	private static Config originalConf;

	/* 署名の鍵 */
	private static KeyPair rsa;

	/* 別の鍵（すり替え用） */
	private static KeyPair otherRsa;

	/* EC の鍵 */
	private static KeyPair ec;

	@BeforeAll
	static void setUp () throws Exception {

		Conf.reload();
		originalConf = Conf.conf().config();

		/*
		 * 3つの口を設定に書いておく。<b>discovery を引きに行かない</b>ので、
		 * ここではネットワークへ出ない。
		 */
		Conf.replace(ConfigFactory.parseString("""
			auth.oidc.test {
				issuer                 = "%s"
				client_id              = "%s"
				client_secret          = "secret"
				redirect_uri           = "https://app.example.com/auth/test/callback"
				authorization_endpoint = "https://issuer.example.com/authorize"
				token_endpoint         = "https://issuer.example.com/token"
				jwks_uri               = "https://issuer.example.com/jwks"
			}
			""".formatted(ISSUER, CLIENT_ID)).withFallback(originalConf));

		KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
		rsaGen.initialize(2048);
		rsa = rsaGen.generateKeyPair();
		otherRsa = rsaGen.generateKeyPair();

		KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
		ecGen.initialize(new ECGenParameterSpec("secp256r1"));
		ec = ecGen.generateKeyPair();

	}

	@AfterAll
	static void tearDown () {

		OidcProvider.reset();
		Jwks.reset();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void putKeys () {

		OidcProvider.reset();
		Jwks.reset();

		Jwks.put(PROVIDER, "k1", new Jwks.Key(rsa.getPublic(), "RS256"));
		Jwks.put(PROVIDER, "ec1", new Jwks.Key(ec.getPublic(), "ES256"));

	}

	// region 通るもの

	@Test
	@DisplayName("F-W-31 正しい ID トークンは通る")
	void accepts () {

		Data claims = verify(sign("k1", "RS256", rsa, claims()));

		assertEquals("u-1", claims.getString("sub"));
		assertEquals("taro@example.com", claims.getString("email"));

	}

	@Test
	@DisplayName("F-W-31 ES256 も通る")
	void acceptsEc () {

		Data claims = verify(sign("ec1", "ES256", ec, claims()));

		assertEquals("u-1", claims.getString("sub"));

	}

	@Test
	@DisplayName("F-W-31 aud が複数でも、azp が自分なら通る")
	void acceptsMultipleAudienceWithAzp () {

		Data claims = claims();
		claims.put("aud", List.of(CLIENT_ID, "other-client"));
		claims.put("azp", CLIENT_ID);

		assertEquals("u-1", verify(sign("k1", "RS256", rsa, claims)).getString("sub"));

	}

	// endregion

	// region 署名

	@Test
	@DisplayName("F-W-31 alg=none は通さない")
	void rejectsNone () {

		/*
		 * <b>いちばん古典的な穴。</b>ヘッダの alg を信じて分岐すると、
		 * <b>署名が空でも「検証した」ことになる</b>。
		 */
		String header = base64("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"k1\"}");
		String payload = base64(claims().toString());

		assertRejected(header + "." + payload + ".", "alg=none");

	}

	@Test
	@DisplayName("F-W-31 alg=HS256 は通さない（公開鍵を共有鍵として使う手）")
	void rejectsAlgorithmConfusion () throws Exception {

		/*
		 * <b>公開鍵は公開されている。</b>それを HMAC の鍵として渡す実装だと、
		 * <b>誰でも正しい署名を作れる</b>。
		 * 攻撃者が本当に作れる形（公開鍵の中身を鍵にした HS256）で試す。
		 */
		String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"k1\"}");
		String payload = base64(claims().toString());

		byte[] secret = ((RSAPublicKey) rsa.getPublic()).getModulus().toByteArray();

		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret, "HmacSHA256"));

		String signature = ENCODER.encodeToString(
			mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));

		assertRejected(header + "." + payload + "." + signature, "alg 混同");

	}

	@Test
	@DisplayName("F-W-31 署名を1ビット変えたら通さない")
	void rejectsTamperedSignature () {

		String token = sign("k1", "RS256", rsa, claims());

		String[] parts = token.split("\\.");
		byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
		signature[10] ^= 0x01;

		assertRejected(parts[0] + "." + parts[1] + "." + ENCODER.encodeToString(signature), "改ざん");

	}

	@Test
	@DisplayName("F-W-31 別の鍵で署名したものは通さない")
	void rejectsOtherKey () {

		assertRejected(sign("k1", "RS256", otherRsa, claims()), "別の鍵");

	}

	@Test
	@DisplayName("F-W-31 知らない kid は通さない")
	void rejectsUnknownKid () {

		assertRejected(sign("no-such-kid", "RS256", rsa, claims()), "知らない kid");

	}

	@Test
	@DisplayName("F-W-31 JWKS が名乗った alg と違えば通さない")
	void rejectsAlgorithmMismatch () {

		/*
		 * JWKS 側が「この鍵は RS256」と言っているのに、ヘッダが ES256 だと言う。
		 * <b>鍵の取り違えを通さない</b>ため。
		 */
		Jwks.put(PROVIDER, "k2", new Jwks.Key(rsa.getPublic(), "RS512"));

		assertRejected(sign("k2", "RS256", rsa, claims()), "鍵の alg 不一致");

	}

	@Test
	@DisplayName("F-W-31 署名の無い（2つしかない）ものは通さない")
	void rejectsUnsecured () {

		String token = sign("k1", "RS256", rsa, claims());
		String[] parts = token.split("\\.");

		assertRejected(parts[0] + "." + parts[1], "署名なし");
		assertRejected(parts[0] + "." + parts[1] + ".", "署名が空");

	}

	// endregion

	// region 中身

	@Test
	@DisplayName("F-W-31 iss が違えば通さない（前方一致にしない）")
	void rejectsWrongIssuer () {

		Data claims = claims();
		claims.put("iss", ISSUER + ".evil.example.jp");

		assertRejected(sign("k1", "RS256", rsa, claims), "iss の前方一致");

	}

	@Test
	@DisplayName("F-W-31 aud が自分でなければ通さない")
	void rejectsWrongAudience () {

		Data claims = claims();
		claims.put("aud", "other-client");

		assertRejected(sign("k1", "RS256", rsa, claims), "別のクライアント宛て");

	}

	@Test
	@DisplayName("F-W-31 aud が複数で azp が自分でなければ通さない")
	void rejectsMultipleAudienceWithoutAzp () {

		Data claims = claims();
		claims.put("aud", List.of(CLIENT_ID, "other-client"));
		claims.put("azp", "other-client");

		assertRejected(sign("k1", "RS256", rsa, claims), "azp が別");

	}

	@Test
	@DisplayName("F-W-31 期限が切れていれば通さない")
	void rejectsExpired () {

		Data claims = claims();
		claims.put("exp", Instant.now().getEpochSecond() - 3600);

		assertRejected(sign("k1", "RS256", rsa, claims), "期限切れ");

	}

	@Test
	@DisplayName("F-W-31 発行時刻が未来なら通さない")
	void rejectsFutureIssuedAt () {

		Data claims = claims();
		claims.put("iat", Instant.now().getEpochSecond() + 3600);

		assertRejected(sign("k1", "RS256", rsa, claims), "未来の iat");

	}

	@Test
	@DisplayName("F-W-31 sub が無ければ通さない")
	void rejectsMissingSubject () {

		Data claims = claims();
		claims.remove("sub");

		assertRejected(sign("k1", "RS256", rsa, claims), "sub なし");

	}

	@Test
	@DisplayName("F-W-31 nonce が合わなければ通さない")
	void rejectsWrongNonce () {

		Data claims = claims();
		claims.put("nonce", "someone-elses-nonce");

		assertRejected(sign("k1", "RS256", rsa, claims), "nonce 不一致");

	}

	@Test
	@DisplayName("F-W-31 こちらの nonce が無ければ通さない")
	void rejectsMissingOwnNonce () {

		String token = sign("k1", "RS256", rsa, claims());

		assertThrows(OidcException.class
			, () -> IdToken.verify(OidcProvider.of(PROVIDER), token, "")
			, "セッションが切れているのに通っている");

	}

	@Test
	@DisplayName("F-W-31 どちらの nonce も空なら通さない（空と空を突き合わせない）")
	void rejectsWhenBothNoncesAreEmpty () {

		/*
		 * <b>ここが抜けやすい。</b>
		 * {@code session.get} は<b>無い鍵に空文字を返す</b>ので、
		 * {@code start} を通らずにコールバックだけ叩かれると、こちらの nonce は空になる。
		 * そこへ<b>nonce の無いトークン</b>を持ち込まれると、
		 * 突き合わせは「空 == 空」で通ってしまう——<b>nonce を見ていないのと同じ</b>になる。
		 *
		 * <b>先に「こちらの nonce があるか」を見る</b>ことで塞いでいる。
		 */
		Data claims = claims();
		claims.remove("nonce");

		String token = sign("k1", "RS256", rsa, claims);

		assertThrows(OidcException.class
			, () -> IdToken.verify(OidcProvider.of(PROVIDER), token, "")
			, "空と空を突き合わせて通している");

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * <b>alg の表を引くところと、3つに割れているかを見るところは、
	 * 外してもテストが落ちない。</b>どちらも<b>二重の守り</b>だからである——
	 *
	 * - alg：表に無いものは JDK の {@code Signature} を作るところまで行かないので、
	 *   判定を外しても {@code none} や {@code HS256} が通ることはない
	 * - 3つに割れているか：署名が無ければ、そのあとで必ず落ちる
	 *
	 * <b>「通らないこと」自体は上のテストで固定してある</b>（{@code rejectsNone} /
	 * {@code rejectsAlgorithmConfusion} / {@code rejectsUnsecured}）。
	 * ここで固定できないのは<b>どちらの守りが効いたか</b>で、それは見なくてよい。
	 *
	 * 判定を残しているのは、<b>落ちる理由がログに出る</b>ためである。
	 * 「署名を確かめられませんでした」より「通さないアルゴリズムです: none」のほうが、
	 * 攻撃を受けていると分かる。
	 */

	// endregion

	// region 補助

	/**
	 * 検証する
	 *
	 * @param token	トークン
	 * @return	中身
	 */
	private static Data verify (String token) {

		return IdToken.verify(OidcProvider.of(PROVIDER), token, NONCE);

	}

	/**
	 * 落ちることを確かめる
	 *
	 * @param token	トークン
	 * @param what	何を試したか
	 */
	private static void assertRejected (String token, String what) {

		assertThrows(OidcException.class, () -> verify(token), "%s が通ってしまった".formatted(what));

	}

	/**
	 * 正しい中身
	 *
	 * @return	中身
	 */
	private static Data claims () {

		Data claims = new Data();

		claims.put("iss", ISSUER);
		claims.put("aud", CLIENT_ID);
		claims.put("sub", "u-1");
		claims.put("nonce", NONCE);
		claims.put("email", "taro@example.com");
		claims.put("email_verified", true);
		claims.put("name", "申請 太郎");
		claims.put("iat", Instant.now().getEpochSecond());
		claims.put("exp", Instant.now().getEpochSecond() + 3600);

		return claims;

	}

	/**
	 * 署名する
	 *
	 * @param kid		鍵の名前
	 * @param algorithm	アルゴリズム
	 * @param keyPair	鍵
	 * @param claims	中身
	 * @return	トークン
	 */
	private static String sign (String kid, String algorithm, KeyPair keyPair, Data claims) {

		try {

			String header = base64("{\"alg\":\"%s\",\"typ\":\"JWT\",\"kid\":\"%s\"}".formatted(kid.isEmpty() ? "" : algorithm, kid));
			String payload = base64(Dson.encodes(claims));

			String javaName = switch (algorithm) {
				case "RS256" -> "SHA256withRSA";
				case "ES256" -> "SHA256withECDSAinP1363Format";
				default -> throw new IllegalArgumentException(algorithm);
			};

			Signature signature = Signature.getInstance(javaName);
			signature.initSign(keyPair.getPrivate());
			signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));

			return header + "." + payload + "." + ENCODER.encodeToString(signature.sign());

		} catch (Exception cause) {
			throw new IllegalStateException(cause);
		}

	}

	/**
	 * base64url
	 *
	 * @param value	値
	 * @return	base64url
	 */
	private static String base64 (String value) {

		return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));

	}

	// endregion

}
