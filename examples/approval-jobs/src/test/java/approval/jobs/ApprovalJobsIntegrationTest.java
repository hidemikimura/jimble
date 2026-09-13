package approval.jobs;

import approval.jobs.batch.ArchiveChunkBatch;
import approval.jobs.batch.ReminderBatch;
import approval.jobs.mq.JobsQueue;
import approval.jobs.mq.MailExecutor;
import approval.jobs.mq.NoticeExecutor;
import approval.jobs.mq.ReportExecutor;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.AbstractChunkBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.util.thread.VirtualThreadManager;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-jobs）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * <b>ここでいちばん見たいのは「失敗する道」である。</b>
 * わざと落として、やり直されて、最後に {@code dead} になり、
 * 人が戻すところまでを実際に通す。
 * </p>
 *
 * <p>
 * 初期データは 002_seed.sql で入る（申請20件）。
 * <b>件数を当てにしているテストがある</b>ので、初期データを変えるならここも直すこと。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:approval-jobs:pgTest
 * </pre>
 */
@Tag("db")
class ApprovalJobsIntegrationTest {

	/** 初期データの申請の件数 */
	private static final int SEEDED = 20;

	/** 初期データのうち、締切が3日以内で pending のもの */
	private static final int DUE_SOON = 6;

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		Bootstrap.load();

		MqRegistry.clear();
		MqRegistry.add(NoticeExecutor::new);
		MqRegistry.add(MailExecutor::new);
		MqRegistry.add(ReportExecutor::new);

		BatchRegistry.clear();
		BatchRegistry.add(ReminderBatch::new);
		BatchRegistry.add(ArchiveChunkBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		server = JimbleServer.start(new JobsApp(), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		MqRegistry.clear();
		BatchRegistry.clear();
		DBUtil.stop();

	}

	/**
	 * テストごとにまっさらに戻す
	 *
	 * <p>
	 * <b>初期データも入れ直す。</b>書庫入れのテストが申請を消すので、
	 * 残しておくと順番によって結果が変わる。
	 * </p>
	 */
	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE %s".formatted(db.dialect().identifier(JobsQueue.NOTICE)));
		db.execute("TRUNCATE TABLE notice");
		db.execute("TRUNCATE TABLE request_archive");
		db.execute("TRUNCATE TABLE request");
		db.execute("TRUNCATE TABLE batch_history");
		db.execute("TRUNCATE TABLE batch_execute_info");

		/*
		 * マスタも作り直す。
		 *
		 * sync() は cron を上書きしない（運用が管理画面で変えた値を守るため）ので、
		 * 行が残っていると<b>コードを直してもテストが古い値を見続ける</b>。
		 */
		db.execute("TRUNCATE TABLE batch_master");
		BatchRegistry.sync(db);

		BatchExecutor.releaseAll();

		seed(db);

	}

	// region 締切バッチ（要件 F-B-03 / F-B-04 / F-B-08）

	@Test
	@DisplayName("F-B-04 締切が近い申請だけを拾って、通知を積む")
	void reminderQueuesDueSoon () {

		assertEquals(BatchResult.completed, runBatch(ReminderBatch.class));

		Data info = history(ReminderBatch.class).getDataOptional("execute_info");

		assertEquals(DUE_SOON, info.getInt("targets"), info.toString());
		assertEquals(DUE_SOON, info.getInt("queued"), info.toString());

		// 締切が先のものは拾わない
		assertEquals(DUE_SOON, queuedCount(), "締切が先の申請まで拾っている");

	}

	@Test
	@DisplayName("F-B-03 引数で「何日先まで」を変えられる")
	void reminderTakesArgs () {

		/*
		 * 既定は3日先まで（6件）。
		 * 引数で 100 日にすると、pending の12件が全部入る。
		 */
		assertEquals(BatchResult.completed, runBatch(ReminderBatch.class, "days=100"));

		Data info = history(ReminderBatch.class).getDataOptional("execute_info");

		assertEquals(100, info.getInt("days"), info.toString());
		assertEquals(12, info.getInt("targets"), info.toString());

	}

