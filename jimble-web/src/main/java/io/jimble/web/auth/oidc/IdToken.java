package io.jimble.web.auth.oidc;

import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * ID トークンを検証する（要件 F-W-31）
 *
 * <h2>ここが穴の作りどころである</h2>
 * <p>
 * JWT の検証で破られるところは、だいたい決まっている。
 * <b>全部ここで、明示的に見ている。</b>
 * </p>
 *
 * <ol>
 *   <li><b>{@code alg} をヘッダから信じない。</b>信じると
 *       <b>{@code "alg":"none"} で署名を外せる</b>し、
 *       <b>{@code HS256} に変えて「公開鍵を共有鍵として」通される</b>
 *       （公開鍵は公開されているので、誰でも署名を作れる）</li>
 *   <li><b>{@code kid} で鍵を選ぶ。</b>どれか1つでも通ればよい、にしない</li>
 *   <li><b>{@code iss} は設定と1文字違わず一致すること。</b>前方一致にしない</li>
 *   <li><b>{@code aud} に自分がいること。</b>複数入っていたら {@code azp} も見る</li>
 *   <li><b>{@code exp} / {@code iat}。</b>時計のずれぶんだけ緩める</li>
 *   <li><b>{@code nonce} が、こちらが送ったものと一致すること</b>（使い回しを弾く）</li>
 * </ol>
 */
final class IdToken {

	/**
	 * 通すアルゴリズムと、JDK での名前
	 *
	 * <p>
	 * <b>この表が唯一の入口である。</b>「通してよいか」と「どう検証するか」を
	 * 別々に持つと、<b>片方に足してもう片方に足し忘れる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>{@code none} も {@code HS*} も、ここに無いので検証されることがない</b>——
	 * 名前を引けなければ {@code Signature} を作るところまで行かない。
	 * </p>
	 */
	private static final Map<String, String> ALGORITHMS = Map.of(
		"RS256", "SHA256withRSA"
		, "RS384", "SHA384withRSA"
		, "RS512", "SHA512withRSA"
		, "ES256", "SHA256withECDSAinP1363Format"
		, "ES384", "SHA384withECDSAinP1363Format"
		, "ES512", "SHA512withECDSAinP1363Format"
	);

	/** base64url */
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private IdToken () {
	}

	/**
	 * 検証して中身を返す
	 *
	 * @param provider	プロバイダ
	 * @param token		ID トークン
	 * @param nonce		こちらが送った nonce
	 * @return	中身
	 */
	static Data verify (OidcProvider provider, String token, String nonce) {

		String[] parts = split(token);

		Data header = decode(parts[0], "ヘッダ");
		Data claims = decode(parts[1], "中身");

		verifySignature(provider, parts, header);
		verifyClaims(provider, claims, nonce);

		return claims;

	}

	// region 署名

	/**
	 * 署名を見る
	 *
	 * @param provider	プロバイダ
	 * @param parts		3つに割ったもの
	 * @param header	ヘッダ
	 */
	private static void verifySignature (OidcProvider provider, String[] parts, Data header) {

		String algorithm = header.getStringOptional("alg");

		/*
		 * <b>まず表を引く。</b>引けないものは検証しない——
		 * {@code none} は「署名が空でも検証したことになる」、
		 * {@code HS*} は共有鍵の方式なので<b>公開鍵を鍵として渡すと誰でも署名できる</b>。
		 * どちらも表に無いので、ここで終わる。
		 */
		String javaName = ALGORITHMS.get(algorithm);

		if (javaName == null) {
			throw new OidcException("通さないアルゴリズムです: " + algorithm);
		}

		String kid = header.getStringOptional("kid");

		Jwks.Key key = Jwks.find(provider, kid);

		if (key == null) {
			throw new OidcException("鍵が見つかりません: kid=" + kid);
		}

		/*
		 * <b>JWKS が alg を名乗っているなら、それとも一致すること。</b>
		 * ヘッダだけ見ていると、<b>RSA の鍵で ES256 だと言い張る</b>ような
		 * 取り違えを通してしまう。
		 */
		if (!key.algorithm().isEmpty() && !key.algorithm().equals(algorithm)) {
			throw new OidcException("鍵のアルゴリズムと合いません: ヘッダ=%s / 鍵=%s"
				.formatted(algorithm, key.algorithm()));
		}

		try {

			Signature signature = Signature.getInstance(javaName);
			signature.initVerify(key.key());
			signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

			if (!signature.verify(DECODER.decode(parts[2]))) {
				throw new OidcException("署名が合いません");
			}

		} catch (OidcException cause) {
			throw cause;
		} catch (Exception cause) {
			throw new OidcException("署名を確かめられませんでした: " + cause.getMessage(), cause);
		}

	}

	// endregion

	// region 中身

