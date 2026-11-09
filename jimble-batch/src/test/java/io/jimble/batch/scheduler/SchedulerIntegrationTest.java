package io.jimble.batch.scheduler;

import com.typesafe.config.ConfigFactory;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.mq.ExecuteBatchExecutor;
import io.jimble.batch.scheduler.mq.ReExecuteBatchExecutor;
import io.jimble.batch.scheduler.mq.SchedulerQueue;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqRegistry;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB スケジューラが実 DB に対して動くことの確認（要件 F-B-04 / F-B-10 / F-B-11）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-batch:dbTest
 * </pre>
 */
@Tag("db")
class SchedulerIntegrationTest {

	/* 走った回数 */
	static final AtomicInteger RUN_COUNT = new AtomicInteger();

	/* 走ったときの引数 */
	static final ConcurrentLinkedQueue<String> RUN_ARGS = new ConcurrentLinkedQueue<>();

	/* 走らせた台（毎分のバッチ） */
	static final ConcurrentLinkedQueue<String> RUN_SCHEDULERS = new ConcurrentLinkedQueue<>();

	// region テスト用のバッチ

	/**
	 * 毎分動くバッチ
	 */
	public static class EveryMinuteBatch extends AbstractBatch {

		@Override public String batchName () { return "毎分"; }
		@Override public String cron () { return "* * * * *"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();
			RUN_ARGS.add(String.valueOf(args.cliArgs().getStringOptional("site_id")));
			RUN_SCHEDULERS.add(args.schedulerId());
			executeInfo().putData("ran", true);

		}

	}

	/**
	 * cron を持たないバッチ
	 */
	public static class ManualBatch extends AbstractBatch {

		@Override public String batchName () { return "手動"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();
			RUN_ARGS.add(String.valueOf(args.cliArgs().getStringOptional("site_id")));

		}

	}

	/**
	 * cron が壊れているバッチ
	 */
	public static class BrokenCronBatch extends AbstractBatch {

		@Override public String batchName () { return "壊れた cron"; }
		@Override public String cron () { return "これは cron ではない"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();

		}

	}

	// endregion