	@Test
	@DisplayName("F-B-04 cron がマスタに載り、コードの値は default_cron に入る")
	void cronIsRegistered () {

		Data master = DBUtil.getMainDB().select(
			"SELECT cron, default_cron FROM batch_master WHERE class_name = ?"
			, ReminderBatch.class.getName());

		/*
		 * 列が2つある。
		 *
		 * - default_cron … <b>コードが言っている値</b>。sync() のたびに上書きされる
		 * - cron         … <b>いま効いている値</b>。最初の1回だけ default_cron が入り、
		 *                   あとは<b>管理画面から変えたものが残る</b>（sync は触らない）
		 *
		 * 分かれているのは、<b>運用が管理画面で変えた時刻を、
		 * 次のデプロイで黙って戻さない</b>ためである。
		 * コードを直したかを見たいなら default_cron を見ること。
		 */
		assertEquals("0 9 * * *", master.getString("default_cron"), master.toString());
		assertEquals("0 9 * * *", master.getString("cron"), master.toString());

	}

	// endregion

	// region MQ の正常系と冪等性（要件 F-M-02 / F-M-05）

	@Test
	@DisplayName("F-M-02 積んだ通知が処理され、完了した行は消える")
	void noticeIsProcessed () throws Exception {

		runBatch(ReminderBatch.class);

		assertEquals(DUE_SOON, queuedCount());

		runWorkerUntil(20000, () -> noticeCount() >= DUE_SOON);

		assertEquals(DUE_SOON, noticeCount(), "通知が作られていない");

		// completed は消える。滞留は 0 になる
		assertEquals(0, queuedCount(), "完了した行が残っている");

	}

	@Test
	@DisplayName("F-M-05 同じ通知を2回積んでも、通知は1件しかできない")
	void noticeIsIdempotent () throws Exception {

		/*
		 * MQ は「1回だけ実行する」を約束しない。
		 * ワーカーが落ちれば同じ行がもう一度実行される。
		 *
		 * ここでは<b>同じ内容を2回積む</b>ことで、その状況を作っている。
		 */
		runBatch(ReminderBatch.class);
		runBatch(ReminderBatch.class);

		assertEquals(DUE_SOON * 2, queuedCount(), "2回ぶん積まれていない");

		runWorkerUntil(20000, () -> queuedCount() == 0);

		assertEquals(DUE_SOON, noticeCount(), "通知が二重にできている");
		assertEquals(0, deadCount(), "冪等の判定が失敗として扱われている");

	}

	// endregion

	// region 失敗・リトライ・デッドレター（要件 F-M-04）

	@Test
	@DisplayName("F-M-04 何回か落ちても、そのうち成功すれば行は消える")
	void mailRetriesThenSucceeds () throws Exception {

		// 1回目だけ落ちて、2回目に通る
		long id = queueMail(1);

		runWorkerUntil(30000, () -> row(id) == null);

		assertNull(row(id), "やり直されずに残っている");
		assertEquals(0, deadCount(), "1回落ちただけで dead になっている");

	}

	@Test
	@DisplayName("F-M-04 上限まで落ちたら dead になり、行は残る")
	void mailGoesToDeadLetter () throws Exception {

		// maxRetry() は 2 なので、3回目で諦める
		long id = queueMail(99);

		runWorkerUntil(30000, () -> isDead(id));

		Data row = row(id);

		assertNotNull(row, "dead の行が消えている（completed だけが消えるはず）");
		assertEquals(MqStatus.dead.name(), row.getString("status"), row.toString());

		/*
		 * <b>何回やり直したかが行に残る。</b>
		 * maxRetry() = 2 なので retry_count は 2 で止まる（3回実行して諦めた）。
		 */
		assertEquals(2, row.getInt("retry_count"), row.toString());

		// 落ちた理由も残る
		assertTrue(row.getStringOptional("log_info").contains("last_error")
			, row.getStringOptional("log_info"));

	}

	@Test
	@DisplayName("F-M-04 デッドレターを一覧できて、戻すとまた拾われる")
	void deadLetterCanBeRetried () throws Exception {

		long id = queueMail(99);

		runWorkerUntil(30000, () -> isDead(id));

		// 一覧に出る
		String deadBody = get("/dead").body();

		assertTrue(deadBody.contains("\"id\":" + id), deadBody);

		assertEquals(1, number(get("/queue").body(), "dead"), "dead の数が返っていない");

		/*
		 * 戻す。
		 *
		 * <b>retry_count も 0 に戻す</b>ところが要点である。
		 * 戻さないと、拾われた瞬間にまた上限を超えていて dead に戻る。
		 */
		HttpResponse<String> retry = post("/dead/retry", "id=" + id);

		assertEquals(200, retry.statusCode(), retry.body());

		Data row = row(id);

		assertEquals(MqStatus.waiting.name(), row.getString("status"), row.toString());
		assertEquals(0, row.getInt("retry_count"), row.toString());

		// 拾われて、また落ちて、また dead になる（中身は直していないので）
		runWorkerUntil(30000, () -> isDead(id));

		assertTrue(isDead(id), "戻したのに拾われていない");

	}

