package blog.batch;

import blog.mq.NoticeExecutor;
import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.mq.MqQueue;
import io.jimble.util.log.Log;

/**
 * MQ を回すバッチ
 *
 * <pre>
 * java -cp app.jar blog.BlogBatch env=local class=blog.batch.MqWorkerBatch run_seconds=50
 * </pre>
 *
 * <p>
 * <b>{@code AbstractBatch} は中断通知でもある</b>ので、そのまま
 * {@link MqQueue#start} に渡せる。管理画面やスケジューラからバッチを止めれば、
 * MQ のワーカーも止まる。
 * </p>
 */
public class MqWorkerBatch extends AbstractBatch {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String batchName () {

		return "MQ ワーカー";

	}

	/*
	 * cron は入れない。
	 *
	 * スケジューラ（BlogScheduler）を動かしているなら、
	 * MQ はスケジューラのプロセスが直接回すのでこのバッチは要らない。
	 * スケジューラを置かない構成のときに、cron から叩くためのもの。
	 */

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute (BatchArgs args) {

		long runSeconds = args.cliArgs().containsKey("run_seconds")
			? args.cliArgs().getLong("run_seconds")
			: 50;

		long deadline = System.currentTimeMillis() + runSeconds * 1000;

		/*
		 * 「時間が来たら止まる」と「バッチが中断されたら止まる」を重ねる。
		 *
		 * ここで doCancel() を呼ばないのが要点である。呼ぶと
		 * バッチ履歴が canceled になり、時間どおりに終わっただけなのに
		 * 異常終了に見える。
		 */
		CancelOrderNotify stopper = new CancelOrderNotify() {

			@Override
			public boolean isCancelOrder () {
				return System.currentTimeMillis() >= deadline || MqWorkerBatch.this.isCancelOrder();
			}

			@Override
			public void doCancel () {
				MqWorkerBatch.this.doCancel();
			}

		};

		Log.info("MQ を %d 秒回します".formatted(runSeconds));

		new MqQueue(NoticeExecutor.QUEUE_NAME).start(stopper);

	}

}
