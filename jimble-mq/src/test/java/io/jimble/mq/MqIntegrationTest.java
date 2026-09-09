package io.jimble.mq;

import io.jimble.core.context.Context;
import io.jimble.core.context.MqContext;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.metrics.Metrics;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MQ が実 DB に対して動くことの確認（要件 F-M-01〜08）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-mq:dbTest
 * </pre>
 */
@Tag("db")
class MqIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_test";

	/* 処理された回数 */
	static final AtomicInteger DONE = new AtomicInteger();

	/* 落ちた回数 */
	static final AtomicInteger FAILED = new AtomicInteger();

	/* 処理した内容 */
	static final ConcurrentLinkedQueue<String> HANDLED = new ConcurrentLinkedQueue<>();

	/* 見えた Context */
	static final ConcurrentLinkedQueue<String> CONTEXTS = new ConcurrentLinkedQueue<>();

	/* 何回目まで落ちるか */
	static volatile int failUntil = 0;

	// region Executor

	/**
	 * ふつうに終わる
	 */
	public static class OkExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "ok"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			DONE.incrementAndGet();
			HANDLED.add(row.getDataOptional("data").getString("name"));

			// メッセージ1件ごとに Context がある（要件 F-M-01）
			MqContext context = (MqContext) Context.current();
			CONTEXTS.add("%s/%d/%d".formatted(context.queueName(), context.messageId(), context.attempt()));

			return MqStatus.completed;

		}

	}

	/**
	 * 例外を投げる（ワーカーが死なないことの確認）
	 */
	public static class ThrowExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "throw"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public int maxRetry () { return 0; }

		@Override
		public MqStatus execute (DB db, Data row) {

			FAILED.incrementAndGet();
			throw new IllegalStateException("わざと落とす");

		}

	}

	/**
	 * 何回目かまで失敗する（リトライの確認。要件 F-M-04）
	 */
	public static class FlakyExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "flaky"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public int maxRetry () { return 3; }

		@Override
		public MqStatus execute (DB db, Data row) {

			int attempt = FAILED.incrementAndGet();

			if (attempt <= failUntil) {
				return MqStatus.error;
			}

			DONE.incrementAndGet();

			return MqStatus.completed;

		}

	}

	/**
	 * いつも失敗する（デッドレターの確認）
	 */
	public static class AlwaysNgExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "ng"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public int maxRetry () { return 1; }

		@Override
		public MqStatus execute (DB db, Data row) {

			FAILED.incrementAndGet();

			return MqStatus.error;

		}

	}

	// endregion

	/**
	 * 止められる通知
	 */
	static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }
		@Override public void doCancel () { cancelled = true; }

	}

	/* キュー */
	private static MqQueue queue;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), MqIntegrationTest.class), "DB に接続できませんでした");

		queue = new MqQueue(QUEUE);
		queue.install();

	}

	@AfterAll
	static void stopDataSource () {

		MqRegistry.clear();
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("TRUNCATE TABLE %s".formatted(quoted()));

		MqRegistry.clear();
		MqRegistry.add(OkExecutor::new);
		MqRegistry.add(ThrowExecutor::new);
		MqRegistry.add(FlakyExecutor::new);
		MqRegistry.add(AlwaysNgExecutor::new);

		DONE.set(0);
		FAILED.set(0);
		failUntil = 0;
		HANDLED.clear();
		CONTEXTS.clear();

	}

	// region 補助

	/**
	 * ワーカーを回して条件が満たされるまで待つ
	 *
	 * @param timeoutMs	待つ上限
	 * @param until		終わる条件
	 */
	private void runUntil (long timeoutMs, java.util.function.BooleanSupplier until) throws Exception {

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		assertNotNull(manager);

		long limit = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < limit && !until.getAsBoolean()) {
			Thread.sleep(20);
		}

		cancel.doCancel();
		manager.waitThread();

	}

	/**
	 * 行数
	 *
	 * @return	行数
	 */
	private int rowCount () {

		Data row = DBUtil.getMainDB().select("SELECT COUNT(1) AS cnt FROM %s".formatted(quoted()));

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * キューのテーブル名を製品に合わせて囲む（要件 F-D-30）
	 *
	 * @return	囲んだテーブル名
	 */
	private static String quoted () {

		return DBUtil.getMainDB().dialect().identifier(QUEUE);

	}

	/**
	 * 1行目
	 *
	 * @return	行
	 */
	private Data firstRow () {

		return DBUtil.getMainDB().select("SELECT * FROM %s ORDER BY id LIMIT 1".formatted(quoted()));

	}

	// endregion

	// region 積んで処理する

	@Test
	@DisplayName("積んだものが処理され、完了した行は消える")
	void completed () throws Exception {

		DB db = DBUtil.getMainDB();

		new OkExecutor().put(db, new Data().putData("name", "one"));
		new OkExecutor().put(db, new Data().putData("name", "two"));

		assertEquals(2, rowCount());

		runUntil(10000, () -> DONE.get() >= 2);

		assertEquals(2, DONE.get());
		assertEquals(0, rowCount(), "完了した行が残っている");
		assertTrue(HANDLED.contains("one") && HANDLED.contains("two"), HANDLED.toString());

	}

	@Test
	@DisplayName("メッセージ1件ごとに Context がある（要件 F-M-01）")
	void contextPerMessage () throws Exception {

		new OkExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "x"));

		runUntil(10000, () -> DONE.get() >= 1);

		assertEquals(1, CONTEXTS.size());
		assertTrue(CONTEXTS.peek().startsWith(QUEUE + "/"), CONTEXTS.toString());
		assertTrue(CONTEXTS.peek().endsWith("/1"), "1回目なのに attempt が違う: " + CONTEXTS.peek());

	}

	@Test
	@DisplayName("時刻を指定したものは、その時刻まで拾われない")
	void scheduled () throws Exception {

		Date future = new Date(System.currentTimeMillis() + 60_000);

		new OkExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "later"), future);

		runUntil(1500, () -> DONE.get() >= 1);

		assertEquals(0, DONE.get(), "まだ拾ってはいけない");
		assertEquals(1, rowCount());
		assertEquals(MqStatus.waiting.name(), firstRow().getString("status"));

	}

	// endregion

	// region トランザクション（要件 F-M-03）

	@Test
	@DisplayName("トランザクションをロールバックすればキューも消える")
	void rollbackRemovesQueue () throws Exception {

		DB db = DBUtil.getMainDB();

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			new OkExecutor().put(db, new Data().putData("name", "rolled_back"));

			transaction.rollbackEndTransaction();

		}

		assertEquals(0, rowCount(), "ロールバックしたのにキューが残っている");

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			new OkExecutor().put(db, new Data().putData("name", "committed"));

			transaction.commitEndTransaction();

		}

		assertEquals(1, rowCount());

	}

	// endregion

	// region 失敗したとき（要件 F-M-04）

	@Test
	@DisplayName("例外を投げてもワーカーは死なない")
	void executorExceptionDoesNotKillWorker () throws Exception {

		/*
		 * 移送元は execute() の例外を受けていなかった。
		 * ループの外へ抜けてワーカースレッドが静かに死に、
		 * 拾った行は running のまま残る。スレッドが1本ずつ減っていく。
		 */
		DB db = DBUtil.getMainDB();

		new ThrowExecutor().put(db, new Data().putData("name", "boom"));
		new OkExecutor().put(db, new Data().putData("name", "after"));

		runUntil(10000, () -> DONE.get() >= 1 && FAILED.get() >= 1);

		assertEquals(1, FAILED.get());
		assertEquals(1, DONE.get(), "落ちたあとのメッセージが処理されていない");

		Data dead = firstRow();

		assertEquals(MqStatus.dead.name(), dead.getString("status"));
		assertEquals("java.lang.IllegalStateException", dead.getDataOptional("log_info").getString("class"));
		assertEquals("わざと落とす", dead.getDataOptional("log_info").getString("message"));

	}

	@Test
	@DisplayName("error はやり直され、成功すれば消える")
	void retryThenSucceed () throws Exception {

		failUntil = 2;

		new FlakyExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "flaky"));

		runUntil(20000, () -> DONE.get() >= 1);

		assertEquals(1, DONE.get());
		assertEquals(3, FAILED.get(), "やり直しの回数が違う");
		assertEquals(0, rowCount(), "成功したのに残っている");

	}

	@Test
	@DisplayName("やり直しには間隔が空く")
	void retryHasBackoff () throws Exception {

		failUntil = 99;

		new FlakyExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "flaky"));

		runUntil(1500, () -> FAILED.get() >= 1);

		Data row = firstRow();

		assertNotNull(row);
		assertEquals(MqStatus.waiting.name(), row.getString("status"));
		assertEquals(1, row.getInt("retry_count"));
		assertNotNull(row.getString("scheduled_at"), "次にやる時刻が入っていない");
		assertTrue(FAILED.get() <= 2, "間隔を空けずに回している: " + FAILED.get());

	}

	@Test
	@DisplayName("上限まで失敗したら dead になる（デッドレター）")
	void deadLetter () throws Exception {

		new AlwaysNgExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "ng"));

		runUntil(20000, () -> {
			Data row = firstRow();
			return row != null && MqStatus.dead.name().equals(row.getString("status"));
		});

		Data row = firstRow();

		assertEquals(MqStatus.dead.name(), row.getString("status"));
		assertEquals(1, row.getInt("retry_count"), "maxRetry=1 なので1回だけやり直す");
		assertEquals(2, FAILED.get());

	}

	@Test
	@DisplayName("登録されていないキーはすぐ dead")
	void unknownKey () throws Exception {

		DBUtil.getMainDB().insert("""
				INSERT INTO %s (execute_type, mq_key, status, retry_count, data, created_at, updated_at)
				VALUES (?, ?, ?, 0, ?, NOW(), NOW())
			""".formatted(quoted())
			, MqExecuteType.short_time.name()
			, "not_registered"
			, MqStatus.waiting.name()
			, new Data());

		runUntil(10000, () -> {
			Data row = firstRow();
			return row != null && MqStatus.dead.name().equals(row.getString("status"));
		});

		Data row = firstRow();

		assertEquals(MqStatus.dead.name(), row.getString("status"));
		assertEquals(0, row.getInt("retry_count"), "何度やっても同じなのでやり直さない");
		assertTrue(row.getDataOptional("log_info").getString("reason").contains("not_registered")
			, row.getStringOptional("log_info"));

	}

	// endregion

	// region 迷子（プロセスが落ちたあと）

	@Test
	@DisplayName("running のまま古くなった行は waiting に戻る")
	void recoverStale () throws Exception {

		/*
		 * 移送元にはこれが無かった。処理中にプロセスが落ちると
		 * running のまま永久に残り、誰も拾わない。
		 */
		DBUtil.getMainDB().insert("""
				INSERT INTO %s (execute_type, mq_key, status, retry_count, data, created_at, updated_at)
				VALUES (?, ?, ?, 0, ?, NOW(), %s)
			""".formatted(quoted()
				, DBUtil.getMainDB().dialect().intervalFromNow("HOUR", true))
			, MqExecuteType.short_time.name()
			, "ok"
			, MqStatus.running.name()
			, new Data().putData("name", "stranded")
			, 1);

		assertEquals(MqStatus.running.name(), firstRow().getString("status"));

		assertEquals(1, queue.recoverStale());

		runUntil(10000, () -> DONE.get() >= 1);

		assertEquals(1, DONE.get(), "戻したのに拾われていない");
		assertEquals(0, rowCount());

	}

	@Test
	@DisplayName("処理中の行は戻さない")
	void recoverKeepsFresh () {

		DBUtil.getMainDB().insert("""
				INSERT INTO %s (execute_type, mq_key, status, retry_count, data, created_at, updated_at)
				VALUES (?, ?, ?, 0, ?, NOW(), NOW())
			""".formatted(quoted())
			, MqExecuteType.short_time.name()
			, "ok"
			, MqStatus.running.name()
			, new Data());

		assertEquals(0, queue.recoverStale());
		assertEquals(MqStatus.running.name(), firstRow().getString("status"));

	}

	// endregion

	// region 複数

	@Test
	@DisplayName("同じ行を二重に拾わない")
	void noDoubleDelivery () throws Exception {

		DB db = DBUtil.getMainDB();

		for (int i = 0; i < 30; i++) {
			new OkExecutor().put(db, new Data().putData("name", "n" + i));
		}

		runUntil(20000, () -> DONE.get() >= 30);

		assertEquals(30, DONE.get(), "重複して処理されている");
		assertEquals(30, HANDLED.size());
		assertEquals(30, HANDLED.stream().distinct().count(), "同じものを2回処理している");
		assertEquals(0, rowCount());

	}

	@Test
	@DisplayName("実行種別ごとにワーカーが立つ（要件 F-M-08）")
	void executeTypes () {

		List<MqExecuteType> types = MqRegistry.executeTypes(QUEUE);

		assertEquals(1, types.size(), "テストの Executor はすべて short_time");
		assertEquals(MqExecuteType.short_time, types.getFirst());
		assertEquals(2, MqConf.threadCount(MqExecuteType.short_time), "設定が効いていない");

	}

	// endregion

	// region メトリクス（要件 NF-O-04）

	@Test
	@DisplayName("受け取った数・終わり方・かかった時間を数える")
	void metrics () throws Exception {

		Metrics.reset();

		DB db = DBUtil.getMainDB();

		new OkExecutor().put(db, new Data().putData("name", "one"));
		new OkExecutor().put(db, new Data().putData("name", "two"));

		runUntil(10000, () -> DONE.get() >= 2);

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(2, counter.getLong("mq.%s.received".formatted(QUEUE)));
		assertEquals(2, counter.getLong("mq.%s.completed".formatted(QUEUE)));

		Data latency = Metrics.snapshot().getData("latency").getData("mq.%s".formatted(QUEUE));

		assertNotNull(latency, "かかった時間を入れていない");
		assertEquals(2, latency.getLong("count"));

	}

	@Test
	@DisplayName("落ちたぶんは終わり方ごとに分けて数える")
	void metricsByStatus () throws Exception {

		Metrics.reset();

		// maxRetry = 1 なので、error のあと dead_letter で終わる
		new AlwaysNgExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "ng"));

		runUntil(10000, () -> FAILED.get() >= 2);

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(2, counter.getLong("mq.%s.received".formatted(QUEUE)), counter.toString());
		assertTrue(counter.getLong("mq.%s.error".formatted(QUEUE)) >= 1, counter.toString());

		// 完了は1件も無い（0 ではなく、名前ごと出てこない）
		assertFalse(counter.containsKey("mq.%s.completed".formatted(QUEUE)), counter.toString());

	}

	@Test
	@DisplayName("キューの名前がそのまま名前になる（無限には増えない）")
	void metricsNameIsQueue () throws Exception {

		Metrics.reset();

		for (int i = 0; i < 20; i++) {
			new OkExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "n" + i));
		}

		runUntil(20000, () -> DONE.get() >= 20);

		Data data = Metrics.snapshot();

		// メッセージが 20 件でも、名前は received と completed の2つだけ
		assertEquals(2, data.getData("counter").size(), data.getData("counter").keySet().toString());
		assertEquals(1, data.getData("latency").size(), data.getData("latency").keySet().toString());

	}

	@Test
	@DisplayName("滞留数は自分で数えにいく（勝手にゲージにしない）")
	void pendingCount () {

		/*
		 * <b>frame側でゲージに登録してしまうと、Metrics.snapshot() のたびに SQL が飛ぶ。</b>
		 * DB が詰まっているときに限ってメトリクスも取れなくなるので、
		 * 登録するかどうかはアプリが決める（原則5）
		 */
		Metrics.reset();

		assertEquals(0, queue.pendingCount());

		Date future = new Date(System.currentTimeMillis() + 60_000);

		new OkExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "later"), future);
		new OkExecutor().put(DBUtil.getMainDB(), new Data().putData("name", "later2"), future);

		assertEquals(2, queue.pendingCount());

		// 呼ばないかぎりメトリクスには出ない
		assertFalse(Metrics.snapshot().getData("gauge").containsKey("mq.%s.pending".formatted(QUEUE)));

		// アプリが1行書けば出る
		Metrics.gauge("mq.%s.pending".formatted(QUEUE), () -> queue.pendingCount());

		assertEquals(2, Metrics.snapshot().getData("gauge").getLong("mq.%s.pending".formatted(QUEUE)));

	}

	// endregion

}
