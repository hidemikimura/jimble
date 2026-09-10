package approval.jobs.batch;

import approval.jobs.mq.NoticeExecutor;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.List;

/**
 * 締切が近い申請の通知を積む
 *
 * <h2>ここで見せたいこと</h2>
 * <ul>
 *   <li><b>cron で回る</b>（要件 F-B-04）。毎朝9時</li>
 *   <li><b>引数を受け取る</b>（要件 F-B-03）。{@code days=5} で「何日先まで」を変えられる</li>
 *   <li><b>設定を持てる</b>。引数が無ければマスタの設定（{@code defaultBatchSettings}）を使う</li>
 *   <li><b>長い処理は中断を見る</b>（要件 F-B-06）</li>
 *   <li><b>積むのはトランザクションの中</b>（要件 F-M-03）</li>
 * </ul>
 *
 * <h2>キューに積むのを、なぜトランザクションの中でやるのか</h2>
 * <p>
 * <b>「通知を積んだのに、業務のほうが戻った」を作らないため</b>である。
 * キューは同じ DB のテーブルなので、業務の更新と同じトランザクションに入る。
 * ロールバックすれば<b>積んだ行も一緒に消える。</b>
 * </p>
 * <p>
 * 逆に、キューが別の仕組み（Redis や外部の MQ）だと、これができない。
 * <b>jimble の MQ が DB の上に乗っているのは、ここが理由である。</b>
 * </p>
 *
 * <pre>
 * java -cp app.jar approval.jobs.JobsBatch env=local class=approval.jobs.batch.ReminderBatch days=5
 * </pre>
 */
public class ReminderBatch extends AbstractBatch {

	/** 設定キー：何日先までを「締切が近い」とみなすか */
	public static final String KEY_DAYS = "days";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String batchName () {

		return "締切が近い申請の通知";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>{@code isScheduler()} は書かない。</b>
	 * あれは「これはスケジューラ自身のバッチか」であって、
	 * 「スケジューラに載せるか」ではない。true にすると
	 * <b>マスタにも履歴にも載らなくなる</b>。
	 * cron で回すのに要るのは、cron を書くことだけである。
	 * </p>
	 */
	@Override
	public String cron () {

		// 毎朝9時
		return "0 9 * * *";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data defaultBatchSettings () {

		return new Data().putData(KEY_DAYS, 3);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute (BatchArgs args) {

		long days = days(args);

		DB db = DBUtil.getMainDB();

		List<Data> targets = db.selectList("""
				SELECT
					id
					, staff_id
				FROM
					request
				WHERE
					status = 'pending'
					AND needed_on <= current_date + %s
				ORDER BY
					needed_on ASC
					, id ASC
			""".formatted(days)
		);

		int queued = 0;

		for (Data target : targets) {

			// 件数が多いこともある。切れ目ごとに中断を見る（要件 F-B-06）
			if (isCancelOrder()) {
				Log.info("中断されました: %d 件まで積みました".formatted(queued));
				break;
			}

			if (queue(db, target)) {
				queued++;
			}

		}

		executeInfo().putData("days", days);
		executeInfo().putData("targets", targets.size());
		executeInfo().putData("queued", queued);

		Log.info("締切の通知を積みました: %d 日先まで / 対象 %d 件 / 積んだ %d 件"
			.formatted(days, targets.size(), queued));

	}

	/**
	 * 何日先までか
	 *
	 * <p>
	 * <b>引数が設定より強い。</b>いつもは設定どおりに回り、
	 * 手で流すときだけ {@code days=5} と書いて変えられる。
	 * </p>
	 *
	 * @param args	引数
	 * @return	日数
	 */
	private long days (BatchArgs args) {

		String fromArgs = args.cliArgs.getStringOptional(KEY_DAYS);

		if (!fromArgs.isEmpty()) {
			return Long.parseLong(fromArgs);
		}

		return settings() == null ? 3 : settings().getLong(KEY_DAYS);

	}

	/**
	 * 1件ぶん積む
	 *
	 * @param db		DB
	 * @param target	申請
	 * @return	積めた場合 = true
	 */
	private boolean queue (DB db, Data target) {

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			long id = new NoticeExecutor().put(db, new Data()
				.putData("request_id", target.getLong("id"))
				.putData("to_staff_id", target.getLong("staff_id"))
				.putData("kind", NoticeExecutor.KIND_DUE_SOON));

			/*
			 * put() は失敗すると -1 を返す（例外は投げない）。
			 * 見ないと「積んだつもりで積んでいない」が残る。
			 */
			if (id < 0) {
				transaction.rollbackEndTransaction();
				Log.error("通知を積めませんでした: request_id=%d".formatted(target.getLong("id")));
				return false;
			}

			transaction.commitEndTransaction();

			return true;

		} catch (Exception ex) {

			Log.error(ex, "通知を積めませんでした: request_id=%d".formatted(target.getLong("id")));
			return false;

		}

	}

}
