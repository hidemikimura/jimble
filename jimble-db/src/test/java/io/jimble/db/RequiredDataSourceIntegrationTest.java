package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 落ちても全体を止めないデータソース（{@code db.<名前>.required = false}）
 *
 * <p>
 * 店の DB が1つ落ちただけで、Web 全体がロードバランサーから外れたり（死活監視が false）、
 * 起動できなくなったりしないようにする。{@code required = false} のデータソースは、
 * 起動時につながらなくても起動を続け、DB が戻れば使える。死活監視は {@code required = true} のものだけを見る。
 * </p>
 *
 * <p>
 * 「落ちている DB」は、<b>まだ無いデータベース</b>を指して作る（サーバーには届くが、つながらない）。
 * あとでそのデータベースを作れば「戻った」ことになる。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class RequiredDataSourceIntegrationTest {

	/** 落ちているデータソース（まだ無いデータベースを指す） */
	private static final String DOWN = "jimble_req_down";

	/** DB が戻ってから使えるようになるまでの上限 */
	private static final Duration RECOVER_WITHIN = Duration.ofSeconds(15);

	@AfterEach
	void tearDown () {

		DBUtil.stop();
		dropDownDatabase();
		Conf.reload();

	}

	@Test
	@DisplayName("書かなければ（required = true）、これまでどおり、つながらないデータソースで起動が止まる")
	void requiredByDefault () {

		assertThrows(SqlExecuteException.class, () -> load(down("")));

	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "hikari", "agroal" })
	@DisplayName("required = false なら、つながらなくても起動を続け、DB が戻れば使える")
	void notRequiredStartsAndRecovers (String pool) {

		load(down("required = false, connection_pool_type = " + pool));

		// 起動は続き、データソースはある（使えばつながらない例外）
		assertTrue(DBUtil.getDataSourceList().stream().anyMatch(source -> source.name().equals(DOWN)));
		assertThrows(SqlExecuteException.class, () -> {
			try (DB db = DBUtil.getDB(DOWN)) {
				db.select("SELECT 1");
			}
		});

		// 死活監視は required のものだけを見るので true。データソースごとには分かる
		assertTrue(DBUtil.healthCheck(), "required = false のデータソースが落ちているだけで、死活監視が false");
		Map<String, Boolean> all = DBUtil.healthCheckAll();
		assertFalse(all.get(DOWN));
		assertTrue(all.get("jimble_test"));

		// DB が戻る（データベースを作る）と、そのまま使える
		// （HikariCP は、つながらないあいだ作り直す間隔を最大 5 秒まで延ばすので、戻ってから数秒かかる）
		createDownDatabase();

		assertTrue(eventually(() -> {
			try (DB db = DBUtil.getDB(DOWN)) {
				return db.select("SELECT 1 AS v").isPresent();
			}
		}, RECOVER_WITHIN), "戻ったのに使えない");
		assertTrue(DBUtil.healthCheckAll().get(DOWN));

	}

	@Test
	@DisplayName("required = true のデータソースが落ちていれば、死活監視は false")
	void requiredDownMakesHealthCheckFail () throws Exception {

		createDownDatabase();
		load(down("required = true"));

		assertTrue(DBUtil.healthCheck());

		// 落とす（プールを閉じる）
		((Closeable) DBUtil.getDataSource(DOWN).dataSource()).close();

		assertFalse(DBUtil.healthCheck(), "required のデータソースが落ちているのに true");
		assertFalse(DBUtil.healthCheckAll().get(DOWN));

	}

	@Test
	@DisplayName("主のデータソースは required = false にできない")
	void mainMustBeRequired () {

		Conf.reload();
		Config config = ConfigFactory.parseString("db.jimble_test.required = false").withFallback(Conf.conf().config());

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> load(config));
		assertTrue(thrown.getMessage().contains("required"), thrown.getMessage());

	}

	// region ヘルパー

	/**
	 * 主の設定を写して、まだ無いデータベースを指すデータソースを足す
	 *
	 * @param hocon	そのデータソースに足す設定
	 * @return	設定全体
	 */
	private static Config down (String hocon) {

		Conf.reload();
		Config base = Conf.conf().config();
		Config main = base.getConfig("db.jimble_test");
		String url = main.getString("url").replaceFirst("/jimble_test(\\?|$)", "/" + DOWN + "$1");

		Config down = ConfigFactory.parseString(hocon)
			.withFallback(main
				.withoutPath("create_database_sql")
				.withValue("main", ConfigValueFactory.fromAnyRef(false))
				.withValue("url", ConfigValueFactory.fromAnyRef(url))
				.withValue("connection_timeout", ConfigValueFactory.fromAnyRef("2s")));

		return base.withoutPath("db.jimble_test_sub").withValue("db." + DOWN, down.root());

	}

	/**
	 * できるまで試す
	 *
	 * @param check		試すこと（例外は「まだ」）
	 * @param within	上限
	 * @return	できた場合 = true
	 */
	private static boolean eventually (BooleanSupplier check, Duration within) {

		long deadline = System.nanoTime() + within.toNanos();

		while (System.nanoTime() < deadline) {
			try {
				if (check.getAsBoolean()) {
					return true;
				}
			} catch (RuntimeException notYet) {
				// まだつながらない
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		return false;

	}

	private static void load (Config config) {

		DBUtil.stop();
		Conf.replace(config);
		DBUtil.load(config, RequiredDataSourceIntegrationTest.class);

	}

	private static void createDownDatabase () {

		try (DB db = DBUtil.getDB("jimble_test")) {
			db.execute("CREATE DATABASE " + DOWN);
		} catch (RuntimeException notLoaded) {
			Conf.reload();
			DBUtil.load(Conf.conf().config(), RequiredDataSourceIntegrationTest.class);
			try (DB db = DBUtil.getMainDB()) {
				db.execute("CREATE DATABASE " + DOWN);
			}
			DBUtil.stop();
		}

	}

	private static void dropDownDatabase () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), RequiredDataSourceIntegrationTest.class);
		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP DATABASE IF EXISTS " + DOWN);
		}
		DBUtil.stop();

	}

	// endregion

}
