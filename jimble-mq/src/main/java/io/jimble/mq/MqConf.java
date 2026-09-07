package io.jimble.mq;

import io.jimble.mq.status.MqExecuteType;
import io.jimble.util.conf.Conf;

/**
 * MQ の設定
 *
 * <pre>
 * mq {
 *   thread_count {
 *     short_time  = 2      # 種別ごとのスレッド数（要件 F-M-08）
 *     long_time   = 8
 *   }
 *   poll_min_ms                = 10     # キューが空でないときの待ち
 *   poll_max_ms                = 1000   # キューが空のときの待ち（だんだん伸びる）
 *   retry_backoff_seconds      = 10     # リトライの間隔（回を追うごとに倍）
 *   retry_backoff_max_seconds  = 600
 *   stale_seconds              = 600    # この秒数 running のままなら落ちたとみなす
 * }
 * </pre>
 */
public final class MqConf {

	/** 設定キーの前置き：種別ごとのスレッド数 */
	public static final String KEY_THREAD_COUNT = "mq.thread_count.";

	/** 設定キー：キューが空でないときの待ち（ミリ秒） */
	public static final String KEY_POLL_MIN_MS = "mq.poll_min_ms";

	/** 設定キー：キューが空のときの待ち（ミリ秒） */
	public static final String KEY_POLL_MAX_MS = "mq.poll_max_ms";

	/** 設定キー：リトライの間隔（秒） */
	public static final String KEY_RETRY_BACKOFF_SECONDS = "mq.retry_backoff_seconds";

	/** 設定キー：リトライ間隔の上限（秒） */
	public static final String KEY_RETRY_BACKOFF_MAX_SECONDS = "mq.retry_backoff_max_seconds";

	/** 設定キー：落ちたとみなす秒数 */
	public static final String KEY_STALE_SECONDS = "mq.stale_seconds";

	private MqConf () {}

	/**
	 * 種別ごとのスレッド数（要件 F-M-08）
	 *
	 * @param type	種別
	 * @return	スレッド数
	 */
	public static int threadCount (MqExecuteType type) {

		long count = Conf.conf().getLong(KEY_THREAD_COUNT + type.name(), type.defaultThreadCount());

		return (int) Math.max(1, count);

	}

	/**
	 * キューが空でないときの待ち（ミリ秒）
	 *
	 * @return	ミリ秒
	 */
	public static long pollMinMs () {

		return Conf.conf().getLong(KEY_POLL_MIN_MS, 10);

	}

	/**
	 * キューが空のときの待ち（ミリ秒）
	 *
	 * @return	ミリ秒
	 */
	public static long pollMaxMs () {

		return Conf.conf().getLong(KEY_POLL_MAX_MS, 1000);

	}

	/**
	 * リトライの間隔（秒）
	 *
	 * @return	秒数
	 */
	public static long retryBackoffSeconds () {

		return Conf.conf().getLong(KEY_RETRY_BACKOFF_SECONDS, 10);

	}

	/**
	 * リトライ間隔の上限（秒）
	 *
	 * @return	秒数
	 */
	public static long retryBackoffMaxSeconds () {

		return Conf.conf().getLong(KEY_RETRY_BACKOFF_MAX_SECONDS, 600);

	}

	/**
	 * 落ちたとみなす秒数
	 *
	 * @return	秒数
	 */
	public static long staleSeconds () {

		return Conf.conf().getLong(KEY_STALE_SECONDS, 600);

	}

	/**
	 * リトライまでの待ち（秒）
	 *
	 * @param attempt	何回目のリトライか（1 から）
	 * @return	秒数
	 */
	public static long backoffSeconds (int attempt) {

		long base = retryBackoffSeconds();
		long max = retryBackoffMaxSeconds();

		if (attempt <= 1) {
			return Math.min(base, max);
		}

		// 10 → 20 → 40 → 80 …（上限まで）
		long seconds = base;

		for (int i = 1; i < attempt && seconds < max; i++) {
			seconds *= 2;
		}

		return Math.min(seconds, max);

	}

}
