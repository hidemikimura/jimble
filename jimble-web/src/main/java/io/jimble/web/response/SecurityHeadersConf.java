package io.jimble.web.response;

import io.jimble.util.conf.Conf;

import java.time.Duration;

/**
 * 既定で付けるセキュリティのヘッダ（D-213）
 *
 * <pre>
 * security_headers {
 *   enabled                 = true
 *   content_type_options    = "nosniff"                          # X-Content-Type-Options
 *   frame_options           = "SAMEORIGIN"                       # X-Frame-Options（クリックジャッキング）
 *   referrer_policy         = "strict-origin-when-cross-origin"  # Referrer-Policy
 *   hsts                    = 0s                                 # Strict-Transport-Security の max-age。0s なら付けない
 *   hsts_include_subdomains = false
 * }
 * </pre>
 *
 * <p>
 * <b>2.2.2 までは何も付けていなかった。</b>どのページも別のサイトの iframe に入れられ（クリックジャッキング）、
 * 拡張子の無い静的ファイルやダウンロードはブラウザが中身から種類を推測した。
 * </p>
 *
 * <ul>
 *   <li><b>アプリが同じ名前のヘッダを決めていれば上書きしない</b>（{@code setResponseHeader} / {@code addResponseHeader}）</li>
 *   <li>値を {@code ""} にすると、そのヘッダは付けない。{@code enabled = false} なら全部付けない</li>
 *   <li><b>HSTS は既定で付けない。</b>一度ブラウザに覚えられると、その期間は http に戻せないため。
 *       付けるのは https で受けたときだけ</li>
 * </ul>
 */
public final class SecurityHeadersConf {

	/** 設定キー：付けるか */
	public static final String KEY_ENABLED = "security_headers.enabled";

	/** 設定キー：X-Content-Type-Options */
	public static final String KEY_CONTENT_TYPE_OPTIONS = "security_headers.content_type_options";

	/** 設定キー：X-Frame-Options */
	public static final String KEY_FRAME_OPTIONS = "security_headers.frame_options";

	/** 設定キー：Referrer-Policy */
	public static final String KEY_REFERRER_POLICY = "security_headers.referrer_policy";

	/** 設定キー：HSTS の max-age */
	public static final String KEY_HSTS = "security_headers.hsts";

	/** 設定キー：HSTS に includeSubDomains を付けるか */
	public static final String KEY_HSTS_INCLUDE_SUBDOMAINS = "security_headers.hsts_include_subdomains";

	private SecurityHeadersConf () {}

	/**
	 * 付けるか
	 *
	 * @return	付けるなら true（既定 true）
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, true);

	}

	/**
	 * X-Content-Type-Options
	 *
	 * @return	値（空なら付けない）
	 */
	public static String contentTypeOptions () {

		return Conf.conf().getString(KEY_CONTENT_TYPE_OPTIONS, "nosniff");

	}

	/**
	 * X-Frame-Options
	 *
	 * @return	値（空なら付けない）
	 */
	public static String frameOptions () {

		return Conf.conf().getString(KEY_FRAME_OPTIONS, "SAMEORIGIN");

	}

	/**
	 * Referrer-Policy
	 *
	 * @return	値（空なら付けない）
	 */
	public static String referrerPolicy () {

		return Conf.conf().getString(KEY_REFERRER_POLICY, "strict-origin-when-cross-origin");

	}

	/**
	 * HSTS の値
	 *
	 * @return	{@code max-age=...}。付けないなら null
	 */
	public static String hsts () {

		Duration maxAge = Conf.conf().getDuration(KEY_HSTS, Duration.ZERO);

		if (maxAge.isZero() || maxAge.isNegative()) {
			return null;
		}

		return "max-age=" + maxAge.toSeconds()
			+ (Conf.conf().getBoolean(KEY_HSTS_INCLUDE_SUBDOMAINS, false) ? "; includeSubDomains" : "");

	}

}
