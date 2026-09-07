package blog;

import blog.batch.MqWorkerBatch;
import blog.batch.PostCleanupBatch;
import blog.mq.NoticeExecutor;
import io.jimble.batch.BatchExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.batch.BatchTables;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

/**
 * バッチの入口
 *
 * <pre>
 * java -cp app.jar blog.BlogBatch env=local class=blog.batch.PostCleanupBatch days=30
 * </pre>
 *
 * <p>
 * <b>起動の順番がここに全部書いてある</b>（原則1）。
 * 移送元はフレームワーク側がアプリケーションを立ち上げていたので、
 * 何がどの順で起きるかがアプリからは見えなかった。
 * </p>
 */
public class BlogBatch {

	/**
	 * エントリポイント
	 *
	 * @param args	コマンドライン引数
	 */
	public static void main (String[] args) {

		// 1. DB
		DBUtil.load(Conf.conf().config(), BlogBatch.class);

		// 2. バッチが使うテーブル
		BatchTables.install(DBUtil.getMainDB());

		// 3. MQ のテーブルと Executor（要件 F-M-07。クラスパス走査はしない）
		new MqQueue(NoticeExecutor.QUEUE_NAME).install();
		MqRegistry.add(NoticeExecutor::new);

		// 4. バッチの登録（要件 F-B-09。クラスパス走査はしない）
		BatchRegistry.add(PostCleanupBatch::new);
		BatchRegistry.add(MqWorkerBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		// 5. 実行
		BatchResult result = BatchExecutor.start(args);

		Log.info("バッチの結果: %s".formatted(result));

		DBUtil.stop();

		System.exit(result.isExecuted() ? 0 : 1);

	}

}
