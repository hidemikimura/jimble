package approval.jobs;

import approval.jobs.batch.ArchiveChunkBatch;
import approval.jobs.batch.ReminderBatch;
import approval.jobs.mq.MailExecutor;
import approval.jobs.mq.NoticeExecutor;
import approval.jobs.mq.ReportExecutor;

import io.jimble.batch.BatchExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqRegistry;
import io.jimble.util.log.Log;

/**
 * バッチを1本だけ動かす入口
 *
 * <pre>
 * java -cp app.jar approval.jobs.JobsBatch env=local class=approval.jobs.batch.ReminderBatch days=5
 * java -cp app.jar approval.jobs.JobsBatch env=local class=approval.jobs.batch.ArchiveChunkBatch
 * </pre>
 *
 * <p>
 * <b>登録は明示的に行う</b>（要件 F-B-09 / F-M-07）。
 * パッケージ名を渡してクラスパスを走査する自動登録はしない。
 * {@code class=} に渡せるのは<b>ここで登録したものだけ</b>で、
 * {@code Class.forName} には渡らない。
 * </p>
 *
 * <p>
 * <b>終了コードを返す。</b>走らなかった（マスタに無い・無効・同時実行数オーバー）ときに
 * 0 を返すと、<b>cron からは成功に見える。</b>
 * </p>
 */
public class JobsBatch {

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		// MQ（バッチの中から積むので、こちらでも要る）
		MqRegistry.add(NoticeExecutor::new);
		MqRegistry.add(MailExecutor::new);
		MqRegistry.add(ReportExecutor::new);

		BatchRegistry.add(ReminderBatch::new);
		BatchRegistry.add(ArchiveChunkBatch::new);

		/*
		 * マスタを、いま登録されているものに合わせる。
		 *
		 * <b>登録を済ませてから呼ぶこと。</b>
		 * 「1つも登録されていない」は「全部消えた」として扱われるので、
		 * 登録し忘れたまま呼ぶと全部の行が nothing になる（D-104）。
		 */
		BatchRegistry.sync(DBUtil.getMainDB());

		BatchResult result = BatchExecutor.start(args);

		Log.info("バッチの結果: %s".formatted(result));

		DBUtil.stop();

		System.exit(result.isExecuted() ? 0 : 1);

	}

}
