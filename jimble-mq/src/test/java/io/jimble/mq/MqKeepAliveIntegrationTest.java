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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MqExecutor#keepAlive()}：mq.stale より長くかかる処理を、動いている間は戻さない
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqKeepAliveIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_keep_alive_test";

	/** mq.stale（処理はこの 2 倍かかる） */
	static final Duration STALE = Duration.ofSeconds(3);

	/* 処理を始めた回数 */
	static final AtomicInteger STARTED = new AtomicInteger();

	/* 処理を終えた回数 */
	static final AtomicInteger FINISHED = new AtomicInteger();

	/**
	 * mq.stale の 2 倍かかる処理
	 */
	public abstract static class SlowExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public MqExecuteType executeType () { return MqExecuteType.long_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			STARTED.incrementAndGet();

			try {
				Thread.sleep(STALE.multipliedBy(2).toMillis());
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			FINISHED.incrementAndGet();

			return MqStatus.completed;

		}

	}

	/** 延ばす */
	public static class KeepAliveExecutor extends SlowExecutor {

		@Override public String key () { return "keep_alive"; }
		@Override public boolean keepAlive () { return true; }

	}

	/** 延ばさない（これまでどおり） */
	public static class PlainExecutor extends SlowExecutor {

		@Override public String key () { return "plain"; }

	}

	/**
	 * 止められる通知
	 */
	static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }
		@Override public void doCancel () { cancelled = true; }

	}

	@BeforeEach
	void load () {

		DBUtil.stop();
		Conf.reload();
		Conf.replace(ConfigFactory.parseString("""
			mq.stale = %dms
			mq.recover_interval = 300ms
			mq.poll_max = 100ms
			""".formatted(STALE.toMillis())).withFallback(Conf.conf().config()));
		DBUtil.load(Conf.conf().config(), MqKeepAliveIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
			DBVersion.reload(db);
		}

		new MqQueue(QUEUE).install();

	}

	@AfterEach
	void clear () {

		MqRegistry.clear();
		STARTED.set(0);
		FINISHED.set(0);

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
	@DisplayName("keepAlive = true なら、mq.stale の 2 倍かかっても戻されず、1 回だけ処理して消える")
	void keepAliveRunsOnce () throws Exception {

		run(new KeepAliveExecutor());

		assertEquals(1, STARTED.get(), "動いている間に戻されて、もう一度処理された");
		assertEquals(1, FINISHED.get());
		assertTrue(remaining() == 0, "処理し終えた行が残っている");
		assertEquals(0, MqHeartbeats.size(), "終わったのに延ばし続けている");

	}

	@Test
	@DisplayName("keepAlive = false（既定）なら、これまでどおり動いている間に戻され、もう一度処理される（このテストが見分けられる印）")
	void plainIsReclaimed () throws Exception {

		run(new PlainExecutor());

		assertTrue(STARTED.get() >= 2, "戻されなかった（テストの前提が崩れている）: " + STARTED.get());

	}

	@Test
	@DisplayName("延ばす間隔は mq.stale の 1/3（下限 100ms）")
	void interval () {

		assertEquals(Duration.ofSeconds(1), MqHeartbeats.interval());

		Conf.replace(ConfigFactory.parseString("mq.stale = 150ms").withFallback(Conf.conf().config()));
		assertEquals(MqHeartbeats.MIN_INTERVAL, MqHeartbeats.interval());

	}

	// region ヘルパー

	/**
	 * 1 件積んで、処理が 1 回終わるまで回す
	 *
	 * @param executor	Executor
	 * @throws Exception	失敗した場合
	 */
	private static void run (SlowExecutor executor) throws Exception {

		MqRegistry.clear();
		MqRegistry.add(KeepAliveExecutor::new);
		MqRegistry.add(PlainExecutor::new);

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = new MqQueue(QUEUE).startNoWait(cancel);

		try {
			Thread.sleep(300);
			try (DB db = DBUtil.getMainDB()) {
				executor.put(db, new Data());
			}
			assertTrue(waitUntil(() -> FINISHED.get() >= 1, STALE.multipliedBy(4).toMillis()), "終わらない");
			Thread.sleep(500);     // 終えたあとの書き換えが済むまで
		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(30, TimeUnit.SECONDS), "止まりません");
		}

	}

	private static long remaining () {

		try (DB db = DBUtil.getMainDB()) {
			return db.selectList("SELECT id FROM " + db.dialect().identifier(QUEUE)).size();
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
