package io.jimble.batch;

import io.jimble.batch.status.BatchHistoryStatus;
import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * バッチ実行基盤が実 DB に対して動くことの確認（要件 F-B-01〜09）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-batch:dbTest
 * </pre>
 */
@Tag("db")
class BatchIntegrationTest {

	/* 走った回数 */
	static final AtomicInteger RUN_COUNT = new AtomicInteger();

	/* 実行中に待たせるラッチ */
	static volatile CountDownLatch hold = null;

	/* 走り始めたことを知らせるラッチ */
	static volatile CountDownLatch started = null;

	/* 最後に読んだ設定値 */
	static final java.util.concurrent.atomic.AtomicLong LAST_DAYS = new java.util.concurrent.atomic.AtomicLong();

	// region テスト用のバッチ

	/**
	 * ふつうに終わるバッチ
	 */
	public static class OkBatch extends AbstractBatch {

		@Override public String batchName () { return "テスト（正常）"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();
			executeInfo().putData("site_id", args.cliArgs.getString("site_id"));

		}

	}

	/**
	 * 落ちるバッチ
	 */
	public static class NgBatch extends AbstractBatch {

		@Override public String batchName () { return "テスト（異常）"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();
			throw new IllegalStateException("わざと落とす");

		}

	}

	/**
	 * 止まるまで回り続けるバッチ（要件 F-B-06）
	 */
	public static class LoopBatch extends AbstractBatch {

		@Override public String batchName () { return "テスト（長時間）"; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();

			for (int i = 0; i < 200; i++) {

				if (isCancelOrder()) {
					executeInfo().putData("stopped_at", i);
					return;
				}

				try {
					Thread.sleep(50);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return;
				}

			}

			executeInfo().putData("stopped_at", -1);

		}

	}

	/**
	 * 外から止めるまで居座るバッチ（同時実行のテスト用）
	 */
	public static class HoldBatch extends AbstractBatch {

		@Override public String batchName () { return "テスト（居座り）"; }

		@Override public int allowConcurrentExecutionCount () { return 1; }

