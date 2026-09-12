package io.jimble.web.cookie;

import java.time.Duration;
import io.jimble.util.conf.Conf;
import io.jimble.util.crypto.Secrets;
import java.util.List;

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
 *   max_age    = 365d      # 0 以下でセッション Cookie
 *   accept_unsigned = false # 署名を入れる移行期間だけ true にする
 * }
 * </pre>
 */
public final class CookieConf {

	/** 設定キー：署名鍵 */
	public static final String KEY_SECRET = "cookie.secret";

	/** 設定キー：入れ替え前の署名鍵（読むときだけ試す） */
	public static final String KEY_PREVIOUS_SECRETS = "cookie.previous_secrets";

	/** 設定キー：HTTPS のみ */
	public static final String KEY_SECURE = "cookie.secure";

	/** 設定キー：HttpOnly */
	public static final String KEY_HTTP_ONLY = "cookie.http_only";

	/** 設定キー：SameSite */
	public static final String KEY_SAME_SITE = "cookie.same_site";

	/** 設定キー：ドメイン */
	public static final String KEY_DOMAIN = "cookie.domain";

	/**
	 * 設定キー：署名が無い Cookie も受け付けるか（要件 D-159）
	 *
	 * <p>
	 * <b>{@code cookie.secret} を初めて設定した瞬間、
	 * すでに配ってある Cookie が全部いっぺんに検証落ちして捨てられる</b>——
	 * {@code sid} も {@code csrf_token} も {@code remember} も flash もである。
	 * 見えるのは<b>全員ログアウト、フォームは 403</b>で、
	 * <b>例外もログも出ない</b>（捨てるのが正しい動きだからである）。
	 * </p>
	 *
	 * <p>
	 * <b>これを true にしている間は、署名が無い Cookie も読む。</b>
	 * 書くほうは最初から署名するので、<b>放っておけば署名つきに入れ替わる</b>——
	 * 入れ替わりきったら false に戻す（戻さないと、
	 * <b>署名を外した Cookie が通り続ける</b>ので意味が無くなる）。
	 * </p>
	 */
	public static final String KEY_ACCEPT_UNSIGNED = "cookie.accept_unsigned";

	/** 設定キー：有効期間 */
	public static final String KEY_MAX_AGE = "cookie.max_age";

	/** 既定の有効期間（1年） */
	public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(365);

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
	 * 署名鍵の並び（要件 NF-S-09）
	 *
	 * <p>
	 * <b>先頭が「いま書くのに使う鍵」</b>で、残りは
	 * {@code cookie.previous_secrets} に書いた古い鍵である。
	 * 読むときだけ順に試す。
	 * </p>
	 *
	 * <pre>
	 * cookie {
	 *     secret           = ${?COOKIE_SECRET}
	 *     previous_secrets = [${?COOKIE_SECRET_OLD}]
	 * }
	 * </pre>
	 *
	 * @return	鍵の並び（1つも無ければ空）
	 */
	public static List<String> secrets () {

		return Secrets.of(secret(), Conf.conf().getStringListOptional(KEY_PREVIOUS_SECRETS));

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
	 * 署名が無い Cookie も受け付けるか（要件 D-159）
	 *
	 * <p>
	 * <b>既定は false。</b>入れ替えの途中でだけ true にして、戻す。
	 * </p>
	 *
	 * @return	受け付ける場合 = true
	 */
	public static boolean acceptUnsigned () {

		return Conf.conf().getBoolean(KEY_ACCEPT_UNSIGNED, false);

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
	 * <p>
	 * <b>ここだけ秒で返す。</b>Set-Cookie の {@code Max-Age} が秒だからで、
	 * 呼んだ側がそのままヘッダに書ける形にしてある。
	 * </p>
	 *
	 * @return	秒数（0 以下ならセッション Cookie）
	 */
	public static long maxAge () {

		long maxAge = Conf.conf().getDuration(KEY_MAX_AGE, DEFAULT_MAX_AGE).toSeconds();

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
