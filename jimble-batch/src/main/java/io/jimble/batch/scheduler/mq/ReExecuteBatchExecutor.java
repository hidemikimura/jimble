package io.jimble.batch.scheduler.mq;

import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * 「この履歴と同じ引数でもう一度」（要件 F-B-11）
 *
 * <pre>
 * new ReExecuteBatchExecutor().request(db, batchHistoryId);
 * </pre>
 */
public class ReExecuteBatchExecutor extends MqExecutor {

	/** キー */
	public static final String KEY = "scheduler.batch.re_execute";

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
	 */
	@Override
	public int maxRetry () {

		return 0;

	}

	/**
	 * 依頼する
	 *
	 * @param db		DB
	 * @param batchId	バッチ履歴ID
	 * @return	キューID
	 */
	public long request (DB db, long batchId) {

		return put(db, new Data().putData("batch_id", batchId));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		long batchId = row.getDataOptional("data").getLong("batch_id");

		DbScheduler scheduler = DbScheduler.current();

		if (scheduler == null) {
			Log.warn("スケジューラが動いていません: batch_id=%d".formatted(batchId));
			return MqStatus.error;
		}

		return scheduler.reRunBatch(batchId) ? MqStatus.completed : MqStatus.error;

	}

}
