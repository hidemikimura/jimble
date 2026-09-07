package blog.mq;

import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * 記事が登録されたことを知らせる（サンプル）
 *
 * <p>
 * <b>二重に処理されうる</b>（要件 F-M-05）。ここでは何度やっても同じになる処理にしてある。
 * </p>
 */
public class NoticeExecutor extends MqExecutor {

	/** キューのテーブル名 */
	public static final String QUEUE_NAME = "mq_blog";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String queueName () {

		return QUEUE_NAME;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String key () {

		return "notice";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqExecuteType executeType () {

		return MqExecuteType.short_time;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		Log.info("記事のお知らせ: id=%d / %s"
			.formatted(data.getLong("post_id"), data.getString("title")));

		return MqStatus.completed;

	}

}
