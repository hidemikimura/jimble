package io.jimble.web.sse;

import io.jimble.util.conf.Conf;

/**
 * SSE の設定（要件 F-W-21）
 *
 * <pre>
 * sse {
 *   max_duration_seconds = 300   # 1本を張っていられる上限
 *   max_events           = 0     # 送れる件数の上限（0 = 無制限）
 *   retry_millis         = 3000  # 切れたクライアントに何ミリ秒後に繋ぎ直させるか
 * }
 * </pre>
 */
public final class SseConf {

	/** 設定キー：1本を張っていられる上限（秒） */
	public static final String KEY_MAX_DURATION = "sse.max_duration_seconds";

	/** 設定キー：送れる件数の上限 */
	public static final String KEY_MAX_EVENTS = "sse.max_events";

	/** 設定キー：繋ぎ直しまでの待ち（ミリ秒） */
	public static final String KEY_RETRY_MILLIS = "sse.retry_millis";

	/** 既定：1本を張っていられる上限（秒） */
	public static final long DEFAULT_MAX_DURATION_SECONDS = 300;

	/** 既定：繋ぎ直しまでの待ち（ミリ秒） */
	public static final long DEFAULT_RETRY_MILLIS = 3000;

	private SseConf () {}

	/**
	 * 1本を張っていられる上限（秒）
	 *
	 * @return	秒。0 以下なら無制限
	 */
	public static long maxDurationSeconds () {

		return Conf.conf().getLong(KEY_MAX_DURATION, DEFAULT_MAX_DURATION_SECONDS);

	}

	/**
	 * 送れる件数の上限
	 *
	 * @return	件数。0 以下なら無制限
	 */
	public static long maxEvents () {

		return Conf.conf().getLong(KEY_MAX_EVENTS, 0);

	}

	/**
	 * 繋ぎ直しまでの待ち（ミリ秒）
	 *
	 * @return	ミリ秒。0 以下なら送らない
	 */
	public static long retryMillis () {

		return Conf.conf().getLong(KEY_RETRY_MILLIS, DEFAULT_RETRY_MILLIS);

	}

}
