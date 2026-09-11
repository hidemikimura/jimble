package io.jimble.web.auth.oidc;

import io.jimble.util.data.Data;
import io.jimble.util.http.httpclient.method.HttpGetExecutor;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プロバイダの公開鍵（要件 F-W-31）
 *
 * <h2>外部のライブラリを入れていない</h2>
 * <p>
 * JWKS が返すのは <b>{@code n} / {@code e}（RSA）</b>か
 * <b>{@code x} / {@code y}（EC）</b>で、
 * どちらも <b>JDK の {@code KeyFactory} で公開鍵に戻せる</b>。
 * 署名の検証そのものも {@code java.security.Signature} がやる。
 * <b>JOSE のライブラリは要らなかった</b>（D-152。目的1「依存の縮小」）。
 * </p>
 *
 * <h2>鍵は回る</h2>
 * <p>
 * プロバイダは鍵を入れ替える。<b>知らない {@code kid} が来たら引き直す</b>。
 * ただし<b>引き直しには間隔を置く</b>——置かないと、
 * <b>でたらめな {@code kid} を送りつけるだけで JWKS を叩かせ続けられる</b>。
 * </p>
 */
final class Jwks {

	/** 引き直しの下限（これより短い間隔では引き直さない） */
	private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(5);

	/* プロバイダ名 → 鍵 */
	private static final Map<String, Jwks> CACHE = new ConcurrentHashMap<>();

	/** base64url */
	private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

	/* kid → 鍵 */
	private final Map<String, Key> keys = new LinkedHashMap<>();

	/* 最後に引いた時刻 */
	private Instant fetchedAt = Instant.EPOCH;

	/**
	 * 鍵1つ
	 *
	 * @param key		公開鍵
	 * @param algorithm	JWKS が名乗ったアルゴリズム。空のこともある
	 */
	record Key(PublicKey key, String algorithm) {}

	private Jwks () {
	}

	/**
	 * 鍵を取り出す
	 *
	 * @param provider	プロバイダ
	 * @param kid		鍵の名前
	 * @return	鍵。見つからなければ null
	 */
	static synchronized Key find (OidcProvider provider, String kid) {

		Jwks jwks = CACHE.computeIfAbsent(provider.name, name -> new Jwks());

		Key key = jwks.keys.get(kid);

		if (key != null) {
			return key;
		}

		/*
		 * 知らない kid。鍵が回ったのかもしれないので引き直す——
		 * <b>ただし前に引いてから間が空いていれば</b>。
		 */
		if (Instant.now().isBefore(jwks.fetchedAt.plus(REFRESH_INTERVAL))) {
			return null;
		}

		jwks.fetch(provider);

		return jwks.keys.get(kid);

	}

	/**
	 * 持っているものを捨てる（テスト用）
	 */
	static synchronized void reset () {

		CACHE.clear();

	}

	/**
	 * 直に入れる（テスト用）
	 *
	 * @param provider	プロバイダ名
	 * @param kid		鍵の名前
	 * @param key		鍵
	 */
	static synchronized void put (String provider, String kid, Key key) {

		Jwks jwks = CACHE.computeIfAbsent(provider, name -> new Jwks());

		jwks.keys.put(kid, key);
		jwks.fetchedAt = Instant.now();

	}

	/**
	 * 引く
	 *
	 * @param provider	プロバイダ
	 */
	private void fetch (OidcProvider provider) {

		fetchedAt = Instant.now();

		HttpGetExecutor http = new HttpGetExecutor()
			.setUrl(provider.jwksUri).setTimeout(10000).execute();

		if (http.errorException != null || http.responseCode != 200) {
			throw new IllegalStateException("JWKS を引けませんでした: %s（%d）"
				.formatted(provider.jwksUri, http.responseCode), http.errorException);
		}

		Data body = http.getContentJson();

		if (body == null) {
			throw new IllegalStateException("JWKS が読めませんでした: " + provider.jwksUri);
		}

		List<Data> list = body.getDataList("keys");

		if (list == null || list.isEmpty()) {
			throw new IllegalStateException("JWKS に鍵がありません: " + provider.jwksUri);
		}

		Map<String, Key> found = new LinkedHashMap<>();

		for (Data entry : list) {

			String kid = entry.getStringOptional("kid");
			String use = entry.getStringOptional("use");

			// 署名の検証に使わない鍵（暗号化用）は入れない
			if (!use.isEmpty() && !"sig".equals(use)) {
				continue;
			}

			PublicKey key = toKey(entry);

			if (key != null) {
				found.put(kid, new Key(key, entry.getStringOptional("alg")));
			}

		}

		/*
		 * <b>まるごと入れ替える。</b>足すだけにすると、
		 * <b>プロバイダが捨てた鍵をこちらが持ち続ける</b>ことになる。
		 */
		keys.clear();
		keys.putAll(found);

	}

	/**
	 * JWKS の1件を公開鍵にする
	 *
	 * @param entry	1件
	 * @return	公開鍵。作れなければ null
	 */
	private static PublicKey toKey (Data entry) {

		try {

			return switch (entry.getStringOptional("kty")) {

				case "RSA" -> KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
					unsigned(entry.getStringOptional("n")), unsigned(entry.getStringOptional("e"))));

				case "EC" -> ecKey(entry);

				// 知らない種類は使わない（oct = 共有鍵は、ここへ来てはいけない）
				default -> null;

			};

		} catch (Exception cause) {
			throw new IllegalStateException("JWKS の鍵を読めませんでした: " + entry.getStringOptional("kid"), cause);
		}

	}

	/**
	 * EC の公開鍵
	 *
	 * @param entry	1件
	 * @return	公開鍵
	 * @throws Exception	作れなかった場合
	 */
	private static PublicKey ecKey (Data entry) throws Exception {

		String curve = switch (entry.getStringOptional("crv")) {
			case "P-256" -> "secp256r1";
			case "P-384" -> "secp384r1";
			case "P-521" -> "secp521r1";
			default -> null;
		};

		if (curve == null) {
			return null;
		}

		AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
		parameters.init(new ECGenParameterSpec(curve));

		ECPoint point = new ECPoint(
			unsigned(entry.getStringOptional("x")), unsigned(entry.getStringOptional("y")));

		return KeyFactory.getInstance("EC").generatePublic(
			new ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec.class)));

	}

	/**
	 * base64url を符号なしの数にする
	 *
	 * @param value	base64url
	 * @return	数
	 */
	private static BigInteger unsigned (String value) {

		// 1 を渡すと符号なしになる。渡さないと、先頭のビットが立っている鍵が負になる
		return new BigInteger(1, URL_DECODER.decode(value));

	}

}
