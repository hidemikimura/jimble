package io.jimble.web.cookie;

import io.jimble.util.conf.Conf;

/**
 * Cookie の既定値
 *
 * <pre>
 * cookie {
 *   secret     = "..."     # 署名鍵。空なら署名しない
 *   secure     = true      # HTTPS のみ
 *   http_only  = true      # JavaScript から読めない
 *   same_site  = "lax"     # none | strict | lax
 *   domain     = ""        # 省略で発行元ドメイン
 *   max_age    = 31536000  # 秒。0 以下でセッション Cookie
 * }
 * </pre>
 */
public final class CookieConf {

	/** 設定キー：署名鍵 */
	public static final String KEY_SECRET = "cookie.secret";

	/** 設定キー：HTTPS のみ */
	public static final String KEY_SECURE = "cookie.secure";

	/** 設定キー：HttpOnly */
	public static final String KEY_HTTP_ONLY = "cookie.http_only";

	/** 設定キー：SameSite */
	public static final String KEY_SAME_SITE = "cookie.same_site";

	/** 設定キー：ドメイン */
	public static final String KEY_DOMAIN = "cookie.domain";

	/** 設定キー：有効秒数 */
	public static final String KEY_MAX_AGE = "cookie.max_age";

	/** 既定の有効秒数（1年） */
	public static final long DEFAULT_MAX_AGE = 365L * 24 * 60 * 60;

	private CookieConf () {}

	/**
	 * 署名鍵
	 *
	 * <p>空なら署名しない。</p>
	 *
	 * @return	鍵
	 */
	public static String secret () {

		return Conf.conf().getString(KEY_SECRET, "");

	}

	/**
	 * 署名するか
	 *
	 * @return	する場合 = true
	 */
	public static boolean isSigned () {

		return !secret().isEmpty();

	}

	/**
	 * HTTPS のみか
	 *
	 * <p>
	 * 既定は true。<b>安全側を既定にする。</b>
	 * ローカルで http を使うときだけ明示的に false にする。
	 * </p>
	 *
	 * @return	HTTPS のみなら true
	 */
	public static boolean secure () {

		return Conf.conf().getBoolean(KEY_SECURE, true);

	}

	/**
	 * HttpOnly か
	 *
	 * @return	HttpOnly なら true
	 */
	public static boolean httpOnly () {

		return Conf.conf().getBoolean(KEY_HTTP_ONLY, true);

	}

	/**
	 * SameSite
	 *
	 * <p>既定は {@code Lax}。</p>
	 *
	 * @return	SameSite
	 */
	public static SameSite sameSite () {

		SameSite sameSite = SameSite.of(Conf.conf().getString(KEY_SAME_SITE, "lax"));

		return sameSite == null ? SameSite.LAX : sameSite;

	}

	/**
	 * ドメイン
	 *
	 * @return	ドメイン（未設定なら空文字）
	 */
	public static String domain () {

		return Conf.conf().getString(KEY_DOMAIN, "");

	}

	/**
	 * 有効秒数
	 *
	 * @return	秒数
	 */
	public static long maxAge () {

		long maxAge = Conf.conf().getLong(KEY_MAX_AGE, DEFAULT_MAX_AGE);

		return maxAge > 0 ? maxAge : Cookie.MAX_AGE_SESSION;

	}

	/**
	 * 設定を反映した Cookie を作る
	 *
	 * @param name	名前
	 * @param value	値（null で削除）
	 * @return	Cookie
	 */
	public static Cookie create (String name, String value) {

		return create(name, value, maxAge());

	}

	/**
	 * 設定を反映した Cookie を作る
	 *
	 * @param name		名前
	 * @param value		値（null で削除）
	 * @param maxAge	有効秒数
	 * @return	Cookie
	 */
	public static Cookie create (String name, String value, long maxAge) {

		Cookie cookie = new Cookie(name, value)
			.maxAge(maxAge)
			.secure(secure())
			.httpOnly(httpOnly())
			.sameSite(sameSite());

		String domain = domain();
		if (!domain.isEmpty()) {
			cookie.domain(domain);
		}

		return cookie;

	}

}
