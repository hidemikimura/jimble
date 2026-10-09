package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.ConnectionWatch;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB から取るのはキューごとに1本（要件 F-M-08）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * かつては<b>ワーカーが1本ずつ自分で DB を見ていた</b>。何もしていないときでも、ワーカーの数だけ定期的に
 * 接続を借りるので、DB の応答が数秒遅れると<b>ワーカーの数だけ接続が同時に埋まり</b>、
 * プールの取得待ちが時間切れになった。いまは取り出し役1本だけが DB を見て、手の空いたワーカーへ渡す。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqPollerIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_poller_test";

	/* 処理した数 */
	static final AtomicInteger DONE = new AtomicInteger();

	/* いま処理している数 */
	static final AtomicInteger RUNNING = new AtomicInteger();

	/* 同時に処理していた数の最大 */
	static final AtomicInteger PEAK = new AtomicInteger();

	/**
	 * 少し時間のかかる処理（並んで動くかを見る）
	 */
	public static class SlowExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "slow"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			PEAK.accumulateAndGet(RUNNING.incrementAndGet(), Math::max);

			try {
				Thread.sleep(150);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			} finally {
				RUNNING.decrementAndGet();
			}

			DONE.incrementAndGet();

			return MqStatus.completed;

		}

	}

	/**
	 * 別の実行種別（種別が増えても、取り出し役は1本のまま）
	 */
	public static class OtherTypeExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "other"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.middle_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			DONE.incrementAndGet();
			return MqStatus.completed;

		}

	}

	/* いま処理に渡されている DB（同じものが同時に2つの処理へ渡っていないかを見る） */
	static final Set<DB> IN_USE = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

	/* 同じ DB が同時に2つの処理へ渡っていた回数 */
	static final AtomicInteger SHARED = new AtomicInteger();

	/**
	 * すぐ終わる処理（取り出し役がまとめて取る形になる）
	 */
	public static class FastExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "fast"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			if (!IN_USE.add(db)) {
				SHARED.incrementAndGet();
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			} finally {
				IN_USE.remove(db);
			}

			DONE.incrementAndGet();
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

	/* 元の設定 */
	private static Config originalConf;

	/* キュー */
	private static MqQueue queue;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		originalConf = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("""
			mq.thread_count.short_time = 5
			mq.thread_count.middle_time = 2
			mq.poll_max = 100ms
			""").withFallback(originalConf));

		DBUtil.load(Conf.conf().config(), MqPollerIntegrationTest.class);

		queue = new MqQueue(QUEUE);
		queue.install();

	}

	@AfterAll
	static void stopDataSource () {

		MqRegistry.clear();
		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("TRUNCATE TABLE %s".formatted(DBUtil.getMainDB().dialect().identifier(QUEUE)));

		MqRegistry.clear();
		MqRegistry.add(SlowExecutor::new);
		MqRegistry.add(OtherTypeExecutor::new);
		MqRegistry.add(FastExecutor::new);

		DONE.set(0);
		RUNNING.set(0);
		PEAK.set(0);
		SHARED.set(0);
		IN_USE.clear();

	}

	@Test
	@DisplayName("F-M-08 何もしていないとき、DB を借りに来るのは取り出し役の1本だけ（ワーカー7本でも）")
	void onlyThePollerTouchesTheDbWhenIdle () throws Exception {

		Cancel cancel = new Cancel();

		try (ConnectionWatch watch = ConnectionWatch.install()) {

			VirtualThreadManager manager = queue.startNoWait(cancel);

			try {

				// 立ち上がりの分（recoverStale など）を外して見る
				Thread.sleep(300);
				watch.startRecording();

				// poll_max = 100ms なので、この間に何回も見にいく
				Thread.sleep(1500);

				Set<String> threads = watch.stopRecording();

				assertEquals(1, threads.size()
					, "取り出し役のほかにも DB を借りに来ています（ワーカーが自分で見にいっている）: " + threads);
				assertTrue(threads.iterator().next().startsWith(MqQueue.pollerName(QUEUE) + "@"), threads.toString());

			} finally {
				cancel.doCancel();
				assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
			}

		}

	}

	@Test
	@DisplayName("F-M-08 取り出し役が1本でも、ワーカーは並んで処理する")
	void workersStillRunInParallel () throws Exception {

		DB db = DBUtil.getMainDB();

		for (int i = 0; i < 20; i++) {
			new SlowExecutor().put(db, new Data().putData("n", i));
		}
		for (int i = 0; i < 3; i++) {
			new OtherTypeExecutor().put(db, new Data().putData("n", i));
		}

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		try {

			long deadline = System.currentTimeMillis() + 15_000;

			while (DONE.get() < 23 && System.currentTimeMillis() < deadline) {
				Thread.sleep(50);
			}

		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

		assertEquals(23, DONE.get(), "全部は処理されませんでした");
		assertTrue(PEAK.get() >= 2, "ワーカーが並んで処理していません（最大 %d）".formatted(PEAK.get()));
		assertTrue(PEAK.get() <= 5, "スレッド数（5）を超えて同時に処理しています: " + PEAK.get());

	}

	@Test
	@DisplayName("F-M-08 止めても、取った行を取り残さない（running のまま残らない）")
	void stopLeavesNothingRunning () throws Exception {

		DB db = DBUtil.getMainDB();

		for (int i = 0; i < 30; i++) {
			new SlowExecutor().put(db, new Data().putData("n", i));
		}

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		// 何件か処理しているところで止める
		Thread.sleep(400);
		cancel.doCancel();

		assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");

		long running = db.select("SELECT count(*) AS n FROM %s WHERE status = ?"
				.formatted(db.dialect().identifier(QUEUE)), MqStatus.running.name())
			.map(row -> row.getLong("n")).orElse(-1L);

		assertEquals(0, running, "止めたあとに running の行が残っています（取ったのに誰も処理しなかった）");

	}

	@Test
	@DisplayName("D-295 まとめて取っても、running にするのは手の空いたワーカーの数まで（先読みしない）")
	void batchClaimNeverExceedsIdleWorkers () throws Exception {

		DB db = DBUtil.getMainDB();

		for (int i = 0; i < 30; i++) {
			new SlowExecutor().put(db, new Data().putData("n", i));
		}

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		long maxRunning = 0;

		try {

			long deadline = System.currentTimeMillis() + 15_000;

			while (DONE.get() < 30 && System.currentTimeMillis() < deadline) {

				long running = db.select("SELECT count(*) AS n FROM %s WHERE status = ?"
						.formatted(db.dialect().identifier(QUEUE)), MqStatus.running.name())
					.map(row -> row.getLong("n")).orElse(0L);

				maxRunning = Math.max(maxRunning, running);

				Thread.sleep(20);

			}

		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

		assertEquals(30, DONE.get(), "全部は処理されませんでした");
		assertTrue(maxRunning <= 5, "スレッド数（5）より多く running にしています: " + maxRunning);

	}

	@Test
	@DisplayName("D-295 まとめて取った行は、1件ずつ別の DB で処理する（接続を取り合わない）")
	void batchClaimGivesEachRowItsOwnDb () throws Exception {

		/*
		 * DB は握っている接続をフィールドに持つ。まとめて取った行に<b>同じ DB を付けて渡すと</b>、
		 * ワーカーどうしで接続を取り合い、プールへ戻らなくなって止まる。
		 * 取り合いが起きるかは運しだいなので、<b>同じ DB が同時に2つの処理へ渡っていないか</b>を直に見る
		 */
		DB db = DBUtil.getMainDB();
		int count = 200;

		for (int i = 0; i < count; i++) {
			new FastExecutor().put(db, new Data().putData("n", i));
		}

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		try {

			long deadline = System.currentTimeMillis() + 30_000;

			while (DONE.get() < count && System.currentTimeMillis() < deadline) {
				Thread.sleep(50);
			}

		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

		assertEquals(0, SHARED.get(), "同じ DB を同時に何本ものワーカーへ渡しています: %d 回".formatted(SHARED.get()));
		assertEquals(count, DONE.get(), "全部は処理されませんでした（接続がプールへ戻っていない）");

		long left = db.select("SELECT count(*) AS n FROM %s".formatted(db.dialect().identifier(QUEUE)))
			.map(row -> row.getLong("n")).orElse(-1L);

		assertEquals(0, left, "処理し終えた行が消えていません");

	}

}