	/**
	 * 中身を見る
	 *
	 * @param provider	プロバイダ
	 * @param claims	中身
	 * @param nonce		こちらが送った nonce
	 */
	private static void verifyClaims (OidcProvider provider, Data claims, String nonce) {

		/*
		 * <b>前方一致にしない。</b>{@code https://accounts.google.com.example.jp} が
		 * 通ってしまう。
		 */
		if (!provider.issuer.equals(claims.getStringOptional("iss"))) {
			throw new OidcException("iss が違います: " + claims.getStringOptional("iss"));
		}

		verifyAudience(provider, claims);

		if (claims.getStringOptional("sub").isEmpty()) {
			throw new OidcException("sub がありません");
		}

		long now = Instant.now().getEpochSecond();
		long skew = OidcConf.clockSkewSeconds(provider.name);

		long expiresAt = claims.getLong("exp");

		if (expiresAt <= 0) {
			throw new OidcException("exp がありません");
		}

		if (now - skew > expiresAt) {
			throw new OidcException("期限が切れています");
		}

		/*
		 * <b>未来に発行されたことになっているものを通さない。</b>
		 * 通すと、<b>いくらでも先の期限を持ったトークンを作れる</b>。
		 */
		long issuedAt = claims.getLong("iat");

		if (issuedAt > 0 && issuedAt - skew > now) {
			throw new OidcException("発行時刻が未来です");
		}

		long notBefore = claims.getLong("nbf");

		if (notBefore > 0 && notBefore - skew > now) {
			throw new OidcException("まだ有効になっていません");
		}

		verifyNonce(claims, nonce);

	}

	/**
	 * aud を見る
	 *
	 * @param provider	プロバイダ
	 * @param claims	中身
	 */
	private static void verifyAudience (OidcProvider provider, Data claims) {

		/*
		 * aud は<b>文字列のことも配列のことも</b>ある。
		 */
		List<String> audiences = claims.isNull("aud")
			? List.of()
			: claims.get("aud") instanceof List<?> list
				? list.stream().map(String::valueOf).toList()
				: List.of(String.valueOf(claims.get("aud")));

		if (!audiences.contains(provider.clientId)) {
			throw new OidcException("aud に自分がいません: " + audiences);
		}

		/*
		 * <b>宛先が複数あるなら azp まで見る。</b>
		 * 見ないと、<b>同じプロバイダの別のクライアント向けに出たトークン</b>を
		 * こちらへ持ち込めてしまう。
		 */
		if (audiences.size() > 1 && !provider.clientId.equals(claims.getStringOptional("azp"))) {
			throw new OidcException("aud が複数あり、azp が自分ではありません");
		}

	}

	/**
	 * nonce を見る
	 *
	 * @param claims	中身
	 * @param nonce		こちらが送った nonce
	 */
	private static void verifyNonce (Data claims, String nonce) {

		if (nonce == null || nonce.isEmpty()) {
			throw new OidcException("こちらの nonce がありません（セッションが切れています）");
		}

		/*
		 * <b>これが無いと、1度取れたトークンを何度でも使い回せる。</b>
		 * こちらが毎回作った値と一致することで、
		 * 「いまこの人がここで始めたログイン」に紐付く。
		 */
		if (!MessageDigest.isEqual(
			nonce.getBytes(StandardCharsets.UTF_8)
			, claims.getStringOptional("nonce").getBytes(StandardCharsets.UTF_8))) {
			throw new OidcException("nonce が合いません");
		}

	}

	// endregion

	// region 分解

	/**
	 * 3つに割る
	 *
	 * @param token	トークン
	 * @return	3つ
	 */
	private static String[] split (String token) {

		if (token == null || token.isEmpty()) {
			throw new OidcException("ID トークンがありません");
		}

		String[] parts = token.split("\\.", -1);

		/*
		 * <b>3つでなければ捨てる。</b>2つ（署名なし = JWS の unsecured）を
		 * 通すと、署名を見ないまま中身を信じることになる。
		 */
		if (parts.length != 3 || parts[2].isEmpty()) {
			throw new OidcException("ID トークンの形が違います");
		}

		return parts;

	}

	/**
	 * base64url の JSON を読む
	 *
	 * @param part	部分
	 * @param what	何か（メッセージ用）
	 * @return	中身
	 */
	private static Data decode (String part, String what) {

		try {

			Data data = Dson.decodes(new String(DECODER.decode(part), StandardCharsets.UTF_8), Data.class);

			if (data == null) {
				throw new OidcException("ID トークンの%sが読めません".formatted(what));
			}

			return data;

		} catch (OidcException cause) {
			throw cause;
		} catch (Exception cause) {
			throw new OidcException("ID トークンの%sが読めません".formatted(what), cause);
		}

	}

	// endregion

}
