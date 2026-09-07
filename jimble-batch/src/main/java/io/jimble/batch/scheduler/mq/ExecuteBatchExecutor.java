package io.jimble.batch.scheduler.mq;

import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * 「このバッチをいま動かして」（要件 F-B-11）
 *
 * <pre>
 * // 管理画面から
 * new ExecuteBatchExecutor().request(db, "app.batch.RssFetchBatch");
 * </pre>
 */
public class ExecuteBatchExecutor extends MqExecutor {

	/** キー */
	public static final String KEY = "scheduler.batch.execute";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String queueName () {

		return SchedulerQueue.name();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String key () {

		return KEY;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqExecuteType executeType () {

		return MqExecuteType.short_minus_time;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>やり直しても同じ結果にならないので、リトライしない。</p>
	 */
	@Override
	public int maxRetry () {

		return 0;

	}

	/**
	 * 依頼する
	 *
	 * @param db		DB
	 * @param className	バッチのクラス名
	 * @return	キューID
	 */
	public long request (DB db, String className) {

		return put(db, new Data().putData("class_name", className));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		String className = row.getDataOptional("data").getString("class_name");

		DbScheduler scheduler = DbScheduler.current();

		if (scheduler == null) {
			Log.warn("スケジューラが動いていません: %s".formatted(className));
			return MqStatus.error;
		}

		return scheduler.runBatch(className) ? MqStatus.completed : MqStatus.error;

	}

}
