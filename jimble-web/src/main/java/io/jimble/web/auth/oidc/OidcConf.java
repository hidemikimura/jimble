package io.jimble.web.auth.oidc;

import io.jimble.util.conf.Conf;

import java.util.List;

/**
 * OIDC のプロバイダごとの設定（要件 F-W-31）
 *
 * <pre>
 * auth {
 *     oidc {
 *         google {
 *             issuer        = "https://accounts.google.com"
 *             client_id     = ${?GOOGLE_CLIENT_ID}
 *             client_secret = ${?GOOGLE_CLIENT_SECRET}
 *             redirect_uri  = "https://example.com/auth/google/callback"
 *
 *             # 省略できる。discovery（issuer + /.well-known/openid-configuration）で引く
 *             # authorization_endpoint = "..."
 *             # token_endpoint         = "..."
 *             # jwks_uri               = "..."
 *
 *             scopes         = "openid email profile"
 *             clock_skew     = 60      # 秒。時計のずれをどこまで許すか
 *             discovery_ttl  = 3600    # 秒。discovery と JWKS を持つ時間
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>鍵は設定ファイルに書かない</h2>
 * <p>
 * <b>{@code client_secret} は環境変数から入れること</b>（{@code ${?ENV}}）。
 * 設定ファイルに直に書くと<b>リポジトリに入る</b>。
 * </p>
 */
public final class OidcConf {

	/** 設定の根 */
	private static final String ROOT = "auth.oidc.";

	private OidcConf () {
	}

	/**
	 * 設定を読む
	 *
	 * @param provider	プロバイダの名前
	 * @param key		鍵
	 * @return	値。無ければ空文字
	 */
	static String get (String provider, String key) {

		return Conf.conf().getString(ROOT + provider + "." + key, "");

	}

	/**
	 * 設定を読む（数）
	 *
	 * @param provider		プロバイダの名前
	 * @param key			鍵
	 * @param defaultValue	既定値
	 * @return	値
	 */
	static long getLong (String provider, String key, long defaultValue) {

		return Conf.conf().getLong(ROOT + provider + "." + key, defaultValue);

	}

	/**
	 * そのプロバイダの設定があるか
	 *
	 * @param provider	プロバイダの名前
	 * @return	ある場合 = true
	 */
	static boolean exists (String provider) {

		return !get(provider, "client_id").isEmpty();

	}

	/**
	 * 要求する scope
	 *
	 * <p><b>{@code openid} は必ず入る。</b>無いと ID トークンが返ってこない。</p>
	 *
	 * @param provider	プロバイダの名前
	 * @return	scope
	 */
	static String scopes (String provider) {

		String scopes = get(provider, "scopes");

		if (scopes.isEmpty()) {
			return "openid email profile";
		}

		return List.of(scopes.split("\\s+")).contains("openid") ? scopes : "openid " + scopes;

	}

	/**
	 * 時計のずれをどこまで許すか（秒）
	 *
	 * <p>
	 * <b>0 にしないこと。</b>こちらの時計とプロバイダの時計は必ず少しずれていて、
	 * <b>発行された直後のトークンが「まだ有効になっていない」で落ちる</b>。
	 * </p>
	 *
	 * @param provider	プロバイダの名前
	 * @return	秒
	 */
	static long clockSkewSeconds (String provider) {

		return Math.max(0, getLong(provider, "clock_skew", 60));

	}

	/**
	 * discovery と JWKS を持っておく時間（秒）
	 *
	 * @param provider	プロバイダの名前
	 * @return	秒
	 */
	static long cacheSeconds (String provider) {

		return Math.max(60, getLong(provider, "discovery_ttl", 3600));

	}

}
