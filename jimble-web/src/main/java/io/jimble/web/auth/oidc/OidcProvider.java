package io.jimble.web.auth.oidc;

import io.jimble.util.data.Data;
import io.jimble.util.http.httpclient.method.HttpGetExecutor;
import io.jimble.util.log.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * つなぎ先のプロバイダ（要件 F-W-31）
 *
 * <p>
 * 設定に書いてあるものを使い、書いていないものは
 * <b>discovery</b>（{@code issuer + /.well-known/openid-configuration}）で引く。
 * </p>
 *
 * <h2>起動時には引かない</h2>
 * <p>
 * <b>最初に使うときに引いて、しばらく持つ</b>（既定1時間）。
 * 起動時に引くと、<b>プロバイダが落ちているあいだアプリが起動しなくなる</b>——
 * OIDC を使わない画面まで巻き添えになる（原則5）。
 * </p>
 */
final class OidcProvider {

	/* 引いたものを持っておく */
	private static final Map<String, OidcProvider> CACHE = new ConcurrentHashMap<>();

	/** 名前 */
	final String name;

	/** issuer。<b>ID トークンの iss と1文字違わず一致すること</b> */
	final String issuer;

	/** クライアント ID */
	final String clientId;

	/** クライアント秘密 */
	final String clientSecret;

	/** 戻り先 */
	final String redirectUri;

	/** 認可の入口 */
	final String authorizationEndpoint;

	/** トークンの口 */
	final String tokenEndpoint;

	/** 鍵の置き場 */
	final String jwksUri;

	/* いつ引いたか */
	private final Instant fetchedAt;

	/**
	 * コンストラクタ
	 *
	 * @param name			名前
	 * @param discovered	discovery で引いたもの。引いていなければ空
	 */
	private OidcProvider (String name, Data discovered) {

		this.name = name;
		this.issuer = OidcConf.get(name, "issuer");
		this.clientId = OidcConf.get(name, "client_id");
		this.clientSecret = OidcConf.get(name, "client_secret");
		this.redirectUri = OidcConf.get(name, "redirect_uri");

		this.authorizationEndpoint = pick(name, "authorization_endpoint", discovered);
		this.tokenEndpoint = pick(name, "token_endpoint", discovered);
		this.jwksUri = pick(name, "jwks_uri", discovered);

		this.fetchedAt = Instant.now();

	}

	/**
	 * 取り出す（要れば discovery を引く）
	 *
	 * @param name	名前
	 * @return	プロバイダ
	 */
	static OidcProvider of (String name) {

		OidcProvider cached = CACHE.get(name);

		if (cached != null && !cached.isStale()) {
			return cached;
		}

		OidcProvider provider = create(name);

		CACHE.put(name, provider);

		return provider;

	}

	/**
	 * 持っているものを捨てる（テストと、設定を変えたとき）
	 */
	static void reset () {

		CACHE.clear();

	}

	/**
	 * 作る
	 *
	 * @param name	名前
	 * @return	プロバイダ
	 */
	private static OidcProvider create (String name) {

		if (!OidcConf.exists(name)) {
			throw new IllegalStateException(
				"OIDC の設定がありません: auth.oidc.%s.client_id".formatted(name));
		}

		String issuer = OidcConf.get(name, "issuer");

		if (issuer.isEmpty()) {
			throw new IllegalStateException("issuer がありません: auth.oidc.%s.issuer".formatted(name));
		}

		/*
		 * <b>設定に3つとも書いてあるなら、discovery は引かない。</b>
		 * 引かずに済むなら、そのぶん外へ出る回数が減る。
		 */
		boolean complete = !OidcConf.get(name, "authorization_endpoint").isEmpty()
			&& !OidcConf.get(name, "token_endpoint").isEmpty()
			&& !OidcConf.get(name, "jwks_uri").isEmpty();

		OidcProvider provider = new OidcProvider(name, complete ? new Data() : discover(name, issuer));

		provider.verify();

		return provider;

	}

	/**
	 * discovery を引く
	 *
	 * @param name		名前
	 * @param issuer	issuer
	 * @return	引いたもの
	 */
	private static Data discover (String name, String issuer) {

		String url = issuer.endsWith("/")
			? issuer + ".well-known/openid-configuration"
			: issuer + "/.well-known/openid-configuration";

		HttpGetExecutor http = new HttpGetExecutor().setUrl(url).setTimeout(10000).execute();

		if (http.errorException != null || http.responseCode != 200) {
			throw new IllegalStateException("OIDC の discovery を引けませんでした: %s（%d）"
				.formatted(url, http.responseCode), http.errorException);
		}

		Data discovered = http.getContentJson();

		if (discovered == null) {
			throw new IllegalStateException("OIDC の discovery が読めませんでした: " + url);
		}

		/*
		 * <b>返ってきた issuer が、設定の issuer と一致すること。</b>
		 * ここを見ないと、<b>issuer を差し替えた偽の discovery</b> に
		 * 認可の入口ごと連れて行かれる。
		 */
		String responded = discovered.getString("issuer");

		if (!issuer.equals(responded)) {
			throw new IllegalStateException(
				"discovery の issuer が設定と違います: 設定=%s / 返答=%s".formatted(issuer, responded));
		}

		Log.info("OIDC の discovery を引きました: %s".formatted(name));

		return discovered;

	}

	/**
	 * 設定になければ discovery から取る
	 *
	 * @param name			名前
	 * @param key			鍵
	 * @param discovered	discovery で引いたもの
	 * @return	値
	 */
	private static String pick (String name, String key, Data discovered) {

		String configured = OidcConf.get(name, key);

		return configured.isEmpty() ? discovered.getStringOptional(key) : configured;

	}

	/**
	 * 揃っているか確かめる
	 */
	private void verify () {

		require(clientId, "client_id");
		require(redirectUri, "redirect_uri");
		require(authorizationEndpoint, "authorization_endpoint");
		require(tokenEndpoint, "token_endpoint");
		require(jwksUri, "jwks_uri");

		/*
		 * <b>https でないところへは出さない。</b>
		 * 認可コードもトークンもここを通るので、http だと素通しで見える。
		 * localhost だけは開発のために通す。
		 */
		https(authorizationEndpoint, "authorization_endpoint");
		https(tokenEndpoint, "token_endpoint");
		https(jwksUri, "jwks_uri");

	}

	/**
	 * 空でないこと
	 *
	 * @param value	値
	 * @param key	鍵
	 */
	private void require (String value, String key) {

		if (value == null || value.isEmpty()) {
			throw new IllegalStateException(
				"OIDC の設定が足りません: auth.oidc.%s.%s（discovery でも取れませんでした）"
					.formatted(name, key));
		}

	}

	/**
	 * https であること
	 *
	 * @param url	URL
	 * @param key	鍵
	 */
	private void https (String url, String key) {

		if (url.startsWith("https://")) {
			return;
		}

		if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
			Log.warn("OIDC の %s が http です（localhost なので通します）: %s".formatted(key, url));
			return;
		}

		throw new IllegalStateException("OIDC の %s は https でなければなりません: %s".formatted(key, url));

	}

	/**
	 * 引き直す時期か
	 *
	 * @return	引き直す場合 = true
	 */
	private boolean isStale () {

		return Instant.now().isAfter(fetchedAt.plus(Duration.ofSeconds(OidcConf.cacheSeconds(name))));

	}

}
