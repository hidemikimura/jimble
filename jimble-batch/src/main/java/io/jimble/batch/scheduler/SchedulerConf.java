package io.jimble.batch.scheduler;

import io.jimble.util.conf.Conf;

/**
 * DB スケジューラの設定
 *
 * <pre>
 * scheduler {
 *   reload_interval_ms  = 10000   # batch_master を読み直す間隔
 *   tick_interval_ms    = 1000    # cron を確かめる間隔
 *   exit_check_ms       = 3000    # 止められていないか確かめる間隔
 *   execute_threads     = 10      # バッチを走らせるスレッド数（0 で無制限）
 *   queue_name          = "mq_scheduler"
 * }
 * </pre>
 */
public final class SchedulerConf {

	/** 設定キー：batch_master を読み直す間隔（ミリ秒） */
	public static final String KEY_RELOAD_INTERVAL_MS = "scheduler.reload_interval_ms";

	/** 設定キー：cron を確かめる間隔（ミリ秒） */
	public static final String KEY_TICK_INTERVAL_MS = "scheduler.tick_interval_ms";

	/** 設定キー：止められていないか確かめる間隔（ミリ秒） */
	public static final String KEY_EXIT_CHECK_MS = "scheduler.exit_check_ms";

	/** 設定キー：バッチを走らせるスレッド数 */
	public static final String KEY_EXECUTE_THREADS = "scheduler.execute_threads";

	/** 設定キー：スケジューラが使うキューの名前 */
	public static final String KEY_QUEUE_NAME = "scheduler.queue_name";

	/** 既定のキュー名 */
	public static final String DEFAULT_QUEUE_NAME = "mq_scheduler";

	private SchedulerConf () {}

	/**
	 * batch_master を読み直す間隔（ミリ秒）
	 *
	 * @return	ミリ秒
	 */
	public static long reloadIntervalMs () {

		return Conf.conf().getLong(KEY_RELOAD_INTERVAL_MS, 10000);

	}

	/**
	 * cron を確かめる間隔（ミリ秒）
	 *
	 * <p>
	 * cron は分単位なので、100 ミリ秒ごとに見る意味はない。
	 * 移送元は 100 ミリ秒だった（1分に 600 回）。
	 * </p>
	 *
	 * @return	ミリ秒
	 */
	public static long tickIntervalMs () {

		return Conf.conf().getLong(KEY_TICK_INTERVAL_MS, 1000);

	}

	/**
	 * 止められていないか確かめる間隔（ミリ秒）
	 *
	 * @return	ミリ秒
	 */
	public static long exitCheckMs () {

		return Conf.conf().getLong(KEY_EXIT_CHECK_MS, 3000);

	}

	/**
	 * バッチを走らせるスレッド数（0 で無制限）
	 *
	 * @return	スレッド数
	 */
	public static int executeThreads () {

		return (int) Conf.conf().getLong(KEY_EXECUTE_THREADS, 10);

	}

	/**
	 * スケジューラが使うキューの名前
	 *
	 * @return	キュー名
	 */
	public static String queueName () {

		return Conf.conf().getString(KEY_QUEUE_NAME, DEFAULT_QUEUE_NAME);

	}

}
