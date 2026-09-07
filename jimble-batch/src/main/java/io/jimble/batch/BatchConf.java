package io.jimble.batch;

import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

import java.net.InetAddress;

/**
 * バッチの設定
 *
 * <pre>
 * batch {
 *   scheduler_id            = ""    # このインスタンスの識別子（既定：ホスト名）
 *   heartbeat_seconds       = 3     # 実行中であることを知らせる間隔
 *   alive_seconds           = 10    # この秒数だけ更新が無ければ「実行中ではない」とみなす
 *   cancel_check_seconds    = 3     # 中断指示を見にいく間隔
 *   all_stop_hours          = 1     # 全停止フラグが効く時間
 * }
 * </pre>
 */
public final class BatchConf {

	/** 設定キー：インスタンス識別子 */
	public static final String KEY_SCHEDULER_ID = "batch.scheduler_id";

	/** 設定キー：ハートビート間隔（秒） */
	public static final String KEY_HEARTBEAT_SECONDS = "batch.heartbeat_seconds";

	/** 設定キー：生存とみなす秒数 */
	public static final String KEY_ALIVE_SECONDS = "batch.alive_seconds";

	/** 設定キー：中断指示を見にいく間隔（秒） */
	public static final String KEY_CANCEL_CHECK_SECONDS = "batch.cancel_check_seconds";

	/** 設定キー：全停止フラグが効く時間（時間） */
	public static final String KEY_ALL_STOP_HOURS = "batch.all_stop_hours";

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
	 * ハートビート間隔（秒）
	 *
	 * @return	秒数
	 */
	public static long heartbeatSeconds () {

		return Conf.conf().getLong(KEY_HEARTBEAT_SECONDS, 3);

	}

	/**
	 * 生存とみなす秒数
	 *
	 * @return	秒数
	 */
	public static long aliveSeconds () {

		return Conf.conf().getLong(KEY_ALIVE_SECONDS, 10);

	}

	/**
	 * 中断指示を見にいく間隔（秒）
	 *
	 * @return	秒数
	 */
	public static long cancelCheckSeconds () {

		return Conf.conf().getLong(KEY_CANCEL_CHECK_SECONDS, 3);

	}

	/**
	 * 全停止フラグが効く時間（時間）
	 *
	 * @return	時間
	 */
	public static long allStopHours () {

		return Conf.conf().getLong(KEY_ALL_STOP_HOURS, 1);

	}

}
