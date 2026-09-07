package io.jimble.batch.scheduler.mq;

import io.jimble.batch.scheduler.SchedulerConf;
import io.jimble.mq.MqQueue;

/**
 * スケジューラが使うキュー
 *
 * <p>
 * <b>「いま動かして」を伝えるための口</b>である。
 * 管理画面（要件 F-B-11）は Web のプロセスで動くので、
 * <b>スケジューラのプロセスに直接お願いする手段がない。</b>
 * DB のキューに1行積めば、動いているスケジューラが拾う。
 * </p>
 *
 * <p>MQ をもう1つ作らずに済むので、{@code jimble-mq} をそのまま使う（D-38）。</p>
 */
public final class SchedulerQueue {

	private SchedulerQueue () {}

	/**
	 * キューの名前
	 *
	 * @return	テーブル名
	 */
	public static String name () {

		return SchedulerConf.queueName();

	}

	/**
	 * キュー
	 *
	 * @return	キュー
	 */
	public static MqQueue queue () {

		return new MqQueue(name());

	}

}
