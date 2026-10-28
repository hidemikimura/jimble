package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 起動時の確かめを並べる（{@code db.startup_parallelism}）
 *
 * <p>
 * 同じ作りの DB を数十並べると、起動時の確かめ（つなぐ・版を読む・管理の表を作る・マイグレーション）が
 * データソースの数だけ直列に延びる。並べても、1つずつのときと同じに読み込まれること、失敗すれば起動が止まることを確かめる。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class ParallelLoadIntegrationTest {

	/** 足すデータソース */
	private static final List<String> SHOPS = IntStream.range(0, 6).mapToObj(i -> "jimble_par" + i).toList();

	@AfterEach
	void tearDown () {

		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("並べて読んでも、全部のデータソースが読み込まれ、主は main = true のもの、管理の表もそれぞれにできる")
	void loadsAllInParallel () {

		load(withShops("db.startup_parallelism = 4"));

		for (String name : SHOPS) {
			assertTrue(DBUtil.getDataSourceList().stream().anyMatch(source -> source.name().equals(name)), name);
			try (DB db = DBUtil.getDB(name)) {
				assertTrue(db.selectList(db.dialect().tableCommentsSql()).stream()
					.anyMatch(row -> "db_lock".equalsIgnoreCase(row.getString("table_name"))), name + " に管理の表が無い");
			}
		}

		assertEquals("jimble_test", DBUtil.getMainDataSource().name());
		assertTrue(DBUtil.healthCheck());

	}

	@Test
	@DisplayName("並べて読んでも、required のデータソースがつながらなければ起動が止まる")
	void failureStopsStartup () {

		Config config = withShops("db.startup_parallelism = 4");
		Config main = config.getConfig("db.jimble_test");
		Config broken = main
			.withoutPath("create_database_sql")
			.withValue("main", ConfigValueFactory.fromAnyRef(false))
			.withValue("url", ConfigValueFactory.fromAnyRef(main.getString("url").replaceFirst("/jimble_test(\\?|$)", "/jimble_par_missing$1")));

		assertThrows(SqlExecuteException.class, () -> load(config.withValue("db.jimble_par_missing", broken.root())));

	}

	@Test
	@DisplayName("db.startup_parallelism は 1 以上")
	void parallelismMustBePositive () {

		assertThrows(IllegalStateException.class, () -> load(withShops("db.startup_parallelism = 0")));

	}

	// region ヘルパー

	/**
	 * 主の設定を写して、データソースを足す
	 *
	 * @param hocon	足す設定
	 * @return	設定全体
	 */
	private static Config withShops (String hocon) {

		Conf.reload();
		Config config = ConfigFactory.parseString(hocon + "\nmigration.on_startup = false").withFallback(Conf.conf().config());
		Config main = config.getConfig("db.jimble_test");

		for (String name : SHOPS) {
			Config shop = main
				.withValue("main", ConfigValueFactory.fromAnyRef(false))
				.withValue("url", ConfigValueFactory.fromAnyRef(main.getString("url").replaceFirst("/jimble_test(\\?|$)", "/" + name + "$1")))
				.withValue("maximum_pool_size", ConfigValueFactory.fromAnyRef(2))
				.withoutPath("subs");
			if (main.hasPath("create_database_sql")) {
				shop = shop.withValue("create_database_sql"
					, ConfigValueFactory.fromAnyRef(main.getString("create_database_sql").replace("jimble_test", name)));
			}
			config = config.withValue("db." + name, shop.root());
		}

		return config;

	}

	private static void load (Config config) {

		DBUtil.stop();
		Conf.replace(config);
		DBUtil.load(config, ParallelLoadIntegrationTest.class);

	}

	// endregion

}
