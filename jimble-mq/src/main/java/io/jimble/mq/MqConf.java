package io.jimble.mq;

import java.time.Duration;
import java.util.List;

import com.typesafe.config.ConfigUtil;
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
 *   poll_min           = 10ms   # キューが空のときの待ちの最初（ワーカーの手が空いたら待たずに取りにいく）
 *   poll_max           = 1s     # キューが空のときの待ちの上限（だんだん伸びる）
 *   retry_backoff      = 10s    # リトライの間隔（回を追うごとに倍）
 *   retry_backoff_max  = 10m
 *   stale              = 10m    # これだけ running のままなら落ちたとみなす
 * }
 * </pre>
 */
public final class MqConf {

	/** 設定キーの前置き：種別ごとのスレッド数 */
	public static final String KEY_THREAD_COUNT = "mq.thread_count.";

	/** 設定キー：キューが空のときの待ちの最初 */
	public static final String KEY_POLL_MIN = "mq.poll_min";

	/** 設定キー：キューが空のときの待ちの上限 */
	public static final String KEY_POLL_MAX = "mq.poll_max";

	/** 設定キー：リトライの間隔 */
	public static final String KEY_RETRY_BACKOFF = "mq.retry_backoff";

	/** 設定キー：リトライ間隔の上限 */
	public static final String KEY_RETRY_BACKOFF_MAX = "mq.retry_backoff_max";

	/** 設定キー：落ちたとみなす時間 */
	public static final String KEY_STALE = "mq.stale";

	/** 仕事のある印（off / memory / redis） */
	public static final String KEY_HINT = "mq.hint";

	/** 印を使うとき、全部のデータソースを回る間隔 */
	public static final String KEY_SWEEP_INTERVAL = "mq.sweep_interval";

	/** キューごとの設定（{@code mq.queues.<キュー名>.data_sources} など） */
	public static final String KEY_QUEUES = "mq.queues";

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
	 * キューが空のときの待ちの最初
	 *
	 * @return	待ち
	 */
	public static Duration pollMin () {

		return Conf.conf().getDuration(KEY_POLL_MIN, Duration.ofMillis(10));

	}

	/**
	 * キューが空のときの待ちの上限
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

	/**
	 * そのキューを置くデータソース（{@code mq.queues.<キュー名>.data_sources}）
	 *
	 * <p>
	 * 書いていなければ空（{@code db.<名前>.mq = true} の全部に置く）。
	 * 書けば、その中で {@code db.<名前>.mq = true} のものだけに置く。
	 * </p>
	 *
	 * @param queueName	キュー名
	 * @return	データソース名（書いていなければ空）
	 * @since 2.7.8
	 */
	public static List<String> dataSources (String queueName) {

		String key = ConfigUtil.joinPath("mq", "queues", queueName, "data_sources");

		return Conf.conf().config().hasPath(key) ? List.copyOf(Conf.conf().config().getStringList(key)) : List.of();

	}

	/**
	 * 「仕事がある」という印の置き場（{@code mq.hint}。2.7.8）
	 */
	public enum Hint {

		/** 使わない（毎周すべてのデータソースを見る。これまでどおり） */
		off,

		/** 同じプロセスの中だけ */
		memory,

		/** 同じプロセスの中と Redis */
		redis

	}

	/**
	 * 「仕事がある」という印の置き場
	 *
	 * <p>既定は {@link Hint#off}（これまでどおり、毎周すべてのデータソースを見る）。</p>
	 *
	 * @return	置き場
	 * @throws MqException	知らない値の場合
	 * @since 2.7.8
	 */
	public static Hint hint () {

		String value = Conf.conf().getString(KEY_HINT, Hint.off.name());

		try {
			return Hint.valueOf(value);
		} catch (IllegalArgumentException ex) {
			throw new MqException("%s は off / memory / redis のどれかで書いてください: %s".formatted(KEY_HINT, value), ex);
		}

	}

	/**
	 * 印を使うとき、全部のデータソースを回る間隔
	 *
	 * <p>
	 * 印の無い行（別のプロセスが時刻を付けて積んだもの・印が落ちたもの）は、遅くともこの間隔で拾う。既定は 10 秒。
	 * </p>
	 *
	 * @return	間隔
	 * @since 2.7.8
	 */
	public static Duration sweepInterval () {

		return Conf.conf().getDuration(KEY_SWEEP_INTERVAL, Duration.ofSeconds(10));

	}

}