	@Test
	@DisplayName("F-M-04 積んだデータで成功と失敗を並べて動かせる")
	void successAndFailureSideBySide () throws Exception {

		long ok = queueMail(0);
		long ng = queueMail(99);

		runWorkerUntil(30000, () -> row(ok) == null && isDead(ng));

		assertNull(row(ok), "成功するはずのものが残っている");
		assertTrue(isDead(ng), "失敗するはずのものが dead になっていない");

	}

	// endregion

	// region 実行種別（要件 F-M-08）

	@Test
	@DisplayName("F-M-08 実行種別が違っても同じキューで処理される")
	void longTimeExecutorRuns () throws Exception {

		DB db = DBUtil.getMainDB();

		long id = new ReportExecutor().put(db, new Data().putData("status", "pending"));

		assertTrue(id > 0);

		runWorkerUntil(30000, () -> row(id) == null);

		assertNull(row(id), "long_time の Executor が拾われていない");

	}

	// endregion

	// region チャンクバッチ（要件 F-B-12）

	@Test
	@DisplayName("F-B-12 古い申請が、一定件数ずつ書庫へ移る")
	void archiveMovesInChunks () {

		int before = requestCount();

		assertEquals(BatchResult.completed, runBatch(ArchiveChunkBatch.class, "days=100"));

		Data info = history(ArchiveChunkBatch.class).getDataOptional("execute_info");

		/*
		 * 100 日より古い approved / rejected は5件。
		 * chunkSize() は 3 なので、2かたまりに分かれる。
		 */
		assertEquals(5, info.getInt(AbstractChunkBatch.KEY_READ), info.toString());
		assertEquals(5, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());
		assertEquals(2, info.getInt(AbstractChunkBatch.KEY_CHUNKS)
			, "1かたまりで書いている: " + info);

		assertEquals(5, archiveCount(), "書庫に入っていない");
		assertEquals(before - 5, requestCount(), "元の申請が消えていない");

	}

