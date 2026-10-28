package io.jimble.db.cli;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DBConf;
import io.jimble.db.DBUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code migrate} / {@code migrateCheck} のオプションと、当てる先の選び方
 */
class MigrateOptionsTest {

	/** 主・同じフォルダの店の DB 2 つ・別のフォルダの DB（つながずに読む） */
	private static final Map<String, DBConf> CONFS = DBUtil.readDataSourceConfs(ConfigFactory.parseString("""
		db {
			remove_all_null_table_data = false
			main   { main = true, url = "jdbc:x" }
			shop_b { url = "jdbc:x", migration_dir = "shop" }
			shop_a { url = "jdbc:x", migration_dir = "shop" }
			log    { url = "jdbc:x", schema = "logs" }
		}
		"""));

	@Test
	@DisplayName("オプションを書かなければ、全部を名前の順に1つずつ、最初の失敗で止める（これまでと同じ）")
	void defaults () {

		MigrateOptions options = MigrateOptions.parse(List.of());

		assertEquals(1, options.parallel());
		assertFalse(options.keepGoing());
		assertFalse(options.isFiltered());
		assertEquals(List.of("log", "main", "shop_a", "shop_b"), options.select(CONFS));

	}

	@Test
	@DisplayName("--data-source はくり返せる")
	void dataSources () {

		MigrateOptions options = MigrateOptions.parse(List.of("--data-source", "shop_b", "--data-source", "main"));

		assertTrue(options.isFiltered());
		assertEquals(List.of("main", "shop_b"), options.select(CONFS));

	}

	@Test
	@DisplayName("--migration-dir は、そのフォルダを使うデータソースに絞る（schema で決まるものも含む）")
	void migrationDir () {

		assertEquals(List.of("shop_a", "shop_b"), MigrateOptions.parse(List.of("--migration-dir", "shop")).select(CONFS));
		assertEquals(List.of("log"), MigrateOptions.parse(List.of("--migration-dir", "logs")).select(CONFS));

	}

	@Test
	@DisplayName("両方を書けば、両方を満たすものだけ")
	void both () {

		MigrateOptions options = MigrateOptions.parse(List.of("--migration-dir", "shop", "--data-source", "shop_a", "--data-source", "main"));

		assertEquals(List.of("shop_a"), options.select(CONFS));

	}

	@Test
	@DisplayName("--parallel と --keep-going")
	void parallelAndKeepGoing () {

		MigrateOptions options = MigrateOptions.parse(List.of("--parallel", "4", "--keep-going"));

		assertEquals(4, options.parallel());
		assertTrue(options.keepGoing());

	}

	@Test
	@DisplayName("書き間違いは、何もせずに止める")
	void mistakes () {

		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--data-sources", "x")));
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--data-source")));
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--data-source", "--keep-going")));
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--parallel", "0")));
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--parallel", "x")));

		// 設定に無い名前・どれも使っていないフォルダ・両方を満たすものが無い
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--data-source", "shop_c")).select(CONFS));
		assertThrows(IllegalArgumentException.class, () -> MigrateOptions.parse(List.of("--migration-dir", "nope")).select(CONFS));
		assertThrows(IllegalArgumentException.class
			, () -> MigrateOptions.parse(List.of("--migration-dir", "shop", "--data-source", "main")).select(CONFS));

	}

}
