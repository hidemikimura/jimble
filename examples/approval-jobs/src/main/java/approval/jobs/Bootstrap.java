package approval.jobs;

import approval.jobs.mq.JobsQueue;

import io.jimble.batch.BatchTables;
import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.mq.MqQueue;
import io.jimble.util.conf.Conf;

/**
 * 起動のときに必ず通すところ
 *
 * <h2>入口が3つあるので、ここに集める</h2>
 * <p>
 * {@link JobsApp}（Web）/ {@link JobsBatch}（バッチ）/ {@link JobsScheduler}（cron）の
 * どれから起動しても、同じものが用意されている必要がある。
 * </p>
 * <p>
 * <b>Web でもキューのテーブルを作る。</b>
 * バッチとスケジューラだけで作っていると、まっさらな DB に Web だけ立てたとき、
 * <b>キューに積もうとした最初のリクエストが 500 になる</b>
 * （{@code examples/blog} が実際にそうなった）。
 * </p>
 */
public final class Bootstrap {

	private Bootstrap () {
	}

	/**
	 * DB とテーブルを用意する
	 */
	public static void load () {

		// マイグレーション（要件 F-G-*）。DBUtil.load より前に呼ぶ
		Migration.install();

		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException(
				"DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
		}

		// バッチのマスタと履歴（要件 F-B-08）
		BatchTables.install(DBUtil.getMainDB());

		// キュー（要件 F-M-*）
		new MqQueue(JobsQueue.NOTICE).install();

	}

}
