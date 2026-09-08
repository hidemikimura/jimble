package io.jimble.mq;

import io.jimble.core.context.MqContext;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.thread.SleepManager;
import io.jimble.util.thread.VirtualThreadManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * DB をキューにした MQ（要件 F-M-01〜08）
 *
 * <pre>
 * MqQueue queue = new MqQueue("mq_main");
 *
 * // 起動時
 * queue.install();
 * MqRegistry.add(SendMailExecutor::new);
 *
 * // バッチとして回す
 * queue.start(cancelOrderNotify);
 * </pre>
 *
 * <p>
 * <b>DB キュー方式だけを実装する</b>（要件 F-M-06）。
 * 差し替えのためのインターフェースは作らない（原則4）。
 * </p>
 *
 * <h2>拾い方</h2>
 * <p>
 * 実行種別（{@link MqExecuteType}）ごとにワーカーを立て、
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} で1件ずつ取る。
 * <b>複数のプロセスが同時に回してもよい。</b>
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>Executor が投げた例外を受けていなかった。</b>
 *       ワーカーのループは {@code while (!cancel) { ... executor.execute(db, data) ... }} で、
 *       例外はそのままループの外へ抜ける。
 *       <b>そのワーカースレッドが静かに死に、拾われた行は {@code running} のまま残る。</b>
 *       スレッドが1本ずつ減っていって、最後は何も処理されなくなる</li>
 *   <li><b>リトライが無かった</b>（要件 F-M-04）。{@code error} にした行は
 *       二度と拾われない（拾うのは {@code waiting} だけ）。
 *       リトライ回数・間隔・デッドレターを入れた</li>
 *   <li><b>迷子の行を戻す仕組みが無かった。</b>
 *       プロセスが落ちれば {@code running} のまま永久に残る。
 *       {@link #recoverStale()} で戻す</li>
 *   <li><b>停止時に、先読みしてメモリに持っていた行を戻す SQL が壊れていた</b>
 *       （{@code UPDATE `%s`} の {@code %s} が置換されていなかった）。
 *       <b>止めるたびに、先読み済みの行が {@code queuing} のまま取り残されていた。</b>
 *       先読みそのものをやめた（4章の D-35）</li>
 *   <li>知らないキーの行を {@code error} にして放置していた。
 *       <b>キーが登録されていないなら何度やっても同じ</b>なので、
 *       すぐ {@code dead} にして理由を残す</li>
 * </ol>
 */
public final class MqQueue {

	/* テーブル名 */
	private final String queueName;

	/**
	 * コンストラクタ
	 *
	 * @param queueName	テーブル名
	 */
	public MqQueue (String queueName) {

		this.queueName = queueName;

	}

	/**
	 * テーブル名
	 *
	 * @return	テーブル名
	 */
	public String queueName () {

		return queueName;

	}

	/**
	 * テーブルを作る
	 */
	public void install () {

		MqTables.install(queueName);

	}

	// region 起動

	/**
	 * ワーカーを回す（終わるまで待つ）
	 *
	 * @param cancelOrderNotify	中断通知
	 */
	public void start (CancelOrderNotify cancelOrderNotify) {

		VirtualThreadManager manager = startNoWait(cancelOrderNotify);

		if (manager != null) {
			manager.waitThread();
		}

	}

	/**
	 * ワーカーを回す（待たない）
	 *
	 * @param cancelOrderNotify	中断通知
	 * @return	スレッド管理（登録が無ければ null）
	 */
	public VirtualThreadManager startNoWait (CancelOrderNotify cancelOrderNotify) {

		List<MqExecuteType> types = MqRegistry.executeTypes(queueName);

		if (types.isEmpty()) {
			Log.warn("MQ に Executor が登録されていません: %s".formatted(queueName));
			return null;
		}

		recoverStale();

		VirtualThreadManager manager = new VirtualThreadManager();

		for (MqExecuteType type : types) {

			int threadCount = MqConf.threadCount(type);

			Log.info("MQ ワーカーを起動します: %s / %s / %d スレッド"
				.formatted(queueName, type.name(), threadCount));

			for (int i = 0; i < threadCount; i++) {
				manager.execute(() -> worker(type, cancelOrderNotify));
			}

		}

		return manager;

	}

	/**
	 * 複数のキューをまとめて回す
	 *
	 * @param cancelOrderNotify	中断通知
	 * @param queues			キュー
	 */
	public static void startAll (CancelOrderNotify cancelOrderNotify, MqQueue...queues) {

		if (queues == null || queues.length == 0) {
			return;
		}

		VirtualThreadManager manager = new VirtualThreadManager();

		for (MqQueue queue : queues) {
			manager.execute(() -> queue.start(cancelOrderNotify));
		}

		manager.waitThread();

	}

	// endregion

	// region 迷子の行を戻す

	/**
	 * 落ちたプロセスが残した行を {@code waiting} に戻す
	 *
	 * <p>
	 * <b>移送元にはこれが無かった。</b>処理中にプロセスが落ちると
	 * {@code running} のまま永久に残り、誰も拾わない。
	 * </p>
	 *
	 * <p>
	 * 戻した行は<b>もう一度</b>処理される。要件 F-M-05（二重処理されうる前提）の出どころの1つ。
	 * </p>
	 *
	 * @return	戻した件数
	 */
	public int recoverStale () {

		int total = 0;

		for (DB db : DBUtil.getDBList()) {

			int count = db.update("""
					UPDATE %s SET
						status = ?
						, updated_at = NOW()
					WHERE
						status = ?
						AND updated_at < %s
				""".formatted(db.dialect().identifier(queueName)
					, db.dialect().intervalFromNow("SECOND", true))
				, MqStatus.waiting.name()
				, MqStatus.running.name()
				, MqConf.staleSeconds());

			if (count > 0) {
				Log.warn("MQ の迷子を戻しました: %s / %d 件".formatted(queueName, count));
				total += count;
			}

		}

		return total;

	}

	// endregion

	// region ワーカー

	/**
	 * ワーカーのループ
	 *
	 * @param type				実行種別
	 * @param cancelOrderNotify	中断通知
	 */
	private void worker (MqExecuteType type, CancelOrderNotify cancelOrderNotify) {

		SleepManager sleepManager = new SleepManager(MqConf.pollMinMs(), MqConf.pollMaxMs());

		while (!cancelOrderNotify.isCancelOrder()) {

			boolean found = false;

			for (DB db : DBUtil.getDBList()) {

				Data row = claim(db, type);

				if (row == null) {
					continue;
				}

				found = true;

				/*
				 * ここで受け切る。移送元は受けていなかったので、
				 * Executor が投げた例外でワーカースレッドが静かに死んでいた。
				 */
				try {
					handle(db, row, cancelOrderNotify);
				} catch (Throwable ex) {
					Log.error(ex, "MQ の処理で想定外の例外: %s / id=%d".formatted(queueName, row.getLong("id")));
					fail(db, row, ex);
				}

			}

			if (found) {
				sleepManager.sleepMin();
				sleepManager.reset();
			} else {
				sleepManager.sleep();
			}

		}

	}

	/**
	 * 1件取る
	 *
	 * @param db	DB
	 * @param type	実行種別
	 * @return	行（無ければ null）
	 */
	private Data claim (DB db, MqExecuteType type) {

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			Data row = db.select("""
					SELECT
						*
					FROM
						%s
					WHERE
						execute_type = ?
						AND status = ?
						AND (scheduled_at IS NULL OR scheduled_at <= NOW())
					ORDER BY
						id ASC
					LIMIT 1 FOR UPDATE SKIP LOCKED
				""".formatted(db.dialect().identifier(queueName))
				, type.name()
				, MqStatus.waiting.name());

			if (row == null) {
				transaction.rollbackEndTransaction();
				return null;
			}

			db.update("UPDATE %s SET status = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName))
				, MqStatus.running.name()
				, row.getLong("id"));

			transaction.commitEndTransaction();

			return row;

		} catch (Exception ex) {

			Log.error(ex, "MQ の取り出しに失敗しました: %s".formatted(queueName));
			return null;

		}

	}

	/**
	 * 1件処理する
	 *
	 * @param db				DB
	 * @param row				行
	 * @param cancelOrderNotify	中断通知
	 */
	private void handle (DB db, Data row, CancelOrderNotify cancelOrderNotify) {

		long id = row.getLong("id");
		int retryCount = row.getInt("retry_count");
		String key = row.getStringOptional("mq_key");

		MqExecutor executor = MqRegistry.create(queueName, key);

		if (executor == null) {

			/*
			 * キーが登録されていない。何度やっても同じなのでリトライしない。
			 * 移送元は error にして放置していた。
			 */
			Log.error("MQ のキーが登録されていません: %s / %s / id=%d".formatted(queueName, key, id));

			updateStatus(db, id, MqStatus.dead
				, new Data().putData("reason", "MQ のキーが登録されていません: " + key));

			return;

		}

		executor.bind(cancelOrderNotify, row);

		// メッセージ1件ごとに Context を作る（要件 F-M-01）
		try (MqContext context = new MqContext(queueName, id, retryCount + 1)) {

			context.run(() -> {

				MqStatus status;

				try {

					status = executor.execute(db, row);

				} catch (Throwable ex) {

					Log.error(ex, "MQ の処理が失敗しました: %s / %s / id=%d".formatted(queueName, key, id));
					fail(db, row, ex);
					return;

				}

				finish(db, row, executor, normalize(status));

			});

		}

	}

	/**
	 * 返ってきたステータスを整える
	 *
	 * @param status	ステータス
	 * @return	整えたもの
	 */
	private static MqStatus normalize (MqStatus status) {

		if (status == null || status == MqStatus.running) {
			// 「処理中のまま返す」は結果になっていない（移送元と同じ扱い）
			return MqStatus.error;
		}

		return status;

	}

	/**
	 * 結果を反映する
	 *
	 * @param db		DB
	 * @param row		行
	 * @param executor	Executor
	 * @param status	ステータス
	 */
	private void finish (DB db, Data row, MqExecutor executor, MqStatus status) {

		long id = row.getLong("id");

		if (status == MqStatus.completed) {
			db.delete("DELETE FROM %s WHERE id = ?".formatted(db.dialect().identifier(queueName)), id);
			return;
		}

		if (status == MqStatus.error) {
			retryOrDie(db, row, executor, null);
			return;
		}

		updateStatus(db, id, status, null);

	}

	/**
	 * 例外で終わった
	 *
	 * @param db	DB
	 * @param row	行
	 * @param ex	例外
	 */
	private void fail (DB db, Data row, Throwable ex) {

		MqExecutor executor = MqRegistry.create(queueName, row.getStringOptional("mq_key"));

		retryOrDie(db, row, executor, ex);

	}

	/**
	 * リトライするか諦めるか（要件 F-M-04）
	 *
	 * @param db		DB
	 * @param row		行
	 * @param executor	Executor（分からなければ null）
	 * @param ex		例外（無ければ null）
	 */
	private void retryOrDie (DB db, Data row, MqExecutor executor, Throwable ex) {

		long id = row.getLong("id");
		int retryCount = row.getInt("retry_count");
		int maxRetry = executor == null ? 0 : executor.maxRetry();

		Data logInfo = logInfo(row, ex);

		if (retryCount >= maxRetry) {

			Log.error("MQ をあきらめました: %s / id=%d / %d 回目".formatted(queueName, id, retryCount + 1));

			updateStatus(db, id, MqStatus.dead, logInfo);

			return;

		}

		int nextRetry = retryCount + 1;
		long waitSeconds = MqConf.backoffSeconds(nextRetry);

		Log.warn("MQ をやり直します: %s / id=%d / %d 回目 / %d 秒後"
			.formatted(queueName, id, nextRetry, waitSeconds));

		db.update("""
				UPDATE %s SET
					status = ?
					, retry_count = ?
					, scheduled_at = %s
					, log_info = ?
					, updated_at = NOW()
				WHERE
					id = ?
			""".formatted(db.dialect().identifier(queueName)
				, db.dialect().intervalFromNow("SECOND", false))
			, MqStatus.waiting.name()
			, nextRetry
			, waitSeconds
			, logInfo
			, id);

	}

	/**
	 * ステータスを書き換える
	 *
	 * @param db		DB
	 * @param id		ID
	 * @param status	ステータス
	 * @param logInfo	ログ情報（無ければ null）
	 */
	private void updateStatus (DB db, long id, MqStatus status, Data logInfo) {

		if (logInfo == null) {

			db.update("UPDATE %s SET status = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName))
				, status.name(), id);

			return;

		}

		db.update("UPDATE %s SET status = ?, log_info = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName))
			, status.name(), logInfo, id);

	}

	/**
	 * ログ情報を組み立てる
	 *
	 * @param row	行
	 * @param ex	例外（無ければ null）
	 * @return	ログ情報
	 */
	private static Data logInfo (Data row, Throwable ex) {

		Data logInfo = row.getDataOptional("log_info");

		if (ex == null) {
			return logInfo.isEmpty() ? new Data().putData("last_error", "処理が error を返しました") : logInfo;
		}

		StringWriter stackTrace = new StringWriter();
		ex.printStackTrace(new PrintWriter(stackTrace));

		return new Data()
			.putData("class", ex.getClass().getName())
			.putData("message", String.valueOf(ex.getMessage()))
			.putData("stack_trace", stackTrace.toString());

	}

	// endregion

}
