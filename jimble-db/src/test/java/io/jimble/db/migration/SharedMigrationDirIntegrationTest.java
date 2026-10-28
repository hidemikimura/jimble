package io.jimble.db.migration;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2つのデータソースが同じマイグレーションのフォルダを使う（{@code db.<名前>.migration_dir}）
 *
 * <p>
 * 同じ表の作りの DB を何本もデータソースにする使い方では、<b>どの DB にも同じ SQL を当てる</b>。
 * 当たったかどうかの記録（{@code migration}・{@code migration_history}）は<b>それぞれの DB に残り</b>、
 * 片方だけ当たっていれば、<b>もう片方にだけ当たる</b>。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class SharedMigrationDirIntegrationTest {

	/** 主 */
	private static final String MAIN = "jimble_test";

	/** もう1つのデータソース */
	private static final String OTHER = "jimble_test_sub";

	/** 2つが使うフォルダの SQL ファイル */
	private static final String FILE = "shared_1.sql";

	@BeforeAll
	static void load () {

		Conf.reload();
		// ほかのテストが Migration.install() を呼んでいても、load の中では当てない（このテストで当てる）
		Conf.replace(ConfigFactory.parseString("""
			migration.on_startup = false
			migration.resource_dir = "migration_shared_test"
			db.jimble_test.migration_dir = "shop"
			db.jimble_test_sub.migration_dir = "shop"
			""").withFallback(Conf.conf().config()));

		DBUtil.load(Conf.conf().config(), SharedMigrationDirIntegrationTest.class);

	}

	@AfterAll
	static void stop () {

		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void clean () {

		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS shared_tag");
				db.execute("DROP TABLE IF EXISTS shared_item");
				if (hasTable(db, "migration")) {
					db.execute("DELETE FROM migration WHERE name = ?", FILE);
					db.execute("DELETE FROM migration_history WHERE name = ?", FILE);
				}
			}
		}

	}

	@Test
	@DisplayName("同じ migration_dir の2つのデータソースに、同じ SQL がそれぞれ1回ずつ当たる")
	void appliedToBoth () {

		migrate(MAIN);
		migrate(OTHER);

		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				assertTrue(hasTable(db, "shared_item"), name + " に表ができていない");
				assertTrue(hasTable(db, "shared_tag"), name + " に表ができていない");
				assertEquals("complete", row(db).getString("state"), name);
				assertEquals(2, upCount(db), name + " で up が1回ずつ流れていない");
			}
		}

		// 同じファイルなので、名前もハッシュも同じものが残る
		try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
			assertEquals(row(main).getString("hash"), row(other).getString("hash"));
		}

	}

	@Test
	@DisplayName("片方だけ当たっていれば、もう片方にだけ当たる")
	void appliedOnlyWhereMissing () {

		migrate(MAIN);

		long mainUps;
		try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
			mainUps = upCount(main);
			assertFalse(hasTable(other, "shared_item"), "主に当てただけで、もう片方にも当たっている");
		}

		migrate(MAIN);
		migrate(OTHER);

		try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
			assertEquals(mainUps, upCount(main), "当たっている主に、もう一度流れた");
			assertTrue(hasTable(other, "shared_item"));
			assertEquals(2, upCount(other));
		}

	}

	@Test
	@DisplayName("migration_dir のフォルダから SQL を探す（schema やデータソース名のフォルダではない）")
	void readsFromMigrationDir () {

		DBSource other = DBUtil.getDataSource(OTHER);

		assertEquals("shop", other.conf().migrationDir());
		assertEquals(OTHER, other.conf().schema(), "schema の意味は変えない");

	}

	// region ヘルパー

	/**
	 * クラスパスのフォルダから当てる（起動時と ./gradlew migrate と同じ入口）
	 *
	 * @param name	データソース名
	 */
	private static void migrate (String name) {

		Migration.migrate(DBUtil.getDataSource(name), SharedMigrationDirIntegrationTest.class);

	}

	/**
	 * 記録の行
	 *
	 * @param db	DB
	 * @return	行
	 */
	private static Data row (DB db) {

		Data row = db.select("SELECT state, hash FROM migration WHERE name = ?", FILE).orElse(null);
		assertNotNull(row, db.getDBName() + " に記録が無い");

		return row;

	}

	/**
	 * up として流れた文の数
	 *
	 * @param db	DB
	 * @return	件数
	 */
	private static long upCount (DB db) {

		return db.select("SELECT COUNT(*) AS cnt FROM migration_history WHERE name = ? AND kind = 'up'", FILE)
			.orElseThrow().getLong("cnt");

	}

	/**
	 * 表があるか
	 *
	 * @param db	DB
	 * @param table	表の名前
	 * @return	ある場合 = true
	 */
	private static boolean hasTable (DB db, String table) {

		return db.select("""
			SELECT COUNT(*) AS cnt FROM information_schema.tables
			WHERE table_schema = %s AND table_name = ?
			""".formatted(TestDdl.currentSchema(db)), table).orElseThrow().getLong("cnt") > 0;

	}

	// endregion

}
