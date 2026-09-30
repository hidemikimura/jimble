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

		BatchExecutor.releaseAll();
		BatchRegistry.clear();
		BatchRegistry.add(FlakyTableBatch::new);
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

}
