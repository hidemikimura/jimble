package io.jimble.web.response;

import io.jimble.util.conf.Conf;
import io.jimble.util.conf.ConfFlag;
import io.jimble.util.conf.ConfValue;

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

	/* 読み込んだ設定 1 つにつき 1 回だけ解釈して持つ（2.7.5。応答のたびに読んでいた） */
	private static final ConfFlag ENABLED = ConfFlag.of(KEY_ENABLED, true);
	private static final ConfValue<String> CONTENT_TYPE_OPTIONS = ConfValue.of(
		c -> c.getString(KEY_CONTENT_TYPE_OPTIONS, "nosniff"));
	private static final ConfValue<String> FRAME_OPTIONS = ConfValue.of(
		c -> c.getString(KEY_FRAME_OPTIONS, "SAMEORIGIN"));
	private static final ConfValue<String> REFERRER_POLICY = ConfValue.of(
		c -> c.getString(KEY_REFERRER_POLICY, "strict-origin-when-cross-origin"));
	private static final ConfValue<String> HSTS = ConfValue.of(SecurityHeadersConf::readHsts);

	private SecurityHeadersConf () {}

	/**
	 * 付けるか
	 *
	 * @return	付けるなら true（既定 true）
	 */
	public static boolean enabled () {

		return ENABLED.get();

	}

	/**
	 * X-Content-Type-Options
	 *
	 * @return	値（空なら付けない）
	 */
	public static String contentTypeOptions () {

		return CONTENT_TYPE_OPTIONS.get();

	}

	/**
	 * X-Frame-Options
	 *
	 * @return	値（空なら付けない）
	 */
	public static String frameOptions () {

		return FRAME_OPTIONS.get();

	}

	/**
	 * Referrer-Policy
	 *
	 * @return	値（空なら付けない）
	 */
	public static String referrerPolicy () {

		return REFERRER_POLICY.get();

	}

	/**
	 * HSTS の値
	 *
	 * @return	{@code max-age=...}。付けないなら null
	 */
	public static String hsts () {

		return HSTS.get();

	}

	/**
	 * HSTS の値を設定から作る
	 *
	 * @param conf	設定
	 * @return	ヘッダの値。出さないなら null
	 */
	private static String readHsts (Conf conf) {

		Duration maxAge = conf.getDuration(KEY_HSTS, Duration.ZERO);

		if (maxAge.isZero() || maxAge.isNegative()) {
			return null;
		}

		return "max-age=" + maxAge.toSeconds()
			+ (conf.getBoolean(KEY_HSTS_INCLUDE_SUBDOMAINS, false) ? "; includeSubDomains" : "");

	}

}
