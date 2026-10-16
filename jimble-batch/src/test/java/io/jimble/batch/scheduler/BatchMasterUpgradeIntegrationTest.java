package io.jimble.batch.scheduler;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchTables;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移植元から引き継いだ batch_master・途中で止まった batch_master に、2.5.8 の列が足されるか（D-302）
 *
 * <p>
 * jooby_base から移した DB は batch_master のコメントが「バッチマスタ:6」で、2.5.8 の版 2・3 が飛ばされて列が足されず、
 * cron のバッチが止まっていた。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class BatchMasterUpgradeIntegrationTest {

	/** テスト用のバッチ（同時実行数は既定の 1） */
	public static class UpgradeBatch extends AbstractBatch {

		@Override public String batchName () { return "版の引き継ぎ"; }
		@Override public String cron () { return "* * * * *"; }
		@Override public void execute (BatchArgs args) { }

	}

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), BatchMasterUpgradeIntegrationTest.class);

		BatchTables.install(DBUtil.getMainDB());

	}

	@AfterAll
	static void stopDataSource () {

		// 次に流すテストのために、列とコメントを今の版にそろえて戻し、このテストのバッチ（毎分の cron）を残さない
		DBVersion.reload(DBUtil.getMainDB());
		BatchTables.install(DBUtil.getMainDB());
		DBUtil.getMainDB().delete("DELETE FROM batch_master WHERE class_name = ?", UpgradeBatch.class.getName());

		BatchRegistry.clear();
		DBUtil.stop();

	}

	@BeforeEach
	void register () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE batch_master");

		BatchRegistry.clear();
		BatchRegistry.add(UpgradeBatch::new);
		BatchRegistry.sync(db);

	}

	/**
	 * batch_master を、ある版の形に書き換える
	 *
	 * @param commentVersion	コメントに書く版
	 * @param keepLastFiredAt	last_fired_at を残すか（版 2 だけ当たって止まった形）
	 */
	private static void downgrade (long commentVersion, boolean keepLastFiredAt) {

		DB db = DBUtil.getMainDB();

		if (!keepLastFiredAt) {
			db.execute("alter table batch_master drop column last_fired_at");
		}
		db.execute("alter table batch_master drop column fire_count");
		db.execute(db.dialect().setTableCommentSql("batch_master", "バッチマスタ:" + commentVersion));

		// DBVersion は DB ごとに表のコメントを1回だけ読むので、外から書き換えたら読み直す
		DBVersion.reload(db);

	}

	private static boolean hasColumn (String column) {

		DB db = DBUtil.getMainDB();

		return db.select(db.dialect().columnExistsSql(), "batch_master", column).orElseThrow().getLong("cnt") > 0;

	}

	private static String comment () {

		DB db = DBUtil.getMainDB();

		return db.selectList(db.dialect().tableCommentsSql()).stream()
			.filter(row -> row.getString("table_name").equalsIgnoreCase("batch_master"))
			.findFirst().orElseThrow()
			.getStringOptional("table_comment");

	}

	/**
	 * 同じ回を2台が取りにいくと、1台目だけが取れる（同時実行数 1）
	 *
	 * @param fireAt	回の時刻（エポック秒）
	 */
	private static void assertClaimOnce (long fireAt) {

		String className = UpgradeBatch.class.getName();

		assertTrue(new DbScheduler("server-a").claimFire(className, fireAt), "1回目が取れない（列が無い？）");
		assertFalse(new DbScheduler("server-b").claimFire(className, fireAt), "同じ回の2回目も取れた");

	}

	@Test
	@DisplayName("D-302 jooby_base から移した「バッチマスタ:6」の表に、install で last_fired_at・fire_count が足され、cron の回を取れる")
	void addsColumnsToMigratedTable () {

		downgrade(6, false);

		assertFalse(hasColumn("last_fired_at"));
		assertFalse(hasColumn("fire_count"));

		BatchTables.install(DBUtil.getMainDB());

		assertTrue(hasColumn("last_fired_at"), "last_fired_at が足されていない");
		assertTrue(hasColumn("fire_count"), "fire_count が足されていない");
		assertEquals("バッチマスタ:6", comment(), "コメントの版を下げた");

		assertClaimOnce(1_000);

		// 2列がもうある状態でもう一度通しても、何もしない（Duplicate column にならない）
		DBVersion.reload(DBUtil.getMainDB());
		BatchTables.install(DBUtil.getMainDB());

		assertClaimOnce(2_000);

	}

	@Test
	@DisplayName("D-302 版 2 だけ当たって「バッチマスタ:1」のまま止まった表は、fire_count だけが足されて install が通る")
	void resumesAfterStoppingBetweenVersions () {

		downgrade(1, true);

		assertTrue(hasColumn("last_fired_at"));
		assertFalse(hasColumn("fire_count"));

		BatchTables.install(DBUtil.getMainDB());

		assertTrue(hasColumn("fire_count"), "fire_count が足されていない");
		assertEquals("バッチマスタ:3", comment());

		assertClaimOnce(3_000);

	}

}