	@Test
	@DisplayName("F-B-12 対象が無ければ何も動かない")
	void archiveWithNothingToDo () {

		// 10000 日より古いものは無い
		assertEquals(BatchResult.completed, runBatch(ArchiveChunkBatch.class, "days=10000"));

		Data info = history(ArchiveChunkBatch.class).getDataOptional("execute_info");

		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_READ), info.toString());
		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_CHUNKS), info.toString());
		assertEquals(0, archiveCount());
		assertEquals(SEEDED, requestCount(), "何も消えていないはず");

	}

	// endregion

	// region 手で触る口

	@Test
	@DisplayName("申請を足すと、締切バッチが拾う数が増える")
	void createRequestThenReminderPicksIt () throws Exception {

		HttpResponse<String> created = post("/requests", "staff_id=1&amount=5000&in_days=1");

		assertEquals(200, created.statusCode(), created.body());

		runBatch(ReminderBatch.class);

		Data info = history(ReminderBatch.class).getDataOptional("execute_info");

		assertEquals(DUE_SOON + 1, info.getInt("targets"), info.toString());

	}

	@Test
	@DisplayName("滞留数が数えられる")
	void queueStatus () throws Exception {

		assertEquals(0, number(get("/queue").body(), "pending"));

		queueMail(0);

		assertEquals(1, number(get("/queue").body(), "pending"), get("/queue").body());

	}

	@Test
	@DisplayName("知らない kind は 400。積まない")
	void unknownKind () throws Exception {

		HttpResponse<String> response = post("/notices", "kind=telepathy");

		assertEquals(400, response.statusCode(), response.body());
		assertEquals(0, queuedCount(), "400 を返したのに積まれている");

	}

	@Test
	@DisplayName("無いデッドレターを戻そうとしたら 404")
	void retryMissingDeadLetter () throws Exception {

		assertEquals(404, post("/dead/retry", "id=999999").statusCode());

	}

	@Test
	@DisplayName("F-B-11 バッチ管理画面は認証が無ければ 401")
	void batchManagerNeedsAuth () throws Exception {

		assertEquals(401, get("/jimble/batch/manager").statusCode());

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>やり直しの「間隔」は見ていない。</b>
	 *   application.pgtest.conf で 1〜2 秒に縮めてあるので、
	 *   ここで測っても設定を確かめているだけになる。
	 *   倍々になることは jimble-mq のテストが見ている
	 * - <b>スケジューラを実際に起動していない。</b>cron は batch_master に載ることだけ見た。
	 *   時刻が来たら動くことは SchedulerIntegrationTest が見ている。
	 *   ここで起動すると、同じ JVM で2つ動かせない制約とテストの順番が絡む
	 * - <b>複数台に割り振られること</b>は見ていない（1台しか起動できないため）
	 * - <b>冪等の2枚のうち、どちらが効いているかは見分けられていない。</b>
	 *   NoticeExecutor は「先に見る」と「DB の一意キー」の2枚重ねだが、
	 *   ワーカーは1件ずつ処理するので、<b>片方だけ外してもこのテストは通る</b>
	 *   （もう片方が拾うため）。両方外すと落ちる。
	 *   2つのワーカーが同時に同じ申請を掴む状況は、ここでは作れていない——
	 *   <b>それが起きたときに効くのが2枚目（一意キー）</b>である
	 * - <b>put() をトランザクションの外に出しても、このテストは通る。</b>
	 *   ロールバックで積んだ行が消えることは MqIntegrationTest が見ている
	 * - <b>ReportExecutor の実行種別ごとにスレッドが分かれていること</b>は見ていない。
	 *   外から見えるのは「拾われた」ことだけで、
	 *   <b>executeType() を short_time に変えてもこのテストは通る</b>。
	 *   種別ごとにワーカーが立つことは MqIntegrationTest が見ている
	 * - <b>中断（isCancelOrder）で ReportExecutor が止まること</b>は見ていない。
	 *   止めるにはワーカーを走らせながら別スレッドから止める必要があり、
	 *   その形は jimble-mq 側で確かめてある
	 */

	// endregion

	// region 道具

	/**
	 * 初期データを入れ直す
	 *
	 * <p>002_seed.sql と同じ内容である（変えるなら両方直すこと）。</p>
	 *
	 * @param db	DB
	 */
	private static void seed (DB db) {

		db.execute("""
				insert into request (staff_id, amount, needed_on, status, created_at) values
					  (1,  12000, current_date + 1,   'pending',  now() - interval '2 days')
					, (1,  35000, current_date + 1,   'pending',  now() - interval '2 days')
					, (2,   4800, current_date + 2,   'pending',  now() - interval '3 days')
					, (2, 120000, current_date + 2,   'pending',  now() - interval '3 days')
					, (3,   9800, current_date + 3,   'pending',  now() - interval '1 days')
					, (3,  76000, current_date + 3,   'pending',  now() - interval '1 days')
					, (1,   3200, current_date + 10,  'pending',  now())
					, (1,  58000, current_date + 14,  'pending',  now())
					, (2,  21000, current_date + 20,  'pending',  now())
					, (2,   7400, current_date + 30,  'pending',  now())
					, (3, 150000, current_date + 45,  'pending',  now())
					, (3,   6600, current_date + 60,  'pending',  now())
					, (1,  18000, current_date - 400, 'approved', now() - interval '400 days')
					, (1,  24000, current_date - 380, 'approved', now() - interval '380 days')
					, (2,  31000, current_date - 365, 'approved', now() - interval '365 days')
					, (2,   5200, current_date - 200, 'rejected', now() - interval '200 days')
					, (3,  47000, current_date - 120, 'approved', now() - interval '120 days')
					, (3,   8800, current_date - 90,  'rejected', now() - interval '90 days')
					, (1,  63000, current_date - 30,  'approved', now() - interval '30 days')
					, (2,  11500, current_date - 10,  'approved', now() - interval '10 days')
			""");

	}

	/**
	 * ワーカーを回して、条件が満たされるまで待つ
	 *
	 * <p>
	 * <b>{@code start()} ではなく {@code startNoWait()} を使う。</b>
	 * {@code start()} は止められるまで戻らない。
	 * </p>
	 *
	 * @param timeoutMs	待つ上限
	 * @param until		これが true になるまで
	 * @throws Exception	失敗した場合
	 */
	private static void runWorkerUntil (long timeoutMs, java.util.function.BooleanSupplier until)
		throws Exception {

		Cancel cancel = new Cancel();

		VirtualThreadManager manager = new MqQueue(JobsQueue.NOTICE).startNoWait(cancel);

		assertNotNull(manager, "ワーカーが立っていない（Executor が登録されていない）");

		long limit = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < limit && !until.getAsBoolean()) {
			Thread.sleep(20);
		}

		// 止めてから確かめる。走っている最中に数えると、数が動く
		cancel.doCancel();
		manager.waitThread();

	}

	/**
	 * 外から止めるための合図
	 *
	 * <p>{@code volatile} が要る（付け忘れると止まらない）。</p>
	 */
	static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }

		@Override public void doCancel () { cancelled = true; }

	}

	/**
	 * バッチを1本動かす
	 *
	 * @param batchClass	バッチのクラス
	 * @param args			引数（{@code key=value}）
	 * @return	結果
	 */
	private static BatchResult runBatch (Class<? extends AbstractBatch> batchClass, String...args) {

		String[] all = new String[args.length + 1];

		all[0] = "class=" + batchClass.getName();
		System.arraycopy(args, 0, all, 1, args.length);

		BatchArgs batchArgs = BatchExecutor.parseArgs(all);

		return BatchExecutor.execute(batchArgs);

	}

	/**
	 * 最新の履歴
	 *
	 * @param batchClass	バッチのクラス
	 * @return	履歴
	 */
	private static Data history (Class<? extends AbstractBatch> batchClass) {

		return DBUtil.getMainDB().select(
			"SELECT * FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
			, batchClass.getName());

	}

	/**
	 * キューの1行
	 *
	 * @param id	ID
	 * @return	行（無ければ null）
	 */
	private static Data row (long id) {

		DB db = DBUtil.getMainDB();

		return db.select("SELECT * FROM %s WHERE id = ?"
			.formatted(db.dialect().identifier(JobsQueue.NOTICE)), id);

	}

	/**
	 * dead になっているか
	 *
	 * @param id	ID
	 * @return	dead の場合 = true
	 */
	private static boolean isDead (long id) {

		Data row = row(id);

		return row != null && MqStatus.dead.name().equals(row.getString("status"));

	}

	/**
	 * メールを1件積む
	 *
	 * @param failUntil	何回目まで落とすか
	 * @return	キューID
	 */
	private static long queueMail (int failUntil) {

		long id = new MailExecutor().put(DBUtil.getMainDB(), new Data()
			.putData("to", "hanako@example.co.jp")
			.putData("fail_until", failUntil));

		assertTrue(id > 0, "積めていない");

		return id;

	}

	/**
	 * キューに残っている数（dead を除く）
	 *
	 * @return	件数
	 */
	private static int queuedCount () {

		return count("SELECT COUNT(1) AS cnt FROM %s WHERE status <> '%s'"
			.formatted(DBUtil.getMainDB().dialect().identifier(JobsQueue.NOTICE)
				, MqStatus.dead.name()));

	}

	/**
	 * dead の数
	 *
	 * @return	件数
	 */
	private static int deadCount () {

		return count("SELECT COUNT(1) AS cnt FROM %s WHERE status = '%s'"
			.formatted(DBUtil.getMainDB().dialect().identifier(JobsQueue.NOTICE)
				, MqStatus.dead.name()));

	}

	/**
	 * 通知の数
	 *
	 * @return	件数
	 */
	private static int noticeCount () {

		return count("SELECT COUNT(1) AS cnt FROM notice");

	}

	/**
	 * 申請の数
	 *
	 * @return	件数
	 */
	private static int requestCount () {

		return count("SELECT COUNT(1) AS cnt FROM request");

	}

	/**
	 * 書庫の数
	 *
	 * @return	件数
	 */
	private static int archiveCount () {

		return count("SELECT COUNT(1) AS cnt FROM request_archive");

	}

	/**
	 * 数える
	 *
	 * @param sql	SQL
	 * @return	件数
	 */
	private static int count (String sql) {

		Data row = DBUtil.getMainDB().select(sql);

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * GET する
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * POST する
	 *
	 * @param path	パス
	 * @param body	本文
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (String path, String body) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * JSON から数を1つ取る
	 *
	 * @param body	本文
	 * @param name	名前
	 * @return	数
	 */
	private static long number (String body, String name) {

		Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("%s が返っていません: %s".formatted(name, body));
		}

		return Long.parseLong(matcher.group(1));

	}

	// endregion

}
