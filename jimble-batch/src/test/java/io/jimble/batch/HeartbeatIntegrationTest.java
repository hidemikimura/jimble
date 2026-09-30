package io.jimble.batch;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 実行中の心拍は、DB の失敗で途切れない（要件 F-B-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * 心拍のスレッドは {@code batch_execute_info.updated_at} を打ち続ける。2.0 から DB の失敗は例外なので、
 * <b>1回失敗するとスレッドが終わり、二度と打たなかった</b>。心拍が途切れると alive を過ぎて同時実行数から外れ
 * （<b>同じバッチがもう1本起動できる</b>）、その3倍で実行情報が掃除される。
 * </p>
 *
 * <p>
 * 失敗は、<b>実行中に表の名前を一時的に変えて</b>起こす（実行中にこの表を触るのは心拍だけ）。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class HeartbeatIntegrationTest {

	/** 表を戻した直後の updated_at（ミリ秒） */
	static final AtomicLong RESTORED = new AtomicLong();

	/** しばらくあとの updated_at（ミリ秒） */
	static final AtomicLong LATER = new AtomicLong();

	/**
	 * 実行中に、心拍の表を一時的に読めなくするバッチ
	 */
	public static class FlakyTableBatch extends AbstractBatch {

		@Override public String batchName () { return "心拍が失敗する"; }

		@Override
		public void execute (BatchArgs args) {

			DB db = DBUtil.getMainDB();

			db.execute("ALTER TABLE batch_execute_info RENAME TO batch_execute_info_off");

			try {
				// 心拍は 200ms ごと。このあいだに数回失敗する
				sleep(800);
			} finally {
				db.execute("ALTER TABLE batch_execute_info_off RENAME TO batch_execute_info");
			}

			RESTORED.set(updatedAt(db, args.uid()));

			// NOW() は秒の精度なので、秒をまたぐまで待つ
			sleep(2300);

			LATER.set(updatedAt(db, args.uid()));

		}

		private static long updatedAt (DB db, String uid) {

			return db.select("SELECT updated_at FROM batch_execute_info WHERE uid = ?", uid)
				.map(row -> row.getDateTime("updated_at"))
				.orElse(-1L);

		}

		private static void sleep (long millis) {

			try {
				Thread.sleep(millis);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

		}

	}

	/**
	 * しばらく走るバッチ（3つ並べて、心拍のスレッドが1本かを見る）
	 */
	public abstract static class HoldBatch extends AbstractBatch {

		@Override
		public void execute (BatchArgs args) {

			try {
				Thread.sleep(2000);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

		}

	}

	/** 並べるバッチ1 */
	public static class HoldBatchA extends HoldBatch { @Override public String batchName () { return "しばらく走る A"; } }

	/** 並べるバッチ2 */
	public static class HoldBatchB extends HoldBatch { @Override public String batchName () { return "しばらく走る B"; } }

	/** 並べるバッチ3 */
	public static class HoldBatchC extends HoldBatch { @Override public String batchName () { return "しばらく走る C"; } }

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		originalConf = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("batch.heartbeat = 200ms").withFallback(originalConf));

		DBUtil.load(Conf.conf().config(), HeartbeatIntegrationTest.class);
		BatchTables.install(DBUtil.getMainDB());

		DB db = DBUtil.getMainDB();
		db.execute("DROP TABLE IF EXISTS batch_execute_info_off");
		db.execute("TRUNCATE TABLE batch_execute_info");
		/*
		 * マスタも空にしてから登録する。ほかのテストの sync で「登録に無い」とみなされて無効になった行は、
		 * sync し直しても有効に戻らない（管理画面で止めたものを勝手に戻さないため）
		 */
		db.execute("TRUNCATE TABLE batch_master");

		BatchExecutor.releaseAll();
		BatchRegistry.clear();
		BatchRegistry.add(FlakyTableBatch::new);
		BatchRegistry.add(HoldBatchA::new);
		BatchRegistry.add(HoldBatchB::new);
		BatchRegistry.add(HoldBatchC::new);
		BatchRegistry.sync(DBUtil.getMainDB());

	}

	@AfterAll
	static void stopDataSource () {

		BatchRegistry.clear();
		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@Test
	@DisplayName("F-B-05 心拍の更新が何度か失敗しても、表が戻れば打ち続ける")
	void heartbeatSurvivesDbFailures () {

		BatchResult result = BatchExecutor.execute(
			BatchExecutor.parseArgs(new String[]{ "class=" + FlakyTableBatch.class.getName() }));

		assertEquals(BatchResult.completed, result);
		assertTrue(RESTORED.get() > 0, "表を戻したあとに実行情報が見えません");
		assertTrue(LATER.get() > RESTORED.get()
			, "表が戻ったのに心拍が止まったままです（1回の失敗で心拍のスレッドが終わっている）: %d → %d"
				.formatted(RESTORED.get(), LATER.get()));

	}

	@Test
	@DisplayName("F-B-05 バッチが何本走っていても、心拍のスレッドは1本（1本の UPDATE で全部打つ）")
	void oneHeartbeatThreadForAllBatches () throws Exception {

		List<Thread> runs = new ArrayList<>();

		for (Class<?> batch : List.of(HoldBatchA.class, HoldBatchB.class, HoldBatchC.class)) {
			runs.add(Thread.ofVirtual().start(() -> BatchExecutor.execute(
				BatchExecutor.parseArgs(new String[]{ "class=" + batch.getName() }))));
			/*
			 * 少しずらして起動する。同じ瞬間に起動すると、実行情報の登録（registerExecuteInfo）で
			 * 5 秒ほど待たされることがある——心拍の間隔を短くした（200ms）ときに出て、2.1 のコードでも同じだった。
			 * ここで見たいのは心拍のスレッドの数なので、その待ちは避ける（原因は別に調べる）
			 */
			Thread.sleep(300);
		}

		// 3本とも走り出すまで待つ
		long deadline = System.currentTimeMillis() + 5000;
		while (BatchHeartbeats.size() < 3 && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		assertEquals(3, BatchHeartbeats.size(), "3本とも心拍に預けられていません");
		assertEquals(1, heartbeatThreads(), "バッチの数だけ心拍のスレッドが立っています");

		// 心拍が打たれている（1本の UPDATE で3本とも）
		Thread.sleep(600);
		long fresh = DBUtil.getMainDB().select("""
				SELECT count(*) AS n FROM batch_execute_info
				WHERE updated_at >= %s
			""".formatted(DBUtil.getMainDB().dialect().intervalFromNow("SECOND", true)), 1)
			.map(row -> row.getLong("n")).orElse(0L);
		assertEquals(3, fresh, "心拍が3本とも打たれていません");

		for (Thread run : runs) {
			run.join(10_000);
		}

		assertEquals(0, BatchHeartbeats.size(), "終わったバッチが心拍に残っています");

		// 誰もいなくなれば、心拍のスレッドも終わる
		deadline = System.currentTimeMillis() + 3000;
		while (heartbeatThreads() > 0 && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertEquals(0, heartbeatThreads(), "バッチがいないのに心拍のスレッドが残っています");

	}

	/**
	 * 心拍のスレッドの数
	 */
	private static long heartbeatThreads () {

		return Thread.getAllStackTraces().keySet().stream()
			.filter(t -> BatchHeartbeats.THREAD_NAME.equals(t.getName()) && t.isAlive())
			.count();

	}

}
