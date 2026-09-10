package approval.jobs.mq;

import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * 集計表を作る（時間がかかる）
 *
 * <h2>ここで見せたいこと：実行種別ごとにワーカーが分かれること（要件 F-M-08）</h2>
 * <p>
 * {@link MqExecuteType} は<b>「どれくらいかかるか」の申告</b>であって、優先度ではない。
 * 申告した種別ごとに<b>別々のワーカーが立つ</b>ので、
 * <b>数分かかる仕事が、数百ミリ秒の仕事を待たせない。</b>
 * </p>
 *
 * <p>
 * 同じキュー（同じテーブル）に乗っていても、拾う口が種別ごとに分かれている。
 * キューを分ける必要はない。
 * </p>
 *
 * <p>
 * スレッド数は種別ごとに既定があり（{@code long_time} は 8）、
 * {@code mq.thread_count.long_time} で変えられる。
 * このサンプルは1台で動かすので 2 に絞ってある。
 * </p>
 *
 * <p>
 * <b>長い処理の途中でも中断は見る</b>（{@code isCancelOrder()}）。
 * 見ていないと、止めても終わるまで動き続ける。
 * </p>
 */
public class ReportExecutor extends MqExecutor {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String queueName () {

		return JobsQueue.NOTICE;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String key () {

		return "report";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqExecuteType executeType () {

		return MqExecuteType.long_time;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		String status = data.getStringOptional("status");

		Data summary = db.select("""
				SELECT
					COUNT(1) AS cnt
					, COALESCE(SUM(amount), 0) AS total
				FROM
					request
				WHERE
					status = ?
			""", status);

		// 長い処理のつもり。ここでも中断は見る（見ないと止められない）
		for (int i = 0; i < 10; i++) {

			if (isCancelOrder()) {
				Log.info("集計を中断しました: status=%s".formatted(status));
				return MqStatus.cancel;
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return MqStatus.cancel;
			}

		}

		Log.info("集計しました: status=%s / %d 件 / %d 円"
			.formatted(status, summary.getInt("cnt"), summary.getLong("total")));

		/*
		 * 結果を行に書き戻す。
		 *
		 * completed を返すと行は消えるので、これは
		 * 「途中経過を残す」ためのものである（ここでは説明のために書いている）。
		 */
		updateData(db, row, data
			.putData("count", summary.getInt("cnt"))
			.putData("total", summary.getLong("total")));

		return MqStatus.completed;

	}

}
