package io.jimble.web.ratelimit;

import io.jimble.util.conf.Conf;

/**
 * 流量制限の設定（要件 F-R-15）
 *
 * <pre>
 * rate_limit {
 *   store   = "memory"   # memory | redis | db
 *   enabled = true       # false にすると宣言してあっても数えない
 * }
 * </pre>
 */
public final class RateLimitConf {

	/** 設定キー：置き場 */
	public static final String KEY_STORE = "rate_limit.store";

	/** 設定キー：有効か */
	public static final String KEY_ENABLED = "rate_limit.enabled";

	/** 置き場：メモリ */
	public static final String STORE_MEMORY = "memory";

	/** 置き場：Redis */
	public static final String STORE_REDIS = "redis";

	/** 置き場：DB */
	public static final String STORE_DB = "db";

	/** 既定の置き場 */
	public static final String DEFAULT_STORE = STORE_MEMORY;

	private RateLimitConf () {}

	/**
	 * 置き場
	 *
	 * @return	置き場
	 */
	public static String store () {

		String value = Conf.conf().getString(KEY_STORE, DEFAULT_STORE).trim();

		return value.isEmpty() ? DEFAULT_STORE : value;

	}

	/**
	 * 数えるか
	 *
	 * @return	数えるなら true
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, true);

	}

}
