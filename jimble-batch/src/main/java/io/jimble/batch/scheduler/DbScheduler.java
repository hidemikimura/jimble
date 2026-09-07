package io.jimble.batch.scheduler;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchConf;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.batch.scheduler.mq.ExecuteBatchExecutor;
import io.jimble.batch.scheduler.mq.ReExecuteBatchExecutor;
import io.jimble.batch.scheduler.mq.SchedulerQueue;
import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.thread.VirtualThreadManager;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DB スケジューラ（要件 F-B-04 / F-B-10）
 *
 * <p>
 * <b>DB だけで動く。Redis は要らない</b>（要件 F-B-10）。
 * </p>
 *
 * <pre>
 * public static void main (String[] args) {
 *
 *     DBUtil.load(Conf.conf().config(), App.class);
 *     BatchTables.install(DBUtil.getMainDB());
 *
 *     BatchRegistry.add(RssFetchBatch::new);
 *     BatchRegistry.sync(DBUtil.getMainDB());
 *
 *     new DbScheduler().start();   // 止められるまで動き続ける
 * }
 * </pre>
 *
 * <h2>やっていること</h2>
 * <ol>
 *   <li>{@code batch_master} を定期的に読み直す（{@code cron} が入っていて有効なもの）</li>
 *   <li>cron の次回時刻を過ぎたバッチを走らせる</li>
 *   <li><b>「いま動かして」の依頼を MQ で受ける</b>（管理画面から。要件 F-B-11）</li>
 *   <li>ハートビートを打つ（{@link SchedulerControl#isRunning()} で見える）</li>
 *   <li>{@link SchedulerControl#disable()} されたら自分から降りる</li>
 * </ol>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>すべて {@code static} だった。</b>2回 {@code start()} を呼べば
 *       スケジューラもフックも二重に登録される。インスタンスにした</li>
 *   <li><b>停止フラグが volatile でなかった。</b>
 *       {@code isExit} / {@code isShutdown} はシャットダウンフックのスレッドが書き、
 *       <b>MQ のワーカースレッドが読む。</b>止めても止まらないことがある</li>
 *   <li><b>やり直し（{@code reExecuteBatch}）が引数を復元できていなかった。</b>
 *       {@code batch_history} の行から {@code getDataOptional("args")} を読んでいたが、
 *       <b>その列は存在しない</b>（引数は {@code execute_info} の中にある）。
 *       いつも空の引数でやり直していた</li>
 *   <li><b>クラス名から {@code Class.forName} でバッチを作っていた</b>（要件 F-B-09）。
 *       {@link BatchRegistry} から引く</li>
 *   <li><b>cron を 100 ミリ秒ごとに確かめていた。</b>cron は分単位なので、
 *       1分に 600 回見る意味がない。既定を1秒にした（設定で変えられる）</li>
 *   <li><b>スケジューラ ID が起動のたびに変わる乱数だった。</b>
 *       これは {@code batch_execute_info.scheduler_id} に入り、
 *       <b>インスタンス間の割り振りのキーになる</b>（{@code AbstractBatch} 参照）。
 *       {@link BatchConf#schedulerId()}（既定はホスト名）に揃えた</li>
 *   <li><b>消えたバッチの cron 情報が残り続けていた</b>（{@code cronExecutionMap} を掃除していない）</li>
 * </ol>
 */
public final class DbScheduler implements CancelOrderNotify {

	/* いま動いているスケジューラ（MQ の Executor から引く） */
	private static final AtomicReference<DbScheduler> CURRENT = new AtomicReference<>();

	/* 識別子 */
	private final String schedulerId;

	/* 止めるか */
	private volatile boolean stopping = false;

	/* 完全に止まったか */
	private volatile boolean stopped = false;

	/* 定期処理 */
	private ScheduledExecutorService ticker;

	/* バッチを走らせるスレッド（要るときに作る。start() しなくても tick() を試せる） */
	private VirtualThreadManager executeThreads;

	/* MQ のスレッド */
	private VirtualThreadManager mqThreads;

	/* 対象のバッチ（cron つき） */
	private volatile List<ScheduledBatch> batches = List.of();

	/* 次に動く時刻（クラス名 → 時刻） */
	private final Map<String, ZonedDateTime> nextExecutions = new LinkedHashMap<>();

	/**
	 * コンストラクタ
	 */
	public DbScheduler () {

		this(BatchConf.schedulerId());

	}

	/**
	 * コンストラクタ
	 *
	 * @param schedulerId	識別子
	 */
	public DbScheduler (String schedulerId) {

		this.schedulerId = schedulerId;

	}

	/**
	 * いま動いているスケジューラ
	 *
	 * @return	スケジューラ（動いていなければ null）
	 */
	public static DbScheduler current () {

		return CURRENT.get();

	}

	/**
	 * 識別子
	 *
	 * @return	識別子
	 */
	public String schedulerId () {

		return schedulerId;

	}

	// region 起動と停止

	/**
	 * 動かす（止められるまで戻らない）
	 *
	 * @param extraQueues	一緒に回す MQ（無くてもよい）
	 */
	public void start (MqQueue...extraQueues) {

		if (!SchedulerControl.isEnabled()) {
			Log.warn("スケジューラは止められています: %s".formatted(SchedulerControl.KEY_ENABLED));
			return;
		}

		if (!CURRENT.compareAndSet(null, this)) {
			throw new IllegalStateException("スケジューラはすでに動いています");
		}

		Thread hook = new Thread(this::shutdown, "jimble-scheduler-shutdown");

		try {

			// 先に「動いている」ことを出す。テーブルを作るのに少し時間がかかる
			SchedulerControl.heartbeat(schedulerId);

			installSchedulerQueue();

			executeThreads();

			Runtime.getRuntime().addShutdownHook(hook);

			ticker = Executors.newSingleThreadScheduledExecutor(
				runnable -> Thread.ofPlatform().name("jimble-scheduler").unstarted(runnable));

			// batch_master を読み直す＋ハートビート
			ticker.scheduleWithFixedDelay(this::reload
				, 0, SchedulerConf.reloadIntervalMs(), TimeUnit.MILLISECONDS);

			// cron
			ticker.scheduleWithFixedDelay(() -> tick(ZonedDateTime.now())
				, SchedulerConf.tickIntervalMs(), SchedulerConf.tickIntervalMs(), TimeUnit.MILLISECONDS);

			// 「いま動かして」を受ける口（要件 F-B-11）
			mqThreads = startQueues(extraQueues);

			Log.info("スケジューラを開始しました: %s".formatted(schedulerId));

			waitUntilStopped();

		} finally {

			try {
				Runtime.getRuntime().removeShutdownHook(hook);
			} catch (IllegalStateException ignore) {
				// もう終了処理に入っている
			}

			shutdown();

		}

	}

	/**
	 * 止める
	 */
	public void stop () {

		stopping = true;

	}

	/**
	 * 止まるまで待つ
	 */
	private void waitUntilStopped () {

		while (!stopping) {

			if (!SchedulerControl.isEnabled()) {
				Log.info("スケジューラが止められました: %s".formatted(schedulerId));
				stopping = true;
				break;
			}

			try {
				Thread.sleep(SchedulerConf.exitCheckMs());
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				stopping = true;
			}

		}

	}

	/**
	 * 後始末
	 */
	private synchronized void shutdown () {

		if (stopped) {
			return;
		}

		stopping = true;

		if (ticker != null) {
			ticker.shutdownNow();
		}

		if (mqThreads != null) {
			mqThreads.waitThread();
		}

		if (executeThreads != null) {
			executeThreads.waitThread();
		}

		SchedulerControl.clearHeartbeat(schedulerId);

		CURRENT.compareAndSet(this, null);

		stopped = true;

		Log.info("スケジューラを終了しました: %s".formatted(schedulerId));

	}

	// endregion

	// region MQ（要件 F-B-11）

	/**
	 * スケジューラ用のキューを用意する
	 */
	private void installSchedulerQueue () {

		SchedulerQueue.queue().install();

		if (MqRegistry.create(SchedulerQueue.name(), ExecuteBatchExecutor.KEY) == null) {
			MqRegistry.add(ExecuteBatchExecutor::new);
		}

		if (MqRegistry.create(SchedulerQueue.name(), ReExecuteBatchExecutor.KEY) == null) {
			MqRegistry.add(ReExecuteBatchExecutor::new);
		}

	}

	/**
	 * バッチを走らせるスレッド
	 *
	 * @return	スレッド管理
	 */
	private synchronized VirtualThreadManager executeThreads () {

		if (executeThreads == null) {

			int threads = SchedulerConf.executeThreads();

			executeThreads = threads > 0 ? new VirtualThreadManager(threads) : new VirtualThreadManager();

		}

		return executeThreads;

	}

	/**
	 * MQ を回す
	 *
	 * @param extraQueues	一緒に回す MQ
	 * @return	スレッド管理
	 */
	private VirtualThreadManager startQueues (MqQueue...extraQueues) {

		VirtualThreadManager manager = new VirtualThreadManager();

		manager.execute(() -> SchedulerQueue.queue().start(this));

		if (extraQueues != null) {
			for (MqQueue queue : extraQueues) {
				manager.execute(() -> queue.start(this));
			}
		}

		return manager;

	}

	// endregion

	// region cron（要件 F-B-04）

	/**
	 * {@code batch_master} を読み直す
	 */
	void reload () {

		if (stopping) {
			return;
		}

		SchedulerControl.heartbeat(schedulerId);

		DB db = DBUtil.getMainDB();

		List<Data> rows = db.selectList("""
				SELECT
					class_name
					, name
					, cron
				FROM
					batch_master
				WHERE
					status = ?
					AND is_enable_scheduler = 1
					AND cron IS NOT NULL
					AND cron <> ''
				ORDER BY
					class_name
			"""
			, BatchMasterStatus.enable.name());

		if (db.isError() || rows == null) {
			Log.error("バッチマスタを読めませんでした: %s".formatted(db.getError()));
			return;
		}

		List<ScheduledBatch> loaded = new ArrayList<>();

		for (Data row : rows) {

			String className = row.getStringOptional("class_name");
			CronSchedule cron = CronSchedule.parse(row.getStringOptional("cron"));

			if (cron == null) {
				Log.warn("cron を読めませんでした: %s / %s".formatted(className, row.getStringOptional("cron")));
				continue;
			}

			loaded.add(new ScheduledBatch(className, cron));

		}

		batches = List.copyOf(loaded);

		// 消えたバッチの記録を落とす（移送元は残り続けていた）
		synchronized (nextExecutions) {
			nextExecutions.keySet().removeIf(className ->
				loaded.stream().noneMatch(batch -> batch.className().equals(className)));
		}

	}

	/**
	 * cron を確かめて、時刻が来たものを走らせる
	 *
	 * <p>
	 * <b>時刻を引数で受ける。</b>こうしておくと、実時間を待たずに確かめられる。
	 * </p>
	 *
	 * @param now	いまの時刻
	 * @return	走らせた件数
	 */
	int tick (ZonedDateTime now) {

		if (stopping) {
			return 0;
		}

		int fired = 0;

		for (ScheduledBatch batch : batches) {

			ZonedDateTime next;

			synchronized (nextExecutions) {
				next = nextExecutions.get(batch.className());
			}

			if (next == null) {

				// 初めて見たものは、次の時刻を控えるだけ（起動と同時には走らせない）
				ZonedDateTime first = batch.cron().nextExecution(now);

				if (first != null) {
					synchronized (nextExecutions) {
						nextExecutions.put(batch.className(), first);
					}
				}

				continue;

			}

			if (now.toEpochSecond() < next.toEpochSecond()) {
				continue;
			}

			ZonedDateTime following = batch.cron().nextExecution(now);

			synchronized (nextExecutions) {
				if (following != null) {
					nextExecutions.put(batch.className(), following);
				} else {
					nextExecutions.remove(batch.className());
				}
			}

			fired++;

			executeThreads().execute(() -> execute(batch.className(), batch.cron().expression(), false, null));

		}

		return fired;

	}

	// endregion

	// region 走らせる

	/**
	 * バッチを走らせる（管理画面などから）
	 *
	 * @param className	クラス名
	 * @return	受け付けた場合 = true
	 */
	public boolean runBatch (String className) {

		if (stopping || CURRENT.get() != this) {
			return false;
		}

		if (BatchRegistry.create(className) == null) {
			Log.error("バッチが登録されていません: %s".formatted(className));
			return false;
		}

		executeThreads().execute(() -> execute(className, null, true, null));

		return true;

	}

	/**
	 * 履歴と同じ引数でもう一度走らせる
	 *
	 * @param batchId	バッチ履歴ID
	 * @return	受け付けた場合 = true
	 */
	public boolean reRunBatch (long batchId) {

		if (stopping || CURRENT.get() != this) {
			return false;
		}

		Data history = DBUtil.getMainDB().select(
			"SELECT class_name, execute_info FROM batch_history WHERE id = ?", batchId);

		if (history == null) {
			Log.error("バッチ履歴がありません: id=%d".formatted(batchId));
			return false;
		}

		String className = history.getStringOptional("class_name");

		if (BatchRegistry.create(className) == null) {
			Log.error("バッチが登録されていません: %s".formatted(className));
			return false;
		}

		/*
		 * 引数は execute_info の中にある。
		 * 移送元は履歴の行から "args" 列を読もうとしていたが、そんな列は無い。
		 * いつも空の引数でやり直していた。
		 */
		Data executeInfo = history.getDataOptional("execute_info");

		executeThreads().execute(() -> execute(className, null, true, executeInfo));

		return true;

	}

	/**
	 * 走らせる
	 *
	 * @param className		クラス名
	 * @param cron			cron（無ければ null）
	 * @param force			マスタの状態を無視するか
	 * @param executeInfo	やり直しの元になる実行情報（無ければ null）
	 */
	private void execute (String className, String cron, boolean force, Data executeInfo) {

		AbstractBatch batch = BatchRegistry.create(className);

		if (batch == null) {
			Log.error("バッチが登録されていません: %s".formatted(className));
			return;
		}

		BatchArgs args = new BatchArgs();

		args.env = Conf.env();
		args.className = className;
		args.fromScheduler = true;
		args.schedulerId = schedulerId;
		args.cron = cron;
		args.forceExecute = force;

		if (executeInfo != null) {
			args.cliArgs = executeInfo.getDataOptional("args");
			Data settings = executeInfo.getDataOptional("settings");
			args.settings = settings.isEmpty() ? null : settings;
		}

		BatchResult result = batch.run(args, this);

		if (result.isSkipped()) {
			Log.info("スケジューラ: バッチを実行しませんでした: %s / %s".formatted(className, result));
		}

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCancelOrder () {

		return stopping || stopped;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doCancel () {

		stopping = true;

	}

	/**
	 * cron つきのバッチ
	 *
	 * @param className	クラス名
	 * @param cron		cron
	 */
	record ScheduledBatch (String className, CronSchedule cron) {}

}
