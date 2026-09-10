package io.jimble.batch;

import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;
import io.jimble.batch.status.BatchHistoryStatus;
import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.core.context.BatchContext;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.db.lock.DBLock;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * バッチ（要件 F-B-01〜08）
 *
 * <pre>
 * public class RssFetchBatch extends AbstractBatch {
 *
 *     &#64;Override public String batchName () { return "RSS取得"; }
 *
 *     &#64;Override public void execute (BatchArgs args) {
 *         for (Data site : sites()) {
 *             if (isCancelOrder()) { return; }   // 要件 F-B-06
 *             fetch(site);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * 登録は明示的に行う（要件 F-B-09）。{@link BatchRegistry} 参照。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>実行されなかったときに黙って {@code return} していた。</b>
 *       マスタに無い / 無効 / 同時実行数オーバー、どれでも
 *       <b>「起動したのに何も起きない」だけが残り、理由が分からない。</b>
 *       {@link BatchResult} を返してログにも出す</li>
 *   <li><b>マスタを2回引いていた。</b>{@code BatchExecutor.enableBatch()} と
 *       {@code fromBatchExecutor()} が同じ行を別々に読んでいた</li>
 *   <li><b>ハートビートのスレッドが止まる保証が無かった。</b>
 *       {@code while (!isCancelOrder())} で回しており、
 *       {@code isCancelOrder()} は<b>3秒ごとに DB を引く。</b>
 *       止め方を明示的にした</li>
 *   <li><b>シャットダウンフックを登録しっぱなしにしていた。</b>
 *       1プロセスで複数のバッチを回すと積み上がる</li>
 *   <li><b>実行情報に {@code Throwable} をそのまま入れていた。</b>
 *       これは JSON にして {@code batch_history.execute_info} に保存される。
 *       例外オブジェクトの JSON 化は中身が読めるものにならない。
 *       メッセージとスタックトレースを文字列で入れる</li>
 *   <li>識別子に {@code getCanonicalName()} を使っていた。
 *       <b>入れ子クラスでは {@code Class.forName} で読めない形</b>になり、
 *       無名クラスでは null になる。{@code getName()} を使う</li>
 * </ol>
 */
public abstract class AbstractBatch implements CancelOrderNotify {

	// region 実装するもの

	/**
	 * バッチ名（日本語）
	 *
	 * @return	バッチ名
	 */
	public abstract String batchName ();

	/**
	 * バッチ本体
	 *
	 * @param args	引数
	 */
	public abstract void execute (BatchArgs args);

	/**
	 * スケジューラ自身のバッチか
	 *
	 * <p>
	 * <b>「スケジューラに載せるか」ではない。</b>
	 * これは<b>「このバッチがスケジューラそのものか」</b>を聞いている。
	 * true にすると、そのバッチは<b>マスタにも履歴にも載らない</b>
	 * （スケジューラを自分自身で管理させないため）。
	 * </p>
	 *
	 * <p>
	 * <b>cron で回したいバッチが true にしてはいけない。</b>
	 * 名前が紛らわしく、実際に取り違えた（サンプル {@code approval-jobs} を書いたとき）。
	 * true にすると {@code BatchRegistry.sync()} が飛ばすので
	 * <b>マスタに行ができず、スケジューラからは見えないまま</b>になる。
	 * それでも手で流せば動いて履歴も残るので、<b>気づきにくい。</b>
	 * </p>
	 *
	 * <p>
	 * cron で回すのに要るのは {@link #cron()} を書くことだけである
	 * （{@link #isEnableScheduler()} は既定で true）。
	 * </p>
	 *
	 * @return	このバッチがスケジューラ自身の場合 = true（ふつうは false のまま）
	 */
	public boolean isScheduler () {

		return false;

	}

	/**
	 * スケジューラに登録してよいか
	 *
	 * <p>
	 * <b>cron で回すかどうかはこちらである</b>（{@link #isScheduler()} ではない）。
	 * 既定は true なので、{@link #cron()} を書けばスケジューラが拾う。
	 * false にすると、cron が書いてあっても回らない。
	 * </p>
	 *
	 * @return	登録する場合 = true
	 */
	public boolean isEnableScheduler () {

		return true;

	}

	/**
	 * cron（{@code 分 時 日 月 曜日}）
	 *
	 * <p>移送元は {@code getCron()}。jimble の他の API に合わせて {@code get} を外した。</p>
	 *
	 * @return	cron（無ければ空文字）
	 */
	public String cron () {

		return "";

	}

	/**
	 * 同時実行可能数（要件 F-B-05）
	 *
	 * <p>0 以下にすると上限なしになる。</p>
	 *
	 * @return	同時実行可能数
	 */
	public int allowConcurrentExecutionCount () {

		return 1;

	}

	/**
	 * 既定のバッチ設定
	 *
	 * @return	設定
	 */
	public Data defaultBatchSettings () {

		return new Data();

	}

	// endregion

	// region 状態

	/* バッチ設定 */
	private Data settings = null;

	/* バッチ履歴ID */
	private long batchId = 0;

	/* 実行情報（履歴に保存される） */
	private final Data executeInfo = new Data();

	/* 親からの中断通知 */
	private CancelOrderNotify parentNotify;

	/**
	 * バッチ設定
	 *
	 * @return	設定
	 */
	public Data settings () {

		return settings;

	}

	/**
	 * 実行情報（バッチ履歴に保存される）
	 *
	 * @return	実行情報
	 */
	public Data executeInfo () {

		return executeInfo;

	}

	/**
	 * バッチ履歴ID
	 *
	 * @return	ID（履歴を作る前は 0）
	 */
	public long batchId () {

		return batchId;

	}

	/**
	 * このバッチの識別子
	 *
	 * @return	クラス名
	 */
	public final String className () {

		return getClass().getName();

	}

	// endregion

	/**
	 * 実行する（{@link BatchExecutor} から呼ばれる）
	 *
	 * @param args			引数
	 * @param parentNotify	親からの中断通知（無ければ null）
	 * @return	結果
	 */
	public BatchResult run (BatchArgs args, CancelOrderNotify parentNotify) {

		this.parentNotify = parentNotify;

		executeInfo.putData("args", args.cliArgs);
		executeInfo.putData("uid", args.uid);
		executeInfo.putData("scheduler_id", args.schedulerId);
		executeInfo.putData("is_scheduler", isScheduler());
		executeInfo.putData("is_enable_scheduler", isEnableScheduler());

		DB db = DBUtil.getMainDB();

		if (isScheduler()) {
			// スケジューラ自身はマスタも履歴も持たない
			return executeWithHistory(db, args, false);
		}

		Data master = master(db);

		if (master == null) {
			Log.warn("バッチマスタに行がありません: %s".formatted(className()));
			return BatchResult.skipped_no_master;
		}

		this.settings = mergeSettings(
			master.getDataOptional("default_settings"), master.getDataOptional("settings"));

		if (args.settings != null) {
			this.settings = args.settings;
		}

		executeInfo.putData("settings", settings);

		if (!args.forceExecute
			&& !BatchMasterStatus.enable.name().equalsIgnoreCase(master.getStringOptional("status"))) {

			Log.warn("バッチが無効です: %s / status=%s"
				.formatted(className(), master.getStringOptional("status")));

			return BatchResult.skipped_disabled;

		}

		int allowCount = args.forceExecute ? 0 : master.getInt("allow_concurrent_execution");

		if (!registerExecuteInfo(db, args, allowCount)) {
			Log.warn("同時実行数の上限に達しています: %s / allow=%d".formatted(className(), allowCount));
			return BatchResult.skipped_concurrent;
		}

		try {

			startHeartbeat(args);

			return executeWithHistory(db, args, !args.fromScheduler);

		} finally {

			stopHeartbeat();

			db.delete("DELETE FROM batch_execute_info WHERE uid = ?", args.uid);

		}

	}

	// region マスタと実行情報

	/**
	 * マスタを読む
	 *
	 * @param db	DB
	 * @return	マスタの行（無ければ null）
	 */
	private Data master (DB db) {

		return db.select("SELECT * FROM batch_master WHERE class_name = ?", className());

	}

	/**
	 * 設定を重ねる
	 *
	 * <p>
	 * <b>移送元は {@code MapUtil.mergeData()} を使っていた。</b>
	 * あれは「深いマージ」で、<b>同じキーに値があると1つのリストにまとめる。</b>
	 * 実際に動かすと {@code default_settings.days = 30} と
	 * {@code settings.days = 30} が {@code days = [30, 30]} になり、
	 * {@code settings().getLong("days")} が壊れた。
	 * ここで欲しいのは「既定の上に、設定してある分を重ねる」なので、単純な上書きでよい。
	 * </p>
	 *
	 * @param defaults	既定の設定
	 * @param settings	マスタで設定された分
	 * @return	重ねたもの
	 */
	private static Data mergeSettings (Data defaults, Data settings) {

		Data merged = new Data();

		merged.putAllData(defaults);
		merged.putAllData(settings);

		return merged;

	}

	/**
	 * 実行情報を登録する（同時実行数の判定つき。要件 F-B-05）
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param allowCount	同時実行可能数（0 以下なら上限なし）
	 * @return	登録できた場合 = true
	 */
	private boolean registerExecuteInfo (DB db, BatchArgs args, int allowCount) {

		if (allowCount <= 0) {
			return insertExecuteInfo(db, args);
		}

		String lockKey = "batch_ace_" + className();

		DBLock.create(db, lockKey);

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			DBLock.lock(db, lockKey);

			if (runningCount(db) >= allowCount) {
				transaction.rollbackEndTransaction();
				return false;
			}

			if (!isMyTurn(db, args)) {
				transaction.rollbackEndTransaction();
				return false;
			}

			if (!insertExecuteInfo(db, args)) {
				transaction.rollbackEndTransaction();
				return false;
			}

			transaction.commitEndTransaction();

			return true;

		} catch (Exception ex) {

			Log.error(ex, "同時実行数の判定に失敗しました: %s".formatted(className()));
			return false;

		}

	}

	/**
	 * いま走っている数
	 *
	 * @param db	DB
	 * @return	件数
	 */
	private int runningCount (DB db) {

		Data row = db.select("""
				SELECT
					COUNT(1) AS cnt
				FROM
					batch_execute_info
				WHERE
					class_name = ?
					AND updated_at >= %s
			""".formatted(db.dialect().intervalFromNow("SECOND", true))
			, className()
			, BatchConf.aliveSeconds());

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * 自分の番か（インスタンス間の割り振り）
	 *
	 * <p>
	 * <b>いちばん抱えているバッチが少ないインスタンスに寄せる。</b>
	 * まだ1本も走らせていないインスタンスがあれば、そちらに譲る。
	 * </p>
	 *
	 * <p>
	 * 判定のキーは {@link BatchConf#schedulerId()} である。
	 * <b>移送元はここに MAC アドレスを使っていた</b>ので、
	 * コンテナだと起動のたびに変わり、割り振りが安定しなかった。
	 * </p>
	 *
	 * @param db	DB
	 * @param args	引数
	 * @return	実行してよい場合 = true
	 */
	private boolean isMyTurn (DB db, BatchArgs args) {

		List<Data> rows = db.selectList("""
				SELECT
					scheduler_id
					, COUNT(1) AS cnt
				FROM
					batch_execute_info
				WHERE
					updated_at >= %s
				GROUP BY
					scheduler_id
				ORDER BY
					cnt ASC
					, %s
			""".formatted(
				db.dialect().intervalFromNow("SECOND", true)
				, db.dialect().call(SqlFunction.RAND))
			, BatchConf.aliveSeconds());

		if (rows == null || rows.isEmpty()) {
			return true;
		}

		int minCount = Integer.MAX_VALUE;
		boolean found = false;

		for (Data row : rows) {

			minCount = Math.min(minCount, row.getInt("cnt"));

			if (row.getStringOptional("scheduler_id").equals(args.schedulerId)) {
				found = true;
			}

		}

		// まだ1本も走らせていないインスタンスなら、そのまま実行してよい
		if (!found) {
			return true;
		}

		for (Data row : rows) {

			if (row.getInt("cnt") == minCount
				&& row.getStringOptional("scheduler_id").equals(args.schedulerId)) {
				return true;
			}

		}

		return false;

	}

	/**
	 * 実行情報を入れる
	 *
	 * @param db	DB
	 * @param args	引数
	 * @return	入れられた場合 = true
	 */
	private boolean insertExecuteInfo (DB db, BatchArgs args) {

		db.insert("""
				INSERT INTO batch_execute_info (
					uid, scheduler_id, class_name, created_at, updated_at
				) VALUES (
					?, ?, ?, NOW(), NOW()
				)
			"""
			, args.uid
			, args.schedulerId
			, className());

		return !db.isError();

	}

	// endregion

	// region ハートビート

	/* ハートビートのスレッド */
	private volatile Thread heartbeat = null;

	/* ハートビートを止めるか */
	private volatile boolean heartbeatStopped = false;

	/* ハートビートの待ちを解く（interrupt を使わないため） */
	private volatile java.util.concurrent.CountDownLatch heartbeatWakeup;

	/**
	 * 「まだ走っている」ことを知らせ続ける
	 *
	 * @param args	引数
	 */
	private void startHeartbeat (BatchArgs args) {

		heartbeatStopped = false;
		heartbeatWakeup = new java.util.concurrent.CountDownLatch(1);

		java.util.concurrent.CountDownLatch wakeup = heartbeatWakeup;

		heartbeat = Thread.ofVirtual().name("jimble-batch-heartbeat").start(() -> {

			while (!heartbeatStopped) {

				DBUtil.getMainDB().update(
					"UPDATE batch_execute_info SET updated_at = NOW() WHERE uid = ?", args.uid);

				try {
					// 止められたらここが解ける
					if (wakeup.await(BatchConf.heartbeatSeconds(), TimeUnit.SECONDS)) {
						return;
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return;
				}

			}

		});

	}

	/**
	 * ハートビートを止める
	 *
	 * <p>
	 * <b>移送元は {@code while (!isCancelOrder())} で回していた。</b>
	 * {@code isCancelOrder()} は 3 秒ごとに DB を引くので、
	 * ハートビートのたびに<b>もう1本クエリが増える。</b>
	 * しかも「バッチが終わったから止まる」しか止め方がなかった。
	 * </p>
	 */
	private void stopHeartbeat () {

		heartbeatStopped = true;

		java.util.concurrent.CountDownLatch wakeup = heartbeatWakeup;

		if (wakeup != null) {
			wakeup.countDown();
		}

		Thread thread = heartbeat;

		if (thread != null) {

			/*
			 * interrupt では止めない。
			 *
			 * JDBC の実行中に割り込むと、そのコネクションが壊れたものとして
			 * プールから捨てられる（HikariCP が "marked as broken" を出す）。
			 * プールが小さいと、後続の処理が接続を取れなくなる。
			 * 待ちを解いて、いま出ているクエリが終わるのを待つ。
			 */
			try {
				thread.join(TimeUnit.SECONDS.toMillis(BatchConf.heartbeatSeconds()) + 1000);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

		}

		heartbeat = null;

	}

	// endregion

	// region 実行と履歴（要件 F-B-08）

	/**
	 * 履歴を残しながら実行する
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param withHook		強制終了を検知するか
	 * @return	結果
	 */
	private BatchResult executeWithHistory (DB db, BatchArgs args, boolean withHook) {

		long startTime = System.currentTimeMillis();

		BatchResult[] result = { BatchResult.completed };

		/*
		 * バッチ1回を1区間として残す（要件 NF-O-05）。
		 *
		 * <b>親は無い。</b>バッチは外から呼ばれたのではなく、時刻が来たから動いている。
		 * 中で SQL を打てば、そのスパンがこの子になる。
		 */
		Span span = Tracing.enabled()
			? Tracing.start("batch %s".formatted(batchName()), SpanKind.internal)
			: Span.NOOP;

		span.attribute("code.namespace", className());

		try (BatchContext context = new BatchContext(batchName())) {

			context.run(() -> {

				Thread hook = null;

				try {

					batchId = insertHistory(db);

					if (withHook) {
						hook = shutdownHook(db, startTime);
						Runtime.getRuntime().addShutdownHook(hook);
					}

					Log.info("バッチを開始します: %s (%s)".formatted(batchName(), className()));

					execute(args);

					batchEnded = true;

					Log.info("バッチが終わりました: %s (%s)".formatted(batchName(), className()));

					/*
					 * ここは {@code isCancelOrder()} ではなくフィールドを見る。
					 * {@code batchEnded} を立てた後なので、
					 * {@code isCancelOrder()} は必ず true を返してしまう。
					 *
					 * フィールドは「中断が指示された」ときだけ立つ。
					 * 指示元は {@link #doCancel()}、履歴の {@code cancel_status}、
					 * そして親（スケジューラ）である。
					 */
					result[0] = cancelOrder ? BatchResult.canceled : BatchResult.completed;

					finishHistory(db, result[0] == BatchResult.canceled
						? BatchHistoryStatus.canceled : BatchHistoryStatus.completed, startTime);

				} catch (Throwable ex) {

					batchEnded = true;
					result[0] = BatchResult.error;

					span.error(ex);

					Log.error(ex, "バッチが失敗しました: %s (%s)".formatted(batchName(), className()));

					putException(ex);
					finishHistory(db, BatchHistoryStatus.error, startTime);

				} finally {

					if (hook != null) {
						try {
							Runtime.getRuntime().removeShutdownHook(hook);
						} catch (IllegalStateException ignore) {
							// もう終了処理に入っている
						}
					}

				}

			});

		} finally {
			span.close();
		}

		return result[0];

	}

	/**
	 * 履歴を作る
	 *
	 * @param db	DB
	 * @return	履歴ID（作れなければ 0）
	 */
	private long insertHistory (DB db) {

		long id = db.insert("""
				INSERT INTO batch_history (
					class_name, name, status, cancel_status, execute_info, starts_at
				) VALUES (
					?, ?, ?, 0, ?, NOW()
				)
			"""
			, className()
			, batchName()
			, BatchHistoryStatus.in_process.name()
			, executeInfo);

		return db.isError() ? 0 : id;

	}

	/**
	 * 履歴を終わらせる
	 *
	 * @param db		DB
	 * @param status	ステータス
	 * @param startTime	開始時刻
	 */
	private void finishHistory (DB db, BatchHistoryStatus status, long startTime) {

		if (batchId <= 0 || sigint) {
			return;
		}

		db.update("""
				UPDATE batch_history SET
					status = ?
					, execute_info = ?
					, ends_at = NOW()
					, required_time = ?
				WHERE
					id = ?
			"""
			, status.name()
			, executeInfo
			, (System.currentTimeMillis() - startTime) / 1000
			, batchId);

	}

	/**
	 * 強制終了を検知する
	 *
	 * @param db		DB
	 * @param startTime	開始時刻
	 * @return	フック
	 */
	private Thread shutdownHook (DB db, long startTime) {

		return new Thread(() -> {

			if (batchId <= 0 || batchEnded) {
				return;
			}

			sigint = true;

			executeInfo.putData("exception", "signal shutdown");

			db.update("""
					UPDATE batch_history SET
						status = ?
						, execute_info = ?
						, ends_at = NOW()
						, required_time = ?
					WHERE
						id = ?
				"""
				, BatchHistoryStatus.sigint.name()
				, executeInfo
				, (System.currentTimeMillis() - startTime) / 1000
				, batchId);

		}, "jimble-batch-shutdown");

	}

	/**
	 * 例外を実行情報に残す
	 *
	 * <p>
	 * <b>{@code Throwable} をそのまま入れない。</b>
	 * 実行情報は JSON にして {@code batch_history.execute_info} に保存されるので、
	 * 例外オブジェクトのままだと読めるものにならない。
	 * </p>
	 *
	 * @param ex	例外
	 */
	private void putException (Throwable ex) {

		StringWriter stackTrace = new StringWriter();
		ex.printStackTrace(new PrintWriter(stackTrace));

		executeInfo.putData("exception", new Data()
			.putData("class", ex.getClass().getName())
			.putData("message", String.valueOf(ex.getMessage()))
			.putData("stack_trace", stackTrace.toString()));

	}

	// endregion

	// region 中断（要件 F-B-06）

	/* 中断が指示された */
	private volatile boolean cancelOrder = false;

	/* プロセスごと落とされた */
	private volatile boolean sigint = false;

	/* バッチ本体が終わった */
	private volatile boolean batchEnded = false;

	/* 最後に中断指示を見にいった時刻 */
	private volatile long lastCancelCheck = 0;

	/* 中断指示を見にいく排他 */
	private final ReentrantLock cancelCheckLock = new ReentrantLock();

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 長時間バッチはループの中でこれを定期的に確認して、安全に止める。
	 * DB を見にいくのは {@link BatchConf#cancelCheckSeconds()} に1回だけである。
	 * </p>
	 */
	@Override
	public boolean isCancelOrder () {

		if (batchEnded || sigint || cancelOrder) {
			return true;
		}

		/*
		 * 親（スケジューラ）が止まると言っている。
		 *
		 * <b>ここで cancelOrder を立てておく。</b>
		 * 立てずに true だけ返していたので、
		 * バッチがこれを見てループを抜けても {@code cancelOrder} は false のままで、
		 * 履歴が {@code completed} になっていた。
		 * <b>スケジューラを止めて抜けたバッチが「完了」として残る</b>ということである。
		 */
		if (parentNotify != null && parentNotify.isCancelOrder()) {
			cancelOrder = true;
			return true;
		}

		if (batchId <= 0) {
			return false;
		}

		long intervalMillis = BatchConf.cancelCheckSeconds() * 1000;

		if (System.currentTimeMillis() - lastCancelCheck <= intervalMillis) {
			return cancelOrder;
		}

		cancelCheckLock.lock();

		try {

			if (System.currentTimeMillis() - lastCancelCheck <= intervalMillis) {
				return cancelOrder;
			}

			Data row = DBUtil.getMainDB().select(
				"SELECT cancel_status FROM batch_history WHERE id = ?", batchId);

			if (row != null) {
				cancelOrder = row.getBoolean("cancel_status");
			}

			lastCancelCheck = System.currentTimeMillis();

		} finally {

			cancelCheckLock.unlock();

		}

		return cancelOrder;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doCancel () {

		cancelOrder = true;

		if (batchId <= 0) {
			return;
		}

		DBUtil.getMainDB().update(
			"UPDATE batch_history SET cancel_status = 1 WHERE id = ?", batchId);

	}

	// endregion

}
