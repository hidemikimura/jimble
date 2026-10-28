package io.jimble.db.cli;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 並べた DB に CLI からマイグレーションを当てる（{@code migrate} のオプションと {@code migrateCheck}）
 *
 * <p>
 * 主（{@code jimble_test}）ともう1つ（{@code jimble_test_sub}）に同じ {@code migration_dir} を付け、
 * デプロイの段で CLI から当てる使い方を確かめる。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class JimbleDbCliIntegrationTest {

	/** 主 */
	private static final String MAIN = "jimble_test";

	/** もう1つ */
	private static final String OTHER = "jimble_test_sub";

	/** 当てるファイル */
	private static final String FILE = "shared_1.sql";

	/** CLI の結果の一覧 */
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	@AfterAll
	static void reset () {

		withDatabases(JimbleDbCliIntegrationTest::clean);
		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void cleanBoth () {

		withDatabases(JimbleDbCliIntegrationTest::clean);

	}

	@Test
	@DisplayName("--data-source で絞った先にだけ当たる")
	void dataSource () {

		assertEquals(JimbleDbCli.EXIT_SUCCESS, cli("migrate", "--data-source", OTHER));

		withDatabases(db -> assertEquals(db.getDBName().equals(OTHER), hasTable(db, "shared_item"), db.getDBName()));

	}

	@Test
	@DisplayName("--migration-dir で、そのフォルダを使うデータソース全部に当たり、結果の一覧が出る")
	void migrationDir () {

		assertEquals(JimbleDbCli.EXIT_SUCCESS, cli("migrate", "--migration-dir", "shop", "--parallel", "2"));

		withDatabases(db -> assertTrue(hasTable(db, "shared_item"), db.getDBName()));
		assertTrue(output().contains(MAIN) && output().contains(OTHER) && output().contains("完了（1 件当てた）"), output());

	}

	@Test
	@DisplayName("既定では最初の失敗で止め、残りには当てない。0 以外で終わる")
	void stopsAtFirstFailure () {

		breakMain();

		assertEquals(JimbleDbCli.EXIT_FAILURE, cli("migrate"));

		withDatabases(db -> {
			if (db.getDBName().equals(OTHER)) {
				assertFalse(hasTable(db, "shared_item"), "最初の失敗のあとも当てている");
			}
		});
		assertTrue(output().contains("失敗") && output().contains("未実行（前の失敗で止めた）"), output());

	}

	@Test
	@DisplayName("--keep-going なら、1つ失敗してもほかに当て、一覧と 0 以外の終了コードを出す")
	void keepGoing () {

		breakMain();

		assertEquals(JimbleDbCli.EXIT_FAILURE, cli("migrate", "--keep-going"));

		withDatabases(db -> {
			if (db.getDBName().equals(OTHER)) {
				assertTrue(hasTable(db, "shared_item"), "失敗のあとのデータソースに当たっていない");
			}
		});
		assertTrue(output().contains("失敗") && output().contains("完了（1 件当てた）"), output());

	}

	@Test
	@DisplayName("migrateCheck は、1つの DB だけ未適用ならそれを出して 0 以外で終わる。そろえば 0")
	void migrateCheck () {

		assertEquals(JimbleDbCli.EXIT_SUCCESS, cli("migrate", "--data-source", MAIN));

		out.reset();
		assertEquals(JimbleDbCli.EXIT_FAILURE, cli("migrateCheck"));
		assertTrue(output().contains(OTHER) && output().contains("未適用: " + FILE), output());

		assertEquals(JimbleDbCli.EXIT_SUCCESS, cli("migrate"));

		out.reset();
		assertEquals(JimbleDbCli.EXIT_SUCCESS, cli("migrateCheck"), output());
		assertTrue(output().contains("そろっている"), output());

	}

	@Test
	@DisplayName("migrateCheck は DB に書かない（記録の表が無くても作らない）")
	void migrateCheckDoesNotWrite () {

		withDatabases(db -> {
			db.execute("DROP TABLE IF EXISTS migration_history");
			db.execute("DROP TABLE IF EXISTS migration");
		});

		assertEquals(JimbleDbCli.EXIT_FAILURE, cli("migrateCheck"));

		withDatabases(db -> assertFalse(hasTable(db, "migration"), db.getDBName() + " に記録の表を作った"));

	}

	@Test
	@DisplayName("絞ったときは、選ばなかったデータソースにつながない（つながらない DB があっても当たる）")
	void doesNotConnectToOthers () {

		String broken = "db.broken { url = \"%s\", username = \"x\", password = \"x\", connection_timeout = 1s, migration_dir = \"other\" }"
			.formatted(isPostgres() ? "jdbc:postgresql://127.0.0.1:1/x" : "jdbc:mariadb://127.0.0.1:1/x");

		assertEquals(JimbleDbCli.EXIT_FAILURE, cliWith(broken, "migrate"), "絞らなければ、つながらない DB で止まる");
		assertEquals(JimbleDbCli.EXIT_SUCCESS, cliWith(broken, "migrate", "--migration-dir", "shop"));

	}

	// region ヘルパー

	/**
	 * CLI を動かす
	 *
	 * @param args	引数
	 * @return	終了コード
	 */
	private int cli (String... args) {

		return cliWith("", args);

	}

	/**
	 * 設定を足して CLI を動かす
	 *
	 * @param hocon	足す設定
	 * @param args	引数
	 * @return	終了コード
	 */
	private int cliWith (String hocon, String... args) {

		return JimbleDbCli.run(args, config(hocon), new PrintStream(out, true, StandardCharsets.UTF_8));

	}

	private String output () {

		return out.toString(StandardCharsets.UTF_8);

	}

	/**
	 * 2つに同じフォルダを付けた設定（CLI はこれだけを見る。Conf にも入れる）
	 *
	 * @param hocon	足す設定
	 * @return	設定全体
	 */
	private static Config config (String hocon) {

		Conf.reload();
		// ほかのテストが Migration.install() を呼んでいても、load の中では当てない（CLI が当てる）
		Conf.replace(ConfigFactory.parseString(hocon + """

			migration.on_startup = false
			migration.resource_dir = "migration_shared_test"
			db.jimble_test.migration_dir = "shop"
			db.jimble_test_sub.migration_dir = "shop"
			""").withFallback(Conf.conf().config()).resolve());

		return Conf.conf().config();

	}

	/**
	 * 主を、当てると失敗する状態にする（同じ名前の表を先に作っておく）
	 */
	private static void breakMain () {

		withDatabases(db -> {
			if (db.getDBName().equals(MAIN)) {
				db.execute("create table shared_item (id int primary key)");
			}
		});

	}

	/**
	 * 2つの DB を開いて何かする（CLI は終わりに DB を閉じるので、そのたびに開き直す）
	 *
	 * @param action	すること
	 */
	private static void withDatabases (Consumer<DB> action) {

		DBUtil.stop();
		DBUtil.load(config(""), JimbleDbCliIntegrationTest.class);

		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				action.accept(db);
			}
		}

		DBUtil.stop();

	}

	private static void clean (DB db) {

		db.execute("DROP TABLE IF EXISTS shared_tag");
		db.execute("DROP TABLE IF EXISTS shared_item");
		// ほかのテストの記録が残っていると、migrateCheck が「余分」として出すので、全部消す
		if (hasTable(db, "migration")) {
			db.execute("DELETE FROM migration");
		}
		if (hasTable(db, "migration_history")) {
			db.execute("DELETE FROM migration_history");
		}

	}

	private static boolean hasTable (DB db, String table) {

		return db.select("""
			SELECT COUNT(*) AS cnt FROM information_schema.tables
			WHERE table_schema = %s AND table_name = ?
			""".formatted(TestDdl.currentSchema(db)), table).orElseThrow().getLong("cnt") > 0;

	}

	private static boolean isPostgres () {

		return Conf.conf().config().getString("db.jimble_test.url").startsWith("jdbc:postgresql:");

	}

	// endregion

}
