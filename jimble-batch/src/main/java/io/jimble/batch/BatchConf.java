package io.jimble.batch;

import java.time.Duration;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

import java.net.InetAddress;

/**
 * バッチの設定
 *
 * <pre>
 * batch {
 *   scheduler_id            = ""    # このインスタンスの識別子（既定：ホスト名）
 *   heartbeat               = 3s    # 実行中であることを知らせる間隔
 *   alive                   = 10s   # これだけ更新が無ければ「実行中ではない」とみなす
 *   cancel_check            = 3s    # 中断指示を見にいく間隔
 *   progress                = 5s    # チャンクバッチが進み具合を履歴に書く間隔
 *   all_stop                = 1h    # 全停止フラグが効く時間
 * }
 * </pre>
 */
public final class BatchConf {

	/** 設定キー：インスタンス識別子 */
	public static final String KEY_SCHEDULER_ID = "batch.scheduler_id";

	/** 設定キー：ハートビート間隔 */
	public static final String KEY_HEARTBEAT = "batch.heartbeat";

	/** 設定キー：生存とみなす時間 */
	public static final String KEY_ALIVE = "batch.alive";

	/** 設定キー：中断指示を見にいく間隔 */
	public static final String KEY_CANCEL_CHECK = "batch.cancel_check";

	/** 設定キー：進み具合を履歴に書く間隔 */
	public static final String KEY_PROGRESS = "batch.progress";

	/** 設定キー：全停止フラグが効く時間 */
	public static final String KEY_ALL_STOP = "batch.all_stop";

	/* ホスト名（1回だけ引く） */
	private static volatile String hostName = null;

	private BatchConf () {}

	/**
	 * このインスタンスの識別子
	 *
	 * <p>
	 * <b>移送元は MAC アドレスを使っていた。</b>
	 * コンテナでは MAC が起動のたびに変わり、しかも同じ値が別のホストで出ることもある。
	 * この値は「どのインスタンスが何本バッチを持っているか」を数えるキーなので、
	 * ぶれると同時実行数の割り振り（{@link AbstractBatch} 参照）が狂う。
	 * </p>
	 *
	 * <p>設定が無ければホスト名を使う。</p>
	 *
	 * @return	識別子
	 */
	public static String schedulerId () {

		String configured = Conf.conf().getString(KEY_SCHEDULER_ID, "");

		if (!configured.isEmpty()) {
			return configured;
		}

		return hostName();

	}

	/**
	 * ホスト名
	 *
	 * @return	ホスト名（取れなければ {@code "unknown"}）
	 */
	private static String hostName () {

		if (hostName != null) {
			return hostName;
		}

		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (Exception ex) {
			Log.warn("ホスト名を取得できませんでした。batch.scheduler_id を設定してください");
			hostName = "unknown";
		}

		return hostName;

	}

	/**
	 * ハートビート間隔
	 *
	 * @return	間隔
	 */
	public static Duration heartbeat () {

		return Conf.conf().getDuration(KEY_HEARTBEAT, Duration.ofSeconds(3));

	}

	/**
	 * 生存とみなす時間
	 *
	 * @return	時間
	 */
	public static Duration alive () {

		return Conf.conf().getDuration(KEY_ALIVE, Duration.ofSeconds(10));

	}

	/**
	 * 中断指示を見にいく間隔
	 *
	 * @return	間隔
	 */
	public static Duration cancelCheck () {

		return Conf.conf().getDuration(KEY_CANCEL_CHECK, Duration.ofSeconds(3));

	}

	/**
	 * 進み具合を履歴に書く間隔（秒）
	 *
	 * <p>
	 * {@link AbstractChunkBatch} が {@code batch_history.execute_info} を
	 * 上書きする間隔である。0 以下にすると<b>チャンクごとに毎回書く。</b>
	 * </p>
	 *
	 * @return	間隔
	 */
	public static Duration progress () {

		return Conf.conf().getDuration(KEY_PROGRESS, Duration.ofSeconds(5));

	}

	/**
	 * 全停止フラグが効く時間
	 *
	 * @return	時間
	 */
	public static Duration allStop () {

		return Conf.conf().getDuration(KEY_ALL_STOP, Duration.ofHours(1));

	}

}
