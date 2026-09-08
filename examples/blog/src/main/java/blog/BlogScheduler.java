package blog;

import blog.batch.MqWorkerBatch;
import blog.batch.PostCleanupBatch;
import blog.mq.NoticeExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;

/**
 * スケジューラの入口
 *
 * <pre>
 * java -cp app.jar blog.BlogScheduler
 * </pre>
 *
 * <p>
 * cron を持つバッチを時間どおりに動かし、あわせて MQ も回す。
 * <b>止めるときは {@code SchedulerControl.disable()}</b>（管理画面から）。
 * </p>
 */
public class BlogScheduler {

	/**
	 * エントリポイント
	 *
	 * @param args	コマンドライン引数
	 */
	public static void main (String[] args) {

		// 1. DB とテーブル（3つの入口で同じものを用意する）
		Bootstrap.load();

		MqQueue noticeQueue = new MqQueue(NoticeExecutor.QUEUE_NAME);

		// 2. 登録（要件 F-B-09 / F-M-07。クラスパス走査はしない）
		MqRegistry.add(NoticeExecutor::new);

		BatchRegistry.add(PostCleanupBatch::new);
		BatchRegistry.add(MqWorkerBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		// 3. スケジューラ（アプリの MQ も一緒に回す）
		new DbScheduler().start(noticeQueue);

		DBUtil.stop();

	}

}
