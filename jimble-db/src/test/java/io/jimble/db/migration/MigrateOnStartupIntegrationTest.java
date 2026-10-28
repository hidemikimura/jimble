package io.jimble.db.migration;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code db.<名前>.migrate_on_startup = false} のデータソースには、起動時に当てない
 *
 * <p>
 * 並べた DB はデプロイの段で CLI から当て、起動では当てない、という使い方のため。
 * 起動時に流れる処理（{@link Migration#install()} が {@code DBUtil.load} に預けるもの）を、データソースごとに呼んで確かめる。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class MigrateOnStartupIntegrationTest {

	/** 主 */
	private static final String MAIN = "jimble_test";

	/** 起動では当てないデータソース */
	private static final String OTHER = "jimble_test_sub";

	@BeforeAll
	static void load () {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString("""
			migration.on_startup = true
			migration.resource_dir = "migration_shared_test"
			db.jimble_test.migration_dir = "shop"
			db.jimble_test_sub.migration_dir = "shop"
			db.jimble_test_sub.migrate_on_startup = false
			""").withFallback(Conf.conf().config()));

		DBUtil.load(Conf.conf().config(), MigrateOnStartupIntegrationTest.class);

		// ほかのテストが Migration.install() を呼んでいると load の中で当たるので、当たる前の状態に戻す
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				clean(db);
			}
		}

	}

	@AfterAll
	static void stop () {

		// 次のテストに当たった状態を残さない
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				clean(db);
			}
		}

		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("migrate_on_startup = false のデータソースには、起動時の適用が当たらない")
	void skipsDataSource () {

		for (String name : new String[] { MAIN, OTHER }) {
			Migration.migrateOnStartup(DBUtil.getDataSource(name), MigrateOnStartupIntegrationTest.class);
		}

		try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
			assertTrue(hasTable(main, "shared_item"), "書いていない主には当たる（migration.on_startup に従う）");
			assertFalse(hasTable(other, "shared_item"), "migrate_on_startup = false なのに当たった");
		}

	}

	private static void clean (DB db) {

		db.execute("DROP TABLE IF EXISTS shared_tag");
		db.execute("DROP TABLE IF EXISTS shared_item");
		if (hasTable(db, "migration")) {
			db.execute("DELETE FROM migration WHERE name = 'shared_1.sql'");
		}
		if (hasTable(db, "migration_history")) {
			db.execute("DELETE FROM migration_history WHERE name = 'shared_1.sql'");
		}

	}

	private static boolean hasTable (DB db, String table) {

		return db.select("""
			SELECT COUNT(*) AS cnt FROM information_schema.tables
			WHERE table_schema = %s AND table_name = ?
			""".formatted(TestDdl.currentSchema(db)), table).orElseThrow().getLong("cnt") > 0;

	}

}
