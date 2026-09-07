package io.jimble.batch.scheduler;

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

import java.time.ZoneId;
import java.time.ZonedDateTime;
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
			RUN_ARGS.add(String.valueOf(args.cliArgs.getStringOptional("site_id")));
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
			RUN_ARGS.add(String.valueOf(args.cliArgs.getStringOptional("site_id")));

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
		assertTrue(DBUtil.load(Conf.conf().config(), SchedulerIntegrationTest.class), "DB に接続できませんでした");

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
		db.execute("TRUNCATE TABLE `%s`".formatted(SchedulerQueue.name()));

		BatchRegistry.clear();
		MqRegistry.clear();

		BatchRegistry.add(EveryMinuteBatch::new);
		BatchRegistry.add(ManualBatch::new);
		BatchRegistry.add(BrokenCronBatch::new);
		BatchRegistry.sync(db);

		SchedulerControl.enable();

		RUN_COUNT.set(0);
		RUN_ARGS.clear();

		scheduler = new DbScheduler("test-scheduler");

	}

	@AfterEach
	void stopScheduler () {

		if (scheduler != null) {
			scheduler.stop();
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
		args.className = ManualBatch.class.getName();
		args.cliArgs.putData("site_id", "42");
		args.schedulerId = "test-scheduler";

		new ManualBatch().run(args, null);

		assertEquals(1, RUN_COUNT.get());
		assertEquals("42", RUN_ARGS.peek());

		Data history = DBUtil.getMainDB().select(
			"SELECT id FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
			, ManualBatch.class.getName());

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
