package io.jimble.mq;

import com.typesafe.config.ConfigFactory;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 処理中のまま残った行を周期的に戻す（{@code mq.recover_interval}）と、取り直された行を上書きしない
 *
 * <p>
 * かつて {@code recoverStale} を呼ぶのはキューを起動するときの1回だけだった。プロセスが落ちて 10 分以内に立ち上がり直すと、
 * そのときの行はまだ古くなっておらず戻らず、<b>次にキューを起動し直すまで誰も処理しなかった</b>。
 * </p>
 *
 * <p>
 * また、処理を終えたときの書き換えは {@code WHERE id = ?} だけだった。{@code mq.stale} より長くかかった処理が戻されて、
 * 別のワーカーに取り直されると、<b>あとから終わった古い処理が、新しい処理の状態を上書きしていた</b>。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqRecoverIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_recover_test";

	/* 処理した数 */
	static final AtomicInteger DONE = new AtomicInteger();

	/* 処理の途中ですること（別のワーカーが取り直したことにする、など） */
	static volatile Callable<MqStatus> during = () -> MqStatus.completed;

	/**
	 * 処理（途中で during を呼ぶ）
	 */
	public static class RecoverExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "recover"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public int maxRetry () { return 3; }

		@Override
		public MqStatus execute (DB db, Data row) {

			DONE.incrementAndGet();

			try {
				return during.call();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}

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

	@AfterEach
	void clear () {

		MqRegistry.clear();
		DONE.set(0);
		during = () -> MqStatus.completed;

	}

	@AfterAll
	static void stop () {

		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
			DBVersion.reload(db);
		}
		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("動いている間に running のまま古くなった行を、recover_interval ごとに戻して処理する")
	void recoversPeriodically () throws Exception {

		load("""
			mq.recover_interval = 300ms
			mq.stale = 1s
			""");

		run(() -> {
			// 起動のときの recoverStale のあとに、落ちたプロセスの行が残る
			insertStaleRunning();
			return waitUntil(() -> DONE.get() == 1, 5_000);
		});

		assertEquals(1, DONE.get(), "動いている間に戻していない");

	}

	@Test
	@DisplayName("recover_interval = 0s なら、これまでどおり起動のときだけ戻す")
	void zeroMeansStartupOnly () throws Exception {

		load("""
			mq.recover_interval = 0s
			mq.stale = 1s
			""");

		run(() -> {
			insertStaleRunning();
			return waitUntil(() -> DONE.get() == 1, 2_000);
		});

		assertEquals(0, DONE.get(), "起動のときだけのはずが、動いている間に戻した");

	}

	@Test
	@DisplayName("何台かが同時に戻しても、retry_count は1しか増えない")
	void concurrentRecoverCountsOnce () throws Exception {

		load("mq.stale = 1s");
		insertStaleRunning();

		MqRegistry.add(RecoverExecutor::new);
		MqQueue queue = new MqQueue(QUEUE);

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return queue.recoverStale();
				}));
			}
			start.countDown();
			int total = 0;
			for (Future<Integer> result : results) {
				total += result.get(10, TimeUnit.SECONDS);
			}
			assertEquals(1, total, "二重に戻した");
		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, row().getInt("retry_count"));

	}

	@Test
	@DisplayName("処理の途中で取り直された行は、古い処理が終わっても消さない（completed）")
	void reclaimedRowIsNotDeleted () throws Exception {

		load("");

		during = () -> {
			reclaimByAnotherWorker();
			return MqStatus.completed;
		};

		runOneRow();

		Data row = row();
		assertEquals("running", row.getString("status"), "取り直した新しい処理の行を、古い処理が消した");
		assertEquals(1, row.getInt("retry_count"));

	}

	@Test
	@DisplayName("処理の途中で取り直された行は、古い処理が失敗しても書き換えない（error → やり直し）")
	void reclaimedRowIsNotRetried () throws Exception {

		load("");

		during = () -> {
			reclaimByAnotherWorker();
			return MqStatus.error;
		};

		runOneRow();

		Data row = row();
		assertEquals("running", row.getString("status"), "取り直した新しい処理の行を、古い処理が waiting に戻した");
		assertEquals(1, row.getInt("retry_count"));

	}

	@Test
	@DisplayName("取り直されていなければ、これまでどおり completed で消える")
	void notReclaimedIsDeleted () throws Exception {

		load("");

		runOneRow();

		try (DB db = DBUtil.getMainDB()) {
			assertTrue(db.select("SELECT id FROM " + db.dialect().identifier(QUEUE)).isEmpty());
		}

	}

	// region ヘルパー

	/**
	 * 1件積んで、処理し終えるまで回す
	 *
	 * @throws Exception	失敗した場合
	 */
	private static void runOneRow () throws Exception {

		run(() -> {
			try (DB db = DBUtil.getMainDB()) {
				new RecoverExecutor().put(db, new Data());
			}
			boolean done = waitUntil(() -> DONE.get() == 1, 5_000);
			Thread.sleep(300);     // 終えたあとの書き換えが済むまで
			return done;
		});

		assertEquals(1, DONE.get());

	}

	/**
	 * 処理の途中で、別のワーカーが取り直したことにする（recoverStale が戻し、ほかのワーカーが取った）
	 */
	private static void reclaimByAnotherWorker () {

		try (DB db = DBUtil.getMainDB()) {
			db.execute("UPDATE %s SET status = 'running', retry_count = retry_count + 1, updated_at = NOW()"
				.formatted(db.dialect().identifier(QUEUE)));
		}

	}

	/**
	 * 落ちたプロセスが残した行（running のまま1時間前）を入れる
	 */
	private static void insertStaleRunning () {

		try (DB db = DBUtil.getMainDB()) {
			new RecoverExecutor().put(db, new Data());
			// 古さは DB の NOW() で比べるので、DB の時刻で入れる（JVM と DB の時間帯が違っても古くなるように）
			db.execute("UPDATE %s SET status = 'running', updated_at = %s"
				.formatted(db.dialect().identifier(QUEUE), db.dialect().intervalFromNow("SECOND", true)), 3_600);
		}

	}

	private static Data row () {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT status, retry_count FROM " + db.dialect().identifier(QUEUE)).orElseThrow();
		}

	}

	/**
	 * キューを回し、立ち上がってから中身を流して止める
	 *
	 * @param body	回している間にすること
	 * @throws Exception	失敗した場合
	 */
	private static void run (Callable<Boolean> body) throws Exception {

		MqRegistry.clear();
		MqRegistry.add(RecoverExecutor::new);

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = new MqQueue(QUEUE).startNoWait(cancel);

		try {
			Thread.sleep(300);
			body.call();
		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

	}

	private static boolean waitUntil (BooleanSupplier condition, long millis) throws InterruptedException {

		long deadline = System.currentTimeMillis() + millis;

		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		return condition.getAsBoolean();

	}

	/**
	 * 設定を足して読み込み直し、キューの表を作り直す
	 *
	 * @param hocon	足す設定
	 */
	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon + """

			mq.poll_max = 100ms
			""").withFallback(Conf.conf().config()));
		DBUtil.load(Conf.conf().config(), MqRecoverIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
			DBVersion.reload(db);
		}

		new MqQueue(QUEUE).install();

	}

	// endregion

}
