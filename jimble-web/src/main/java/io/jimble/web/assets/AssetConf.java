package io.jimble.web.assets;

import java.time.Duration;
import io.jimble.util.conf.Conf;

/**
 * 静的ファイル配信の設定
 *
 * <pre>
 * assets {
 *   max_age            = 0s     # Cache-Control の max-age
 *   immutable_max_age  = 365d
 *   if_modified_since  = true   # If-Modified-Since を見るか
 *   etag               = true   # ETag / If-None-Match を使うか
 * }
 * </pre>
 */
public final class AssetConf {

	/** 設定キー：max-age */
	public static final String KEY_MAX_AGE = "assets.max_age";

	/** 設定キー：不変扱いのファイルの max-age */
	public static final String KEY_IMMUTABLE_MAX_AGE = "assets.immutable_max_age";

	/** 設定キー：If-Modified-Since を見るか */
	public static final String KEY_IF_MODIFIED_SINCE = "assets.if_modified_since";

	/** 設定キー：ETag を使うか */
	public static final String KEY_ETAG = "assets.etag";

	/** 既定の不変 max-age（1年） */
	public static final Duration DEFAULT_IMMUTABLE_MAX_AGE = Duration.ofDays(365);

	private AssetConf () {}

	/**
	 * max-age
	 *
	 * @return	時間
	 */
	public static Duration maxAge () {

		return Conf.conf().getDuration(KEY_MAX_AGE, Duration.ZERO);

	}

	/**
	 * 不変扱いのファイルの max-age
	 *
	 * @return	時間
	 */
	public static Duration immutableMaxAge () {

		return Conf.conf().getDuration(KEY_IMMUTABLE_MAX_AGE, DEFAULT_IMMUTABLE_MAX_AGE);

	}

	/**
	 * If-Modified-Since を見るか
	 *
	 * @return	見る場合 = true
	 */
	public static boolean ifModifiedSince () {

		return Conf.conf().getBoolean(KEY_IF_MODIFIED_SINCE, true);

	}

	/**
	 * ETag を使うか
	 *
	 * @return	使う場合 = true
	 */
	public static boolean etag () {

		return Conf.conf().getBoolean(KEY_ETAG, true);

	}

}
