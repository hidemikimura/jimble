package io.jimble.mq;

import java.time.Duration;
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
 *   poll_min           = 10ms   # キューが空でないときの待ち
 *   poll_max           = 1s     # キューが空のときの待ち（だんだん伸びる）
 *   retry_backoff      = 10s    # リトライの間隔（回を追うごとに倍）
 *   retry_backoff_max  = 10m
 *   stale              = 10m    # これだけ running のままなら落ちたとみなす
 * }
 * </pre>
 */
public final class MqConf {

	/** 設定キーの前置き：種別ごとのスレッド数 */
	public static final String KEY_THREAD_COUNT = "mq.thread_count.";

	/** 設定キー：キューが空でないときの待ち */
	public static final String KEY_POLL_MIN = "mq.poll_min";

	/** 設定キー：キューが空のときの待ち */
	public static final String KEY_POLL_MAX = "mq.poll_max";

	/** 設定キー：リトライの間隔 */
	public static final String KEY_RETRY_BACKOFF = "mq.retry_backoff";

	/** 設定キー：リトライ間隔の上限 */
	public static final String KEY_RETRY_BACKOFF_MAX = "mq.retry_backoff_max";

	/** 設定キー：落ちたとみなす時間 */
	public static final String KEY_STALE = "mq.stale";

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
	 * キューが空でないときの待ち
	 *
	 * @return	待ち
	 */
	public static Duration pollMin () {

		return Conf.conf().getDuration(KEY_POLL_MIN, Duration.ofMillis(10));

	}

	/**
	 * キューが空のときの待ち
	 *
	 * @return	待ち
	 */
	public static Duration pollMax () {

		return Conf.conf().getDuration(KEY_POLL_MAX, Duration.ofSeconds(1));

	}

	/**
	 * リトライの間隔
	 *
	 * @return	間隔
	 */
	public static Duration retryBackoff () {

		return Conf.conf().getDuration(KEY_RETRY_BACKOFF, Duration.ofSeconds(10));

	}

	/**
	 * リトライ間隔の上限
	 *
	 * @return	上限
	 */
	public static Duration retryBackoffMax () {

		return Conf.conf().getDuration(KEY_RETRY_BACKOFF_MAX, Duration.ofMinutes(10));

	}

	/**
	 * 落ちたとみなす時間
	 *
	 * @return	時間
	 */
	public static Duration stale () {

		return Conf.conf().getDuration(KEY_STALE, Duration.ofMinutes(10));

	}

	/**
	 * リトライまでの待ち
	 *
	 * @param attempt	何回目のリトライか（1 から）
	 * @return	待ち
	 */
	public static Duration backoff (int attempt) {

		Duration base = retryBackoff();
		Duration max = retryBackoffMax();

		if (attempt <= 1) {
			return base.compareTo(max) < 0 ? base : max;
		}

		// 10 → 20 → 40 → 80 …（上限まで）
		Duration wait = base;

		for (int i = 1; i < attempt && wait.compareTo(max) < 0; i++) {
			wait = wait.multipliedBy(2);
		}

		return wait.compareTo(max) < 0 ? wait : max;

	}

}
