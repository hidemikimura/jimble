package io.jimble.web.sse;

import java.time.Duration;
import io.jimble.util.conf.Conf;

/**
 * SSE の設定（要件 F-W-21）
 *
 * <pre>
 * sse {
 *   max_duration = 5m     # 1本を張っていられる上限
 *   max_events   = 0      # 送れる件数の上限（0 = 無制限）
 *   retry        = 3s     # 切れたクライアントにどれだけ後で繋ぎ直させるか
 * }
 * </pre>
 */
public final class SseConf {

	/** 設定キー：1本を張っていられる上限 */
	public static final String KEY_MAX_DURATION = "sse.max_duration";

	/** 設定キー：送れる件数の上限 */
	public static final String KEY_MAX_EVENTS = "sse.max_events";

	/** 設定キー：繋ぎ直しまでの待ち */
	public static final String KEY_RETRY = "sse.retry";

	/** 既定：1本を張っていられる上限 */
	public static final Duration DEFAULT_MAX_DURATION = Duration.ofMinutes(5);

	/** 既定：繋ぎ直しまでの待ち */
	public static final Duration DEFAULT_RETRY = Duration.ofSeconds(3);

	private SseConf () {}

	/**
	 * 1本を張っていられる上限
	 *
	 * @return	上限。0 以下なら無制限
	 */
	public static Duration maxDuration () {

		return Conf.conf().getDuration(KEY_MAX_DURATION, DEFAULT_MAX_DURATION);

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
	 * 繋ぎ直しまでの待ち
	 *
	 * @return	待ち。0 以下なら送らない
	 */
	public static Duration retry () {

		return Conf.conf().getDuration(KEY_RETRY, DEFAULT_RETRY);

	}

}
