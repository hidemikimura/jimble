package approval.jobs;

import approval.jobs.batch.ArchiveChunkBatch;
import approval.jobs.batch.ReminderBatch;
import approval.jobs.mq.JobsQueue;
import approval.jobs.mq.MailExecutor;
import approval.jobs.mq.NoticeExecutor;
import approval.jobs.mq.ReportExecutor;

import io.jimble.batch.BatchRegistry;
import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;

/**
 * cron を回し、キューのワーカーも回す入口
 *
 * <pre>
 * java -cp app.jar approval.jobs.JobsScheduler env=local
 * </pre>
 *
 * <h2>ここで見せたいこと</h2>
 * <ul>
 *   <li><b>スケジューラは DB で動く</b>（要件 F-B-10）。Redis は要らない</li>
 *   <li><b>アプリのキューも一緒に回せる</b>。{@code start(...)} に渡すだけ</li>
 *   <li><b>止め方は1つ</b>。スケジューラを止めれば、その下のワーカーも止まる</li>
 * </ul>
 *
 * <h2>書かなくてよいもの</h2>
 * <p>
 * スケジューラ自身のキュー（{@code mq_scheduler}）と、その Executor は
 * <b>{@code start()} が自分で用意する。</b>
 * アプリが {@code MqRegistry.add} で足すと<b>二重登録で例外になる。</b>
 * </p>
 *
 * <h2>複数台で動かすとき</h2>
 * <p>
 * そのまま何台でも起動してよい。同じバッチが同時に走る本数は
 * {@code allowConcurrentExecutionCount()} で決まり、<b>DB のテーブルで排他する。</b>
 * どの台に寄せるかも DB を見て決まる（{@code batch.scheduler_id}）。
 * </p>
 */
public class JobsScheduler {

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		MqRegistry.add(NoticeExecutor::new);
		MqRegistry.add(MailExecutor::new);
		MqRegistry.add(ReportExecutor::new);

		BatchRegistry.add(ReminderBatch::new);
		BatchRegistry.add(ArchiveChunkBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		// 止められるまで戻らない
		new DbScheduler().start(new MqQueue(JobsQueue.NOTICE));

		DBUtil.stop();

	}

}
