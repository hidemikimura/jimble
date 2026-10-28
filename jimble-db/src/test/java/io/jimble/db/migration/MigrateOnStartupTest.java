package io.jimble.db.migration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * データソースごとの起動時の適用（{@code db.<名前>.migrate_on_startup}）
 */
class MigrateOnStartupTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	@Test
	@DisplayName("書かなければ migration.on_startup に従う")
	void followsGlobal () {

		Config config = conf("migration.on_startup = true", "a { url = \"jdbc:x\" }");

		assertTrue(MigrationConf.isOnStartup(DBUtil.readDataSourceConfs(config).get("a")));

		config = conf("migration.on_startup = false", "a { url = \"jdbc:x\" }");

		assertFalse(MigrationConf.isOnStartup(DBUtil.readDataSourceConfs(config).get("a")));

	}

	@Test
	@DisplayName("書けば、そのデータソースだけ migration.on_startup より優先する")
	void ownWins () {

		Config config = conf("migration.on_startup = true", """
			a { url = "jdbc:x", migrate_on_startup = false }
			b { url = "jdbc:x" }
			""");

		assertFalse(MigrationConf.isOnStartup(DBUtil.readDataSourceConfs(config).get("a")));
		assertTrue(MigrationConf.isOnStartup(DBUtil.readDataSourceConfs(config).get("b")));

		config = conf("migration.on_startup = false", "a { url = \"jdbc:x\", migrate_on_startup = true }");

		assertTrue(MigrationConf.isOnStartup(DBUtil.readDataSourceConfs(config).get("a")));

	}

	@Test
	@DisplayName("全体で切っていても、migrate_on_startup = true のデータソースがあれば起動時の適用を組み込む")
	void anyOnStartup () {

		assertFalse(Migration.anyOnStartup(conf("migration.on_startup = false", "a { url = \"jdbc:x\" }")));
		assertFalse(Migration.anyOnStartup(conf("migration.on_startup = false", "a { url = \"jdbc:x\", migrate_on_startup = false }")));
		assertTrue(Migration.anyOnStartup(conf("migration.on_startup = false", "a { url = \"jdbc:x\", migrate_on_startup = true }")));
		assertTrue(Migration.anyOnStartup(conf("migration.on_startup = true", "a { url = \"jdbc:x\", migrate_on_startup = false }")));

	}

	/**
	 * 設定を差し替える
	 *
	 * @param migration	migration の設定
	 * @param db		db の中身
	 * @return	設定全体
	 */
	private static Config conf (String migration, String db) {

		Conf.reload();
		Config config = ConfigFactory.parseString(migration + "\ndb {\n" + db + "\n}");
		Conf.replace(config.withFallback(Conf.conf().config()).resolve());

		return config;

	}

}
