package blog;

import blog.batch.MqWorkerBatch;
import blog.batch.PostCleanupBatch;
import blog.mq.NoticeExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.util.conf.Conf;

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

		// 1. DB
		DBUtil.load(Conf.conf().config(), BlogScheduler.class);

		// 2. テーブル
		BatchTables.install(DBUtil.getMainDB());

		MqQueue noticeQueue = new MqQueue(NoticeExecutor.QUEUE_NAME);
		noticeQueue.install();

		// 3. 登録（要件 F-B-09 / F-M-07。クラスパス走査はしない）
		MqRegistry.add(NoticeExecutor::new);

		BatchRegistry.add(PostCleanupBatch::new);
		BatchRegistry.add(MqWorkerBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		// 4. スケジューラ（アプリの MQ も一緒に回す）
		new DbScheduler().start(noticeQueue);

		DBUtil.stop();

	}

}
