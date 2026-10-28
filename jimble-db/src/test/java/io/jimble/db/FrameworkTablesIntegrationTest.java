package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.db.cache.DBCache;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.db.log.DBLog;
import io.jimble.db.redis.lock.RedisLock;
import io.jimble.db.value.DBValue;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理の表を作らないデータソース（{@code db.<名前>.framework_tables}）
 *
 * <p>
 * 店の DB には、使わない jimble の管理の表（{@code db_log}・{@code db_value}・{@code redis_lock}・{@code db_cache}）を作りたくない。
 * 作らない表を使う API をそのデータソースで呼んだら、「表が無い」という SQL のエラーではなく、設定の名前を言う例外にする。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class FrameworkTablesIntegrationTest {

	/** 表を絞るデータソース */
	private static final String SHOP = "jimble_test_sub";

	/** 絞るときに外す表 */
	private static final List<String> OPTIONAL = List.of("db_log", "db_value", "redis_lock", "db_cache");

	@AfterEach
	void tearDown () {

		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("書かなければ、これまでどおり全部の管理の表を作る")
	void allByDefault () {

		load("");

		for (String table : OPTIONAL) {
			assertTrue(hasTable(SHOP, table), table);
		}
		assertTrue(hasTable(SHOP, "db_lock"));

	}

	@Test
	@DisplayName("framework_tables = [db_lock] なら、ほかの管理の表を作らない")
	void onlyListedTablesAreCreated () {

		dropOptionalTables();
		load("db.jimble_test_sub.framework_tables = [\"db_lock\"]");

		for (String table : OPTIONAL) {
			assertFalse(hasTable(SHOP, table), table + " を作った");
		}
		assertTrue(hasTable(SHOP, "db_lock"));

		// 主は書いていないので、これまでどおり全部
		for (String table : OPTIONAL) {
			assertTrue(hasTable("jimble_test", table), table);
		}

	}

	@Test
	@DisplayName("作らない表を使う API を、そのデータソースで呼んだら、設定の名前を言う例外にする")
	void usingMissingTableFailsClearly () {

		dropOptionalTables();
		load("""
			db.jimble_test_sub.framework_tables = ["db_lock"]
			log.db = true
			""");

		try (DB shop = DBUtil.getDB(SHOP)) {
			assertNamesSetting(() -> DBValue.set(shop, "k", "v"));
			assertNamesSetting(() -> DBValue.getString(shop, "k"));
			assertNamesSetting(() -> new DBCache(shop));
			assertNamesSetting(() -> RedisLock.lock(shop, "k"));
			assertNamesSetting(() -> DBLog.save(shop, new Data().putData("message", "x")));
		}

		// 主ではこれまでどおり使える
		try (DB main = DBUtil.getMainDB()) {
			DBValue.set(main, "framework_tables_test", "v");
			assertEquals("v", DBValue.getString(main, "framework_tables_test"));
		}

	}

	@Test
	@DisplayName("db_lock は外せない（マイグレーションのロックに使う）")
	void dbLockIsRequired () {

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> load("db.jimble_test_sub.framework_tables = [\"db_value\"]"));
		assertTrue(thrown.getMessage().contains("db_lock"), thrown.getMessage());

	}

	@Test
	@DisplayName("知らない表の名前は起動で止める")
	void unknownTable () {

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> load("db.jimble_test_sub.framework_tables = [\"db_lock\", \"db_vlaue\"]"));
		assertTrue(thrown.getMessage().contains("db_vlaue"), thrown.getMessage());

	}

	// region ヘルパー

	private static void assertNamesSetting (Executable call) {

		IllegalStateException thrown = assertThrows(IllegalStateException.class, call);
		assertTrue(thrown.getMessage().contains("db." + SHOP + ".framework_tables"), thrown.getMessage());

	}

	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();
		Config config = ConfigFactory.parseString(hocon + "\nmigration.on_startup = false").withFallback(Conf.conf().config());
		Conf.replace(config);
		DBUtil.load(config, FrameworkTablesIntegrationTest.class);

	}

	/**
	 * 前のテストが作った管理の表を消す（表の一覧の写しも読み直す）
	 */
	private static void dropOptionalTables () {

		load("");
		try (DB shop = DBUtil.getDB(SHOP)) {
			for (String table : OPTIONAL) {
				shop.execute("DROP TABLE IF EXISTS " + table);
			}
			DBVersion.reload(shop);
		}
		DBUtil.stop();

	}

	private static boolean hasTable (String name, String table) {

		try (DB db = DBUtil.getDB(name)) {
			return db.selectList(db.dialect().tableCommentsSql()).stream()
				.anyMatch(row -> table.equalsIgnoreCase(row.getString("table_name")));
		}

	}

	// endregion

}
