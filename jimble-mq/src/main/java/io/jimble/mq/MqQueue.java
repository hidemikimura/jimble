package io.jimble.mq;

import io.jimble.core.context.MqContext;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.Tx;
import io.jimble.db.DBUtil;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.metrics.Metrics;
import io.jimble.util.thread.SleepManager;
import io.jimble.util.thread.VirtualThreadManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

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
 * 実行種別（{@link MqExecuteType}）ごとにワーカーを立てる。
 * <b>DB から取るのはキューごとに1本の取り出し役</b>で、{@code SELECT ... FOR UPDATE SKIP LOCKED} で1件ずつ取り、
 * <b>手の空いたワーカーにその場で渡す</b>（ワーカーは待っている間 DB を触らない）。
 * <b>複数のプロセスが同時に回してもよい。</b>
 * </p>
 *
 * <p>
 * かつては<b>ワーカーが1本ずつ自分で DB を見ていた</b>。何もしていないときでも、ワーカーの数だけ
 * 定期的に接続を借りるので、DB の応答が数秒遅れると<b>ワーカーの数だけ接続が同時に埋まった</b>。
 * </p>
 *
 * <p>
 * <b>先読みはしない</b>（D-35）。取り出し役が取るのは、<b>待っているワーカーがいるときだけ</b>で、
 * 取った行はすぐに渡す。止めるときに手元に溜まった行が残ることはない。
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

		Map<MqExecuteType, Handoff> handoffs = new LinkedHashMap<>();

		for (MqExecuteType type : types) {

			int threadCount = MqConf.threadCount(type);

			Log.info("MQ ワーカーを起動します: %s / %s / %d スレッド"
				.formatted(queueName, type.name(), threadCount));

			Handoff handoff = new Handoff();
			handoffs.put(type, handoff);

			for (int i = 0; i < threadCount; i++) {
				String name = "jimble-mq-%s-%s-%d".formatted(queueName, type.name(), i);
				manager.execute(() -> {
					Thread.currentThread().setName(name);
					worker(handoff, cancelOrderNotify);
				});
			}

		}

		manager.execute(() -> {
			Thread.currentThread().setName(pollerName(queueName));
			poller(handoffs, cancelOrderNotify);
		});

		return manager;

	}

	/**
	 * 取り出し役のスレッドの名前
	 *
	 * @param queueName	キューの名前
	 * @return	名前
	 */
	static String pollerName (String queueName) {

		return "jimble-mq-poll-" + queueName;

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

	 * <p>
	 * 戻すのも1回のリトライと数える。{@link MqExecutor#maxRetry()} を使い切った行は、戻さずに {@code dead} にする。
	 * </p>
	 *
	 * @return	戻した件数（{@code dead} にしたものは数えない）
	 */
	public int recoverStale () {

		int total = 0;

		for (DB db : DBUtil.getDBList()) {

			String table = db.dialect().identifier(queueName);
			String stale = db.dialect().intervalFromNow("SECOND", true);
			long staleSeconds = MqConf.stale().toSeconds();

			/*
			 * <b>戻すたびに retry_count を数え、上限を超えたら dead にする</b>（D-250）。
			 * かつては数えずに waiting へ戻すだけだったので、<b>処理するたびにプロセスを落とす行</b>
			 * （メモリを使い切らせる大きな data など）は、戻されては落とし、を際限なく繰り返した。
			 * 1行ずつ、拾ったときと同じ状態（running・同じ retry_count・古いまま）のときだけ書き換えるので、
			 * 何台かで同時に呼んでも二重には数えない
			 */
			List<Data> rows = db.selectList("""
					SELECT
						id, mq_key, retry_count
					FROM
						%s
					WHERE
						status = ?
						AND updated_at < %s
				""".formatted(table, stale)
				, MqStatus.running.name()
				, staleSeconds);

			int count = 0;

			for (Data row : rows) {

				long id = row.getLong("id");
				int retryCount = row.getInt("retry_count");
				MqExecutor executor = MqRegistry.create(queueName, row.getStringOptional("mq_key"));
				int maxRetry = executor == null ? 0 : executor.maxRetry();
				boolean dead = retryCount >= maxRetry;

				int updated = db.update("""
						UPDATE %s SET
							status = ?
							, retry_count = ?
							, log_info = ?
							, updated_at = NOW()
						WHERE
							id = ?
							AND status = ?
							AND retry_count = ?
							AND updated_at < %s
					""".formatted(table, stale)
					, dead ? MqStatus.dead.name() : MqStatus.waiting.name()
					, dead ? retryCount : retryCount + 1
					, new Data().putData("reason", "running のまま止まっていました（処理中にプロセスが落ちた）")
					, id
					, MqStatus.running.name()
					, retryCount
					, staleSeconds);

				if (updated == 0) {
					continue;
				}

				if (dead) {
					Log.error("MQ の迷子をあきらめました: %s / id=%d / %d 回目".formatted(queueName, id, retryCount + 1));
				} else {
					count++;
				}

			}

			if (count > 0) {
				Log.warn("MQ の迷子を戻しました: %s / %d 件".formatted(queueName, count));
				total += count;
			}

		}

		return total;

	}

	/**
	 * まだ処理されていない件数（要件 NF-O-04）
	 *
	 * <p>
	 * <b>呼ぶたびに SQL を1本打つ。</b>だから jimble は<b>これを勝手にメトリクスへ登録しない</b>——
	 * {@code Metrics.snapshot()} が DB を触ると、<b>DB が詰まっているときに限ってメトリクスも取れなくなる</b>。
	 * いちばん見たいときに見えないのでは意味がない（原則5）。
	 * </p>
	 *
	 * <p>
	 * 滞留数を見たいなら、<b>アプリが承知のうえで登録する</b>：
	 * </p>
	 *
	 * <pre>{@code
	 * Metrics.gauge("mq.notice.pending", () -> noticeQueue.pendingCount());
	 * }</pre>
	 *
	 * <p>
	 * SQL を打ちたくないなら、{@code mq.<キュー名>.received} と
	 * {@code mq.<キュー名>.completed} の差でおおよそは分かる（こちらは常に数えている）。
	 * </p>
	 *
	 * @return	待っている件数
	 */
	public long pendingCount () {

		long total = 0;

		for (DB db : DBUtil.getDBList()) {

			// 読めなければ例外（1.x はその DB を黙って 0 件と数えていた）
			total += db.select("SELECT count(*) AS pending FROM %s WHERE status = ?"
				.formatted(db.dialect().identifier(queueName))
				, MqStatus.waiting.name()).map(row -> row.getLong("pending")).orElse(0L);

		}

		return total;

	}

	// endregion

	// region ワーカー

	/**
	 * 取り出し役から、手の空いたワーカーへ渡す口（実行種別ごとに1つ）
	 *
	 * <p>
	 * <b>{@code idle} の許可 = 待っているワーカーの数。</b>ワーカーは待つ前に1つ足し、取り出し役は取る前に1つ引く。
	 * 引けたときだけ DB から取るので、<b>待っている人がいないのに取ることはない</b>（先読みしない）。
	 * 取れなかったら許可を戻す。
	 * </p>
	 */
	private static final class Handoff {

		/* 待っているワーカーの数 */
		final Semaphore idle = new Semaphore(0);

		/* 渡すところ（溜めない） */
		final SynchronousQueue<Claimed> queue = new SynchronousQueue<>();

	}

	/**
	 * 取った1件
	 *
	 * @param db	取った DB
	 * @param row	行
	 */
	private record Claimed (DB db, Data row) {}

	/**
	 * 取り出し役のループ（キューごとに1本）
	 *
	 * @param handoffs			実行種別ごとの渡す口
	 * @param cancelOrderNotify	中断通知
	 */
	private void poller (Map<MqExecuteType, Handoff> handoffs, CancelOrderNotify cancelOrderNotify) {

		SleepManager sleepManager = new SleepManager(MqConf.pollMin().toMillis(), MqConf.pollMax().toMillis());

		while (!cancelOrderNotify.isCancelOrder()) {

			boolean found = false;
			boolean anyIdle = false;

			for (Map.Entry<MqExecuteType, Handoff> entry : handoffs.entrySet()) {

				Handoff handoff = entry.getValue();

				for (DB db : DBUtil.getDBList()) {

					// 待っているワーカーがいなければ、DB を見にいかない
					if (!handoff.idle.tryAcquire()) {
						break;
					}

					anyIdle = true;

					Data row = claim(db, entry.getKey());

					if (row == null) {
						handoff.idle.release();
						continue;
					}

					found = true;

					/*
					 * <b>必ず渡し切る。</b>許可を引いた以上、待っているワーカーが1人いる。
					 * 取った行を捨てると running のまま残る（stale で戻るまで誰も処理しない）。
					 */
					putUninterruptibly(handoff.queue, new Claimed(db, row));

				}

			}

			if (found) {
				// 取れた。続けて見る（取れる限り、待たずに回す）
				sleepManager.reset();
			} else if (!anyIdle) {
				// 全員が処理中。DB は見ずに、誰かの手が空くのを少し待つ
				sleepManager.sleepMin();
			} else {
				// 手は空いているが、キューが空。だんだん間隔を伸ばす
				sleepManager.sleep();
			}

		}

	}

	/**
	 * ワーカーのループ
	 *
	 * @param handoff			渡す口
	 * @param cancelOrderNotify	中断通知
	 */
	private void worker (Handoff handoff, CancelOrderNotify cancelOrderNotify) {

		while (true) {

			Claimed claimed = await(handoff);

			if (claimed == null) {

				if (cancelOrderNotify.isCancelOrder()) {
					return;
				}

				continue;

			}

			/*
			 * ここで受け切る。移送元は受けていなかったので、
			 * Executor が投げた例外でワーカースレッドが静かに死んでいた。
			 */
			try {
				handle(claimed.db(), claimed.row(), cancelOrderNotify);
			} catch (Throwable ex) {
				Log.error(ex, "MQ の処理で想定外の例外: %s / id=%d".formatted(queueName, claimed.row().getLong("id")));
				fail(claimed.db(), claimed.row(), ex);
			}

		}

	}

	/**
	 * 1件渡されるのを待つ
	 *
	 * <p>
	 * 待つ前に許可を1つ足す。しばらく来なければ許可を引いて抜ける（中断を見るため）。
	 * <b>引けなかったら、取り出し役がいま取りにいっている</b>——渡されるか、許可が戻されるまで待ち直す。
	 * ここで諦めて抜けると、取り出し役が渡そうとした行を誰も受け取らない。
	 * </p>
	 *
	 * @param handoff	渡す口
	 * @return	渡された1件（しばらく来なければ null）
	 */
	private static Claimed await (Handoff handoff) {

		handoff.idle.release();

		while (true) {

			try {

				Claimed claimed = handoff.queue.poll(200, TimeUnit.MILLISECONDS);

				if (claimed != null) {
					return claimed;
				}

			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			if (handoff.idle.tryAcquire()) {
				return null;
			}

		}

	}

	/**
	 * 割り込まれても渡し切る
	 *
	 * @param queue		渡すところ
	 * @param claimed	渡すもの
	 */
	private static void putUninterruptibly (SynchronousQueue<Claimed> queue, Claimed claimed) {

		boolean interrupted = false;

		while (true) {
			try {
				queue.put(claimed);
				break;
			} catch (InterruptedException ex) {
				interrupted = true;
			}
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
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

		try (Tx tx = db.begin()) {

			/*
			 * <b>「予定なし」と「予定が来たもの」を別々に引いて、id の小さいほうを取る</b>（D-280）。
			 *
			 * 2.5.1 までは scheduled_at IS NULL OR scheduled_at &lt;= NOW() を1本で引いて id で並べていた。
			 * OR があると索引の順に読めないので、溜まった分を並べ替えるか、主キーを頭から読み飛ばすかになり、
			 * <b>1件取るたびに溜まった件数ぶん読んでいた</b>（ほかの種別が 10 万件・この種別が 10 万件溜まった表で 1 本 47ms。
			 * 分けると 0.1ms ほど）。
			 *
			 * 並べ方は製品で変える。MySQL は IS NULL のあとの id の順を索引から読めるが、
			 * PostgreSQL は「主キーを読めばすぐ見つかる」と見積もって外すので、索引の列の順そのままで並べさせる。
			 */
			boolean postgres = db.dialect() instanceof io.jimble.db.dialect.PostgreSqlDialect;
			String table = db.dialect().identifier(queueName);

			Data immediate = db.select("""
					SELECT * FROM %s
					WHERE execute_type = ? AND status = ? AND scheduled_at IS NULL
					ORDER BY %s
					LIMIT 1 FOR UPDATE SKIP LOCKED
				""".formatted(table, postgres ? "execute_type, status, scheduled_at, id" : "id")
				, type.name()
				, MqStatus.waiting.name()).orElse(null);

			/*
			 * <b>いまの時刻は DB の NOW() ではなく、この JVM の時刻を渡す</b>（D-281）。
			 * scheduled_at は put が JVM の時刻で書くので、DB の NOW() と比べると、
			 * JVM と DB の時間帯が違うとき（JST の JVM と UTC の DB など）<b>時差のぶん遅れて拾っていた</b>
			 */
			Data due = db.select("""
					SELECT * FROM %s
					WHERE execute_type = ? AND status = ? AND scheduled_at <= ?
					ORDER BY %s
					LIMIT 1 FOR UPDATE SKIP LOCKED
				""".formatted(table, postgres ? "execute_type, status, scheduled_at, id" : "scheduled_at, id")
				, type.name()
				, MqStatus.waiting.name()
				, new java.util.Date()).orElse(null);

			// 両方取れたら、先に積まれたほう（取らなかったほうの行ロックは、すぐ下のコミットで外れる）
			Data row = immediate == null ? due
				: due == null ? immediate
				: immediate.getLong("id") <= due.getLong("id") ? immediate : due;

			if (row == null) {
				// 何も取らずに抜ける（close で巻き戻る）
				return null;
			}

			db.update("UPDATE %s SET status = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName))
				, MqStatus.running.name()
				, row.getLong("id"));

			tx.commit();

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

		Metrics.count("mq.%s.received".formatted(queueName));

		/*
		 * 積んだところに繋ぐ（要件 NF-O-05）。
		 *
		 * <b>いまのスパンの子にはしない。</b>この処理を回しているのはワーカーのループで、
		 * 積んだのは別のリクエストである。行に書いてある traceparent を親にすることで、
		 * <b>「その注文を登録したリクエスト」から「何分もあとにメールを送った処理」まで</b>が
		 * 1本のトレースになる。
		 */
		Span span = Tracing.enabled()
			? Tracing.start("mq %s".formatted(queueName), SpanKind.consumer, row.getString("traceparent"), 0)
			: Span.NOOP;

		span.attribute("messaging.destination.name", queueName);
		span.attribute("messaging.operation.name", key);
		span.attribute("messaging.message.id", id);
		span.attribute("messaging.message.retry_count", retryCount);

		// メッセージ1件ごとに Context を作る（要件 F-M-01）
		try (MqContext context = new MqContext(queueName, id, retryCount + 1)) {

			context.run(() -> {

				MqStatus status;

				try {

					status = executor.execute(db, row);

				} catch (Throwable ex) {

					Log.error(ex, "MQ の処理が失敗しました: %s / %s / id=%d".formatted(queueName, key, id));
					span.error(ex);
					fail(db, row, ex);
					return;

				}

				finish(db, row, executor, normalize(status));

			});

			// 1件にかかった時間（要件 NF-O-04）
			Metrics.record("mq.%s".formatted(queueName), context.elapsed().toNanos());

		} finally {
			span.close();
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

		// どう終わったかを数える（要件 NF-O-04）
		Metrics.count("mq.%s.%s".formatted(queueName, status.name()));

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
		long waitSeconds = MqConf.backoff(nextRetry).toSeconds();

		Log.warn("MQ をやり直します: %s / id=%d / %d 回目 / %d 秒後"
			.formatted(queueName, id, nextRetry, waitSeconds));

		// 次にやる時刻も JVM の時刻で書く（取り出しと同じ時計で比べる。D-281）
		db.update("""
				UPDATE %s SET
					status = ?
					, retry_count = ?
					, scheduled_at = ?
					, log_info = ?
					, updated_at = NOW()
				WHERE
					id = ?
			""".formatted(db.dialect().identifier(queueName))
			, MqStatus.waiting.name()
			, nextRetry
			, new java.util.Date(System.currentTimeMillis() + waitSeconds * 1000)
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