	/* スケジューラ */
	private DbScheduler scheduler;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), SchedulerIntegrationTest.class);

		BatchTables.install(DBUtil.getMainDB());
		SchedulerQueue.queue().install();

	}

	@AfterAll
	static void stopDataSource () {

		BatchRegistry.clear();
		MqRegistry.clear();
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE batch_master");
		db.execute("TRUNCATE TABLE batch_history");
		db.execute("TRUNCATE TABLE batch_execute_info");
		db.execute("TRUNCATE TABLE %s".formatted(db.dialect().identifier(SchedulerQueue.name())));

		BatchRegistry.clear();
		MqRegistry.clear();

		BatchRegistry.add(EveryMinuteBatch::new);
		BatchRegistry.add(ManualBatch::new);
		BatchRegistry.add(BrokenCronBatch::new);
		BatchRegistry.sync(db);

		SchedulerControl.enable();

		RUN_COUNT.set(0);
		RUN_ARGS.clear();
		RUN_SCHEDULERS.clear();

		scheduler = new DbScheduler("test-scheduler");

	}

	@AfterEach
	void stopScheduler () {

		/*
		 * tick() で走らせた回が終わるまで待つ。待たないと、その回が次のテストで数え直したあとに走り、
		 * 次のテストの走った数に混ざる（「cron のあるバッチだけを見る」の 12:01 の回が D-301 のテストに混ざっていた）
		 */
		if (scheduler != null) {
			scheduler.stopAndAwait();
		}

	}

	// region cron（要件 F-B-04）

	@Test
	@DisplayName("cron のあるバッチだけを見る")
	void reloadPicksCronBatches () {

		scheduler.reload();

		// 毎分バッチだけ。cron 無しと壊れた cron は外れる
		ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

		// 1回目は次回時刻を控えるだけ
		assertEquals(0, scheduler.tick(now));

		// 1分後には動く
		assertEquals(1, scheduler.tick(now.plusMinutes(1)));

	}

	@Test
	@DisplayName("D-300 複数台で動かしても、cron の1回はどれか1台だけが走らせる（すぐ終わるバッチでも、時刻が遅れて来た台では走らせない）")
	void oneRunPerFireAcrossSchedulers () throws Exception {

		DbScheduler serverA = new DbScheduler("server-a");
		DbScheduler serverB = new DbScheduler("server-b");

		try {

			serverA.reload();
			serverB.reload();

			ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

			// 次回時刻を控える
			serverA.tick(now);
			serverB.tick(now);

			// 12:01 の回。A が走らせて、終わる
			serverA.tick(now.plusMinutes(1));
			awaitRuns(1);

			// B には少し遅れて同じ回の時刻が来る（確かめる間隔や時計のずれ）。A のバッチはもう終わっている
			serverB.tick(now.plusMinutes(1).plusSeconds(1));
			Thread.sleep(500);

			assertEquals(1, RUN_COUNT.get(), "同じ回を2台で走らせました");

			// 次の回（12:02）は、また1台だけが走らせる
			serverB.tick(now.plusMinutes(2));
			awaitRuns(2);
			serverA.tick(now.plusMinutes(2).plusSeconds(1));
			Thread.sleep(500);

			assertEquals(2, RUN_COUNT.get(), "次の回を2台で走らせました");

		} finally {
			serverA.stopAndAwait();
			serverB.stopAndAwait();
		}

	}

	@Test
	@DisplayName("D-301 cron の1回で走るのは同時実行数まで（3台で、すぐ終わるバッチでも）。0 なら台数分。次の回は数え直す")
	void runsPerFireUpToConcurrency () throws Exception {

		// 同時実行数 → 3台で1回に走る数
		int[][] cases = { { 2, 2 }, { 0, 3 } };

		for (int[] c : cases) {

			int allow = c[0];
			int expected = c[1];

			RUN_COUNT.set(0);
			DBUtil.getMainDB().execute("TRUNCATE TABLE batch_execute_info");
			DBUtil.getMainDB().update("UPDATE batch_master SET allow_concurrent_execution = ?, last_fired_at = NULL, fire_count = 0 WHERE class_name = ?"
				, allow, EveryMinuteBatch.class.getName());

			List<DbScheduler> servers = List.of(new DbScheduler("server-a"), new DbScheduler("server-b"), new DbScheduler("server-c"));

			try {

				ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

				for (DbScheduler server : servers) {
					server.reload();
					server.tick(now);
				}

				// 12:01 の回。1台ずつ、前の台のバッチが終わってから時刻が来る
				for (int i = 0; i < servers.size(); i++) {
					servers.get(i).tick(now.plusMinutes(1).plusSeconds(i));
					awaitSettled();
				}

				assertEquals(expected, RUN_COUNT.get(), "同時実行数 %d の1回で走った数".formatted(allow));

				// 12:02 の回は数え直す
				for (int i = 0; i < servers.size(); i++) {
					servers.get(i).tick(now.plusMinutes(2).plusSeconds(i));
					awaitSettled();
				}

				assertEquals(expected * 2, RUN_COUNT.get(), "同時実行数 %d の次の回で走った数".formatted(allow));

			} finally {
				servers.forEach(DbScheduler::stopAndAwait);
			}

		}

	}

	@Test
	@DisplayName("回を取った台が、ほかの台より多くのバッチを抱えていても、その回は走らせる（譲っても、ほかの台はその回を取れない）")
	void claimedFireRunsEvenIfBusier () throws Exception {

		DbScheduler serverA = new DbScheduler("server-a");

		try {

			ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

			serverA.reload();
			serverA.tick(now);

			// ほかのバッチを、A は2本・B は1本抱えている（B は生きているが、この回の時刻はまだ来ていない）
			insertRunning("server-a", 2);
			insertRunning("server-b", 1);

			// 12:01 の回は、A しか取りにいかない。A は忙しいので少し待つが、ほかに取る台がいないので自分で取る
			serverA.tick(now.plusMinutes(1));

			awaitRunCount(1);

			assertEquals(1, RUN_COUNT.get(), "回を取った台が譲り、その回がどの台でも走りませんでした");
			assertEquals("server-a", RUN_SCHEDULERS.peek(), "回を取ったのは A のはず");

		} finally {
			serverA.stopAndAwait();
		}

	}

	@Test
	@DisplayName("同じ回の時刻が来たら、空いている台が先に取る（忙しい台は少し待つ）")
	void lessBusyServerClaimsFirst () throws Exception {

		DbScheduler serverA = new DbScheduler("server-a");
		DbScheduler serverB = new DbScheduler("server-b");

		try {

			ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

			serverA.reload();
			serverB.reload();
			serverA.tick(now);
			serverB.tick(now);

			insertRunning("server-a", 2);
			insertRunning("server-b", 1);

			// 先に時刻が来るのは忙しい A（300ms 先）。待たなければ A が取ってしまうが、A は 1 秒待つので、あとから来た B が取る
			serverA.tick(now.plusMinutes(1));
			Thread.sleep(300);
			serverB.tick(now.plusMinutes(1));

			awaitRunCount(1);

			assertEquals(1, RUN_COUNT.get(), "1 回だけ走るはず");
			assertEquals("server-b", RUN_SCHEDULERS.peek(), "空いている B が取るはず");

		} finally {
			serverA.stopAndAwait();
			serverB.stopAndAwait();
		}

	}

	@Test
	@DisplayName("忙しい台が待つ時間は、いちばん少ない台との差 × claim_stagger（上限 5 秒）。走らせていない台・設定 0s は待たない")
	void claimStagger () {

		insertRunning("server-a", 3);
		insertRunning("server-b", 1);

		assertEquals(Duration.ofSeconds(2), new DbScheduler("server-a").claimStagger());
		assertEquals(Duration.ZERO, new DbScheduler("server-b").claimStagger());
		assertEquals(Duration.ZERO, new DbScheduler("server-c").claimStagger(), "走らせていない台は待たない");

		insertRunning("server-a", 6);
		assertEquals(SchedulerConf.MAX_CLAIM_STAGGER, new DbScheduler("server-a").claimStagger());

		try {
			Conf.replace(ConfigFactory.parseString(SchedulerConf.KEY_CLAIM_STAGGER + " = 0s").withFallback(Conf.conf().config()));
			assertEquals(Duration.ZERO, new DbScheduler("server-a").claimStagger());
		} finally {
			Conf.reload();
		}

	}

	/**
	 * ほかのバッチを走らせていることにする（どれも生きている）
	 *
	 * @param schedulerId	台
	 * @param count			本数
	 */
	private static void insertRunning (String schedulerId, int count) {

		DB db = DBUtil.getMainDB();

		for (int i = 0; i < count; i++) {
			db.insert("INSERT INTO batch_execute_info (uid, scheduler_id, class_name, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())"
				, "busy-" + schedulerId + "-" + java.util.UUID.randomUUID(), schedulerId, "example.LongBatch" + i);
		}

	}

	/**
	 * 走った回数がそろうまで待ち、そのあと少し待つ（余計に走らないことも見る）
	 *
	 * @param expected	回数
	 * @throws InterruptedException	割り込まれた場合
	 */
	private static void awaitRunCount (int expected) throws InterruptedException {

		long deadline = System.currentTimeMillis() + 15_000;

		while (RUN_COUNT.get() < expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		Thread.sleep(SchedulerConf.MAX_CLAIM_STAGGER.toMillis() + 500);

	}

	/**
	 * 走らせたかもしれないものが終わるまで待つ（走らないこともあるので、回数は見ない）
	 *
	 * @throws InterruptedException	割り込まれた場合
	 */
	private static void awaitSettled () throws InterruptedException {

		Thread.sleep(500);

		long deadline = System.currentTimeMillis() + 10_000;

		while (DBUtil.getMainDB().select("SELECT COUNT(1) AS cnt FROM batch_execute_info").map(row -> row.getLong("cnt")).orElse(0L) > 0
			&& System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

	}

	/**
	 * 走った回数がそろうまで待つ
	 *
	 * @param expected	回数
	 * @throws InterruptedException	割り込まれた場合
	 */
	private static void awaitRuns (int expected) throws InterruptedException {

		long deadline = System.currentTimeMillis() + 10_000;

		while (RUN_COUNT.get() < expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		assertEquals(expected, RUN_COUNT.get(), "時刻が来たのに走りません");

		// 走り終えて、実行中の印が消えるまで待つ
		while (DBUtil.getMainDB().select("SELECT COUNT(1) AS cnt FROM batch_execute_info").map(row -> row.getLong("cnt")).orElse(0L) > 0
			&& System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

	}

	@Test
	@DisplayName("起動と同時には走らせない")
	void doesNotFireOnStartup () {

		/*
		 * 初めて見たバッチは「次の時刻」を控えるだけにする。
		 * そうしないと、プロセスを再起動するたびに全部のバッチが走る。
		 */
		scheduler.reload();

		// 分の境目をまたがない時刻で見る
		ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 30, 15, 0, ZoneId.of("Asia/Tokyo"));

		assertEquals(0, scheduler.tick(now));
		assertEquals(0, scheduler.tick(now.plusSeconds(1)));
		assertEquals(0, scheduler.tick(now.plusSeconds(10)));

	}

	@Test
	@DisplayName("時刻が来るまでは走らせない")
	void firesOnlyWhenDue () {

		DBUtil.getMainDB().update("UPDATE batch_master SET cron = ? WHERE class_name = ?"
			, "0 3 * * *", EveryMinuteBatch.class.getName());

		scheduler.reload();

		ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

		assertEquals(0, scheduler.tick(now));
		assertEquals(0, scheduler.tick(now.plusHours(1)));
		assertEquals(0, scheduler.tick(now.plusHours(14)));
		assertEquals(1, scheduler.tick(now.plusHours(16)), "翌日 03:00 を過ぎたのに走らない");

	}

	@Test
	@DisplayName("無効にしたバッチは見ない")
	void ignoresDisabled () {

		DBUtil.getMainDB().update("UPDATE batch_master SET is_enable_scheduler = 0 WHERE class_name = ?"
			, EveryMinuteBatch.class.getName());

		scheduler.reload();

		ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 30, 15, 0, ZoneId.of("Asia/Tokyo"));

		scheduler.tick(now);

		assertEquals(0, scheduler.tick(now.plusMinutes(2)));

	}

	@Test
	@DisplayName("消えたバッチの記録は残さない")
	void forgetsRemovedBatches () {

		scheduler.reload();

		ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 30, 15, 0, ZoneId.of("Asia/Tokyo"));
		scheduler.tick(now);

		DBUtil.getMainDB().update("UPDATE batch_master SET is_enable_scheduler = 0 WHERE class_name = ?"
			, EveryMinuteBatch.class.getName());

		scheduler.reload();

		// 戻しても、また「初めて見た」扱いになる（起動と同時には走らない）
		DBUtil.getMainDB().update("UPDATE batch_master SET is_enable_scheduler = 1 WHERE class_name = ?"
			, EveryMinuteBatch.class.getName());

		scheduler.reload();

		assertEquals(0, scheduler.tick(now.plusMinutes(5)));

	}

	// endregion

	// region 入り切り（要件 F-B-10）

	@Test
	@DisplayName("止めたら動かない")
	void disabled () {

		assertTrue(SchedulerControl.isEnabled());
		assertTrue(SchedulerControl.disable());
		assertFalse(SchedulerControl.isEnabled());

		// start() はすぐ戻る
		new DbScheduler("test-disabled").start();

		assertNull(DbScheduler.current());

		assertTrue(SchedulerControl.enable());
		assertTrue(SchedulerControl.isEnabled());

	}

	// endregion

	// region 実行

	@Test
	@DisplayName("動いていれば「いま動かして」を受けられる（要件 F-B-11）")
	void runBatchViaQueue () throws Exception {

		Thread thread = Thread.ofVirtual().start(() -> scheduler.start());

		waitFor(10000, () -> DbScheduler.current() != null);

		assertNotNull(DbScheduler.current());

		waitFor(10000, SchedulerControl::isRunning);
		assertTrue(SchedulerControl.isRunning(), "稼働中と見えない");

		// 管理画面が積むのと同じこと
		long id = new ExecuteBatchExecutor().request(DBUtil.getMainDB(), ManualBatch.class.getName());

		assertTrue(id > 0);

		waitFor(20000, () -> RUN_COUNT.get() >= 1);

		assertEquals(1, RUN_COUNT.get(), "スケジューラがバッチを走らせていない");

		scheduler.stop();
		thread.join();

		assertNull(DbScheduler.current());

	}

	@Test
	@DisplayName("履歴と同じ引数でやり直せる")
	void reRunKeepsArgs () throws Exception {

		/*
		 * 移送元は batch_history の行から "args" 列を読もうとしていた。
		 * そんな列は無い（引数は execute_info の中）。
		 * いつも空の引数でやり直していた。
		 */
		Thread thread = Thread.ofVirtual().start(() -> scheduler.start());

		waitFor(10000, () -> DbScheduler.current() != null);

		// 1回目：引数つきで走らせる
		BatchArgs args = new BatchArgs();
		args.className(ManualBatch.class.getName());
		args.cliArgs().putData("site_id", "42");
		args.schedulerId("test-scheduler");

		new ManualBatch().run(args, null);

		assertEquals(1, RUN_COUNT.get());
		assertEquals("42", RUN_ARGS.peek());

		Data history = DBUtil.getMainDB().select(
			"SELECT id FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
			, ManualBatch.class.getName()).orElse(null);

		assertNotNull(history);

		// 2回目：履歴からやり直す
		new ReExecuteBatchExecutor().request(DBUtil.getMainDB(), history.getLong("id"));

		waitFor(20000, () -> RUN_COUNT.get() >= 2);

		scheduler.stop();
		thread.join();

		assertEquals(2, RUN_COUNT.get());
		assertEquals(2, RUN_ARGS.size());
		assertTrue(RUN_ARGS.stream().allMatch("42"::equals)
			, "やり直しで引数が失われている: " + RUN_ARGS);

	}

	@Test
	@DisplayName("動いていないときは「いま動かして」を受け付けない")
	void runBatchWithoutScheduler () {

		assertNull(DbScheduler.current());
		assertFalse(new DbScheduler("x").runBatch(ManualBatch.class.getName())
			, "動いていないのに受け付けている");

	}

	@Test
	@DisplayName("登録されていないバッチは動かさない")
	void runUnknownBatch () throws Exception {

		Thread thread = Thread.ofVirtual().start(() -> scheduler.start());

		waitFor(10000, () -> DbScheduler.current() != null);

		assertFalse(scheduler.runBatch("app.batch.NotExists"));

		scheduler.stop();
		thread.join();

	}

	// endregion

	/**
	 * 条件が満たされるまで待つ
	 *
	 * @param timeoutMs	待つ上限
	 * @param until		条件
	 */
	private void waitFor (long timeoutMs, java.util.function.BooleanSupplier until) throws Exception {

		long limit = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < limit && !until.getAsBoolean()) {
			Thread.sleep(20);
		}

	}

}