		@Override
		public void execute (BatchArgs args) {

			RUN_COUNT.incrementAndGet();

			try {
				if (started != null) {
					started.countDown();
				}
				if (hold != null) {
					hold.await(10, TimeUnit.SECONDS);
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

		}

	}

	/**
	 * 設定を読むバッチ
	 */
	public static class SettingsBatch extends AbstractBatch {

		@Override public String batchName () { return "テスト（設定）"; }

		@Override public Data defaultBatchSettings () { return new Data().putData("days", 30); }

		@Override
		public void execute (BatchArgs args) {

			LAST_DAYS.set(settings().getLong("days"));

		}

	}

	// endregion

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), BatchIntegrationTest.class), "DB に接続できませんでした");

		BatchTables.install(DBUtil.getMainDB());

	}

	@AfterAll
	static void stopDataSource () {

		BatchRegistry.clear();
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE batch_master");
		db.execute("TRUNCATE TABLE batch_history");
		db.execute("TRUNCATE TABLE batch_execute_info");

		BatchExecutor.allReleaseNotStart();
		BatchRegistry.clear();

		RUN_COUNT.set(0);
		hold = null;
		started = null;

	}

	/**
	 * 登録してマスタに反映する
	 */
	private void register () {

		BatchRegistry.add(OkBatch::new);
		BatchRegistry.add(NgBatch::new);
		BatchRegistry.add(LoopBatch::new);
		BatchRegistry.add(HoldBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

	}

	/**
	 * 引数を作る
	 *
	 * @param batchClass	バッチのクラス
	 * @return	引数
	 */
	private BatchArgs args (Class<? extends AbstractBatch> batchClass) {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "class=" + batchClass.getName() });
		return args;

	}

	/**
	 * 最新の履歴
	 *
	 * @param batchClass	バッチのクラス
	 * @return	履歴
	 */
	private Data history (Class<? extends AbstractBatch> batchClass) {

		return DBUtil.getMainDB().select(
			"SELECT * FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
			, batchClass.getName());

	}

	// region 登録（要件 F-B-09）

	@Test
	@DisplayName("明示登録したバッチだけがマスタに載る")
	void syncWritesMaster () {

		register();

		List<Data> rows = DBUtil.getMainDB().selectList("SELECT * FROM batch_master ORDER BY class_name");

		assertEquals(4, rows.size());
		assertEquals(BatchMasterStatus.enable.name(), rows.getFirst().getString("status"));
		assertTrue(rows.stream().anyMatch(row ->
			OkBatch.class.getName().equals(row.getString("class_name"))));

	}

	@Test
	@DisplayName("入れ子クラスでもキーが壊れない")
	void nestedClassName () {

		/*
		 * 移送元は getCanonicalName() をキーにしていた。
		 * 入れ子クラスでは "io.jimble.batch.BatchIntegrationTest.OkBatch" になり、
		 * Class.forName では読めない。
		 */
		register();

		assertNotNull(DBUtil.getMainDB().select(
			"SELECT * FROM batch_master WHERE class_name = ?", OkBatch.class.getName()));

		assertTrue(OkBatch.class.getName().contains("$"), OkBatch.class.getName());

	}

	@Test
	@DisplayName("二重登録は例外")
	void duplicateRegistration () {

		BatchRegistry.add(OkBatch::new);

		assertThrows(IllegalStateException.class, () -> BatchRegistry.add(OkBatch::new));

	}

	@Test
	@DisplayName("登録されていないバッチは実行できない")
	void notRegistered () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "class=app.batch.NotExists" });

		assertEquals(BatchResult.skipped_not_registered, BatchExecutor.execute(args));

	}

	@Test
	@DisplayName("コードから消えたバッチは nothing になる")
	void disappearedBatch () {

		register();

		BatchRegistry.clear();
		BatchRegistry.add(OkBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		Data ok = DBUtil.getMainDB().select(
			"SELECT * FROM batch_master WHERE class_name = ?", OkBatch.class.getName());
		Data gone = DBUtil.getMainDB().select(
			"SELECT * FROM batch_master WHERE class_name = ?", NgBatch.class.getName());

		assertEquals(BatchMasterStatus.enable.name(), ok.getString("status"));
		assertEquals(BatchMasterStatus.nothing.name(), gone.getString("status"), "行そのものは残す");

	}

	@Test
	@DisplayName("1つも登録されていなければ全部 nothing になる")
	void allDisappeared () {

		register();

		/*
		 * 「最後の1つを消した」ときだけ行が enable のまま残る、を防ぐ。
		 * 裏返しに、<b>登録し忘れて sync を呼ぶと全部止まる</b>ので、
		 * sync は登録を済ませてから呼ぶこと。
		 */
		BatchRegistry.clear();
		BatchRegistry.sync(DBUtil.getMainDB());

		Data ok = DBUtil.getMainDB().select(
			"SELECT * FROM batch_master WHERE class_name = ?", OkBatch.class.getName());
		Data ng = DBUtil.getMainDB().select(
			"SELECT * FROM batch_master WHERE class_name = ?", NgBatch.class.getName());

		assertEquals(BatchMasterStatus.nothing.name(), ok.getString("status"));
		assertEquals(BatchMasterStatus.nothing.name(), ng.getString("status"));

	}

	// endregion

	// region 実行と履歴（要件 F-B-08）

	@Test
	@DisplayName("走って履歴が completed になる")
	void completed () {

		register();

		BatchArgs args = args(OkBatch.class);
		args.cliArgs.putData("site_id", "42");

		assertEquals(BatchResult.completed, BatchExecutor.execute(args));
		assertEquals(1, RUN_COUNT.get());

		Data row = history(OkBatch.class);

		assertEquals(BatchHistoryStatus.completed.name(), row.getString("status"));
		assertEquals("テスト（正常）", row.getString("name"));
		assertNotNull(row.getString("starts_at"));
		assertNotNull(row.getString("ends_at"));
		assertEquals("42", row.getDataOptional("execute_info").getString("site_id")
			, row.getStringOptional("execute_info"));

	}

	@Test
	@DisplayName("落ちたら履歴が error になり、例外が読める形で残る")
	void error () {

		register();

		assertEquals(BatchResult.error, BatchExecutor.execute(args(NgBatch.class)));

		Data row = history(NgBatch.class);
		Data exception = row.getDataOptional("execute_info").getDataOptional("exception");

		assertEquals(BatchHistoryStatus.error.name(), row.getString("status"));

		/*
		 * 移送元は Throwable をそのまま Data に入れていた。
		 * これは JSON にして保存されるので、読めるものにならない。
		 */
		assertEquals("java.lang.IllegalStateException", exception.getString("class"));
		assertEquals("わざと落とす", exception.getString("message"));
		assertTrue(exception.getString("stack_trace").contains("NgBatch"), exception.toString());

	}

	@Test
	@DisplayName("終わったら実行情報が消える")
	void executeInfoIsRemoved () {

		register();

		BatchExecutor.execute(args(OkBatch.class));

		assertNull(DBUtil.getMainDB().select("SELECT uid FROM batch_execute_info LIMIT 1"));

	}

	// endregion

	// region 実行されないとき（理由が分かること）

	@Test
	@DisplayName("マスタに行が無ければ skipped_no_master")
	void noMaster () {

		BatchRegistry.add(OkBatch::new);
		// sync しない

		assertEquals(BatchResult.skipped_no_master, BatchExecutor.execute(args(OkBatch.class)));
		assertEquals(0, RUN_COUNT.get());

	}

	@Test
	@DisplayName("無効にしてあれば skipped_disabled")
	void disabled () {

		register();

		DBUtil.getMainDB().update("UPDATE batch_master SET status = ? WHERE class_name = ?"
			, BatchMasterStatus.disable.name(), OkBatch.class.getName());

		assertEquals(BatchResult.skipped_disabled, BatchExecutor.execute(args(OkBatch.class)));
		assertEquals(0, RUN_COUNT.get());

	}

	@Test
	@DisplayName("forceExecute なら無効でも走る")
	void forceExecute () {

		register();

		DBUtil.getMainDB().update("UPDATE batch_master SET status = ? WHERE class_name = ?"
			, BatchMasterStatus.disable.name(), OkBatch.class.getName());

		BatchArgs args = args(OkBatch.class);
		args.forceExecute = true;

		assertEquals(BatchResult.completed, BatchExecutor.execute(args));
		assertEquals(1, RUN_COUNT.get());

	}

	@Test
	@DisplayName("全バッチ停止フラグが立っていれば走らない（要件 F-B-07）")
	void allStopped () {

		register();

		assertTrue(BatchExecutor.allNotStart());
		assertTrue(BatchExecutor.isAllNotStart());

		assertEquals(BatchResult.skipped_all_stopped, BatchExecutor.execute(args(OkBatch.class)));
		assertEquals(0, RUN_COUNT.get());

		assertTrue(BatchExecutor.allReleaseNotStart());
		assertFalse(BatchExecutor.isAllNotStart());

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(OkBatch.class)));

	}

	@Test
	@DisplayName("設定は既定の上に上書きで重なる")
	void settingsAreOverridden () {

		/*
		 * 移送元は MapUtil.mergeData()（深いマージ）を使っていて、
		 * 同じキーに値があると1つのリストにまとめていた。
		 * default_settings.days = 30 と settings.days = 30 が
		 * days = [30, 30] になり、getLong("days") が壊れる。
		 */
		BatchRegistry.add(SettingsBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		// 既定のまま
		assertEquals(BatchResult.completed, BatchExecutor.execute(args(SettingsBatch.class)));
		assertEquals(30L, LAST_DAYS.get());

		// マスタで上書き
		DBUtil.getMainDB().update(
			"UPDATE batch_master SET settings = ? WHERE class_name = ?"
			, new Data().putData("days", 7)
			, SettingsBatch.class.getName());

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(SettingsBatch.class)));
		assertEquals(7L, LAST_DAYS.get(), "上書きが効いていない");

	}

	// endregion

	// region 同時実行（要件 F-B-05）

	@Test
	@DisplayName("同時実行数を超えたら skipped_concurrent")
	void concurrentLimit () throws Exception {

		register();

		hold = new CountDownLatch(1);
		started = new CountDownLatch(1);

		Thread first = Thread.ofVirtual().start(() -> BatchExecutor.execute(args(HoldBatch.class)));

		assertTrue(started.await(10, TimeUnit.SECONDS), "1本目が始まらない");
		assertTrue(BatchExecutor.isExecutingBatch());

		// 2本目
		assertEquals(BatchResult.skipped_concurrent, BatchExecutor.execute(args(HoldBatch.class)));

		hold.countDown();
		first.join();

		assertEquals(1, RUN_COUNT.get(), "2本走ってしまっている");

		// 1本目が終われば通る
		assertEquals(BatchResult.completed, BatchExecutor.execute(args(HoldBatch.class)));

	}

	@Test
	@DisplayName("同時実行数を増やせば通る")
	void concurrentAllowed () throws Exception {

		register();

		DBUtil.getMainDB().update(
			"UPDATE batch_master SET allow_concurrent_execution = 2 WHERE class_name = ?"
			, HoldBatch.class.getName());

		hold = new CountDownLatch(1);
		started = new CountDownLatch(1);

		Thread first = Thread.ofVirtual().start(() -> BatchExecutor.execute(args(HoldBatch.class)));

		assertTrue(started.await(10, TimeUnit.SECONDS));

		started = new CountDownLatch(1);

		Thread second = Thread.ofVirtual().start(() -> BatchExecutor.execute(args(HoldBatch.class)));

		assertTrue(started.await(10, TimeUnit.SECONDS), "2本目が始まらない");

		hold.countDown();
		first.join();
		second.join();

		assertEquals(2, RUN_COUNT.get());

	}

	// endregion

	// region 中断（要件 F-B-06）

	@Test
	@DisplayName("doCancel で安全に止まり、履歴が canceled になる")
	void cancel () throws Exception {

		register();

		LoopBatch batch = new LoopBatch();
		java.util.concurrent.atomic.AtomicReference<BatchResult> result = new java.util.concurrent.atomic.AtomicReference<>();

		Thread thread = Thread.ofVirtual().start(() -> result.set(batch.run(args(LoopBatch.class), null)));

		// 履歴ができるまで待つ
		for (int i = 0; i < 500 && batch.batchId() <= 0 && result.get() == null; i++) {
			Thread.sleep(20);
		}

		assertTrue(batch.batchId() > 0, "履歴ができていない: " + result.get());

		batch.doCancel();
		thread.join();

		assertEquals(BatchResult.canceled, result.get());

		Data row = history(LoopBatch.class);

		assertEquals(BatchHistoryStatus.canceled.name(), row.getString("status"));
		assertTrue(row.getDataOptional("execute_info").getInt("stopped_at") >= 0
			, "最後まで回りきっている: " + row.getStringOptional("execute_info"));

	}

	@Test
	@DisplayName("DB 越しの中断指示でも止まる")
	void cancelFromDatabase () throws Exception {

		register();

		LoopBatch batch = new LoopBatch();
		java.util.concurrent.atomic.AtomicReference<BatchResult> result = new java.util.concurrent.atomic.AtomicReference<>();

		Thread thread = Thread.ofVirtual().start(() -> result.set(batch.run(args(LoopBatch.class), null)));

		for (int i = 0; i < 500 && batch.batchId() <= 0 && result.get() == null; i++) {
			Thread.sleep(20);
		}

		assertTrue(batch.batchId() > 0, "履歴ができていない: " + result.get());

		// 管理画面から止める操作に当たる
		DBUtil.getMainDB().update(
			"UPDATE batch_history SET cancel_status = 1 WHERE id = ?", batch.batchId());

		thread.join();

		assertEquals(BatchResult.canceled, result.get());

		assertEquals(BatchHistoryStatus.canceled.name(), history(LoopBatch.class).getString("status"));

	}

	// endregion

}
