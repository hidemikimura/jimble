package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FlakyDataSource;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * データソースが 1 つ落ちても、ほかのデータソースの MQ を止めない
 *
 * <p>
 * 店の DB を {@code required = false} にして並べると、1 つが落ちることは起こりうる。
 * そのときも、<b>ほかの DB の行は処理され続け</b>、落ちた DB が戻れば、その DB の行もまた処理される。
 * </p>
 *
 * <p>
 * 「落ちる」は、接続を借りようとすると例外を投げるデータソースに差し替えて作る（{@link FlakyDataSource}）。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqDataSourceFailureIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_failure_test";

	/** 落とさないデータソース */
	static final String ALIVE = TwoDataSources.MAIN;

	/** 落とすデータソース */
	static final String FLAKY = TwoDataSources.OTHER;

	/* データソースごとの処理した数 */
	static final Map<String, AtomicInteger> DONE = new ConcurrentHashMap<>();

	/**
	 * すぐ終わる処理（どのデータソースの行かを数える）
	 */
	public static class CountExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "count"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			DONE.computeIfAbsent(db.getDBName(), key -> new AtomicInteger()).incrementAndGet();
			return MqStatus.completed;

		}

	}

	/**
	 * 止められる通知
	 */
	static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }
		@Override public void doCancel () { cancelled = true; }

	}

	/* 落とせるデータソース */
	private FlakyDataSource flaky;

	@BeforeAll
	static void load () {

		Conf.reload();
		Config base = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("""
			mq.poll_max = 100ms
			mq.thread_count.short_time = 1
			""").withFallback(TwoDataSources.withOther(base)));

		DBUtil.load(Conf.conf().config(), MqDataSourceFailureIntegrationTest.class);

		for (String name : new String[] { ALIVE, FLAKY }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

		new MqQueue(QUEUE).install();

	}

	@AfterAll
	static void stop () {

		for (String name : new String[] { ALIVE, FLAKY }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

		MqRegistry.clear();
		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void setUp () {

		for (String name : new String[] { ALIVE, FLAKY }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DELETE FROM " + db.dialect().identifier(QUEUE));
			}
		}

		MqRegistry.clear();
		MqRegistry.add(CountExecutor::new);
		DONE.clear();

		flaky = FlakyDataSource.install(FLAKY);

	}

	@AfterEach
	void tearDown () {

		flaky.close();

	}

	@Test
	@DisplayName("起動するときに 1 つ落ちていても、キューは起動し、ほかのデータソースの行は処理される")
	void startsWhileOneIsDown () throws Exception {

		put(ALIVE, 3);
		flaky.down();

		run(() -> waitUntil(() -> done(ALIVE) == 3, 5_000));

		assertEquals(3, done(ALIVE), "落ちていないデータソースの行が処理されない");

	}

	@Test
	@DisplayName("動いている途中で 1 つ落ちても、ほかの行は処理され続け、戻れば落ちていた DB の行も処理される")
	void keepsGoingAcrossFailureAndRecovery () throws Exception {

		run(() -> {

			put(ALIVE, 2);
			put(FLAKY, 2);
			assertTrue(waitUntil(() -> done(ALIVE) == 2 && done(FLAKY) == 2, 5_000), "落とす前に処理されない: " + DONE);

			flaky.down();

			put(ALIVE, 3);
			assertTrue(waitUntil(() -> done(ALIVE) == 5, 5_000), "1 つ落ちたら、ほかの行が処理されなくなった: " + DONE);

			flaky.up();

			put(FLAKY, 2);
			assertTrue(waitUntil(() -> done(FLAKY) == 4, 15_000), "戻ったのに、落ちていた DB の行が処理されない: " + DONE);

			put(ALIVE, 1);
			return waitUntil(() -> done(ALIVE) == 6, 5_000);

		});

		assertEquals(6, done(ALIVE));
		assertEquals(4, done(FLAKY));

	}

	@Test
	@DisplayName("落ちた DB が接続を待たせても（connection_timeout）、ほかの行は待たされない")
	void slowFailureDoesNotHoldBackOthers () throws Exception {

		run(() -> {

			// 動き出してから落とす（接続を借りようとすると 1 秒待たされてから失敗する）
			flaky.downSlowly(1_000);

			long start = System.nanoTime();
			put(ALIVE, 10);
			boolean done = waitUntil(() -> done(ALIVE) == 10, 15_000);
			long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

			assertTrue(seconds <= 4, "落ちた DB を毎周待ったので、10 件に %d 秒かかった".formatted(seconds));

			return done;

		});

		assertEquals(10, done(ALIVE));

	}

	@Test
	@DisplayName("迷子の戻しと件数は、落ちた DB を飛ばして、ほかの DB の分を返す")
	void recoverAndCountSkipDownDataSource () {

		try (DB db = DBUtil.getDB(ALIVE)) {
			new CountExecutor().put(db, new Data());
			new CountExecutor().put(db, new Data());
			db.execute("UPDATE %s SET status = 'running', updated_at = %s".formatted(
				db.dialect().identifier(QUEUE), db.dialect().intervalFromNow("SECOND", true)), 3_600);
		}

		flaky.down();

		MqQueue queue = new MqQueue(QUEUE);

		assertEquals(2, queue.recoverStale(), "落ちた DB があると、ほかの DB の迷子が戻らない");
		assertEquals(2, queue.pendingCount(), "落ちた DB があると、件数が数えられない");

	}

	// region ヘルパー

	private static int done (String name) {

		AtomicInteger count = DONE.get(name);

		return count == null ? 0 : count.get();

	}

	private static void put (String name, int count) {

		try (DB db = DBUtil.getDB(name)) {
			for (int i = 0; i < count; i++) {
				new CountExecutor().put(db, new Data());
			}
		}

	}

	/** 回している間にすること */
	@FunctionalInterface
	interface Body {

		boolean run () throws Exception;

	}

	/**
	 * キューを回し、中身を流して止める
	 *
	 * @param body	回している間にすること
	 * @throws Exception	失敗した場合
	 */
	private static void run (Body body) throws Exception {

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = new MqQueue(QUEUE).startNoWait(cancel);

		try {
			Thread.sleep(200);
			body.run();
		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(15, TimeUnit.SECONDS), "止まりません");
		}

	}

	private static boolean waitUntil (BooleanSupplier condition, long millis) throws InterruptedException {

		long deadline = System.currentTimeMillis() + millis;

		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		return condition.getAsBoolean();

	}

	// endregion

}
