package io.jimble.db;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 複数データソースの結合テスト（要件 F-D-14 / 10.3）
 *
 * <p>
 * <b>サンプルアプリでは埋まらない分</b>である（要件 10.3）。
 * サンプルは DB を1つしか使わないので、
 * <b>2つ目を繋いだ瞬間に壊れていても誰も気づかない。</b>
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class MultiDataSourceIntegrationTest {

	/** サブ DB の名前 */
	private static final String SUB_DB = "jimble_test_sub";

	/** メイン DB の名前 */
	private static final String MAIN_DB = "jimble_test";

	@BeforeAll
	static void setUp () throws Exception {

		Conf.reload();

		DBUtil.load(Conf.conf().config(), MultiDataSourceIntegrationTest.class);

		try (DB db = DBUtil.getDB(SUB_DB)) {
			TestDdl.execute(db, """
				CREATE TABLE IF NOT EXISTS sub_item (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
			db.execute("TRUNCATE TABLE sub_item");
		}

		try (DB db = DBUtil.getMainDB()) {
			TestDdl.execute(db, """
				CREATE TABLE IF NOT EXISTS main_item (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
			db.execute("TRUNCATE TABLE main_item");
		}

	}

	@AfterAll
	static void tearDown () {

		DBUtil.stop();

	}

	@Test
	@DisplayName("設定に書いた数だけデータソースができる")
	void dataSources () throws Exception {

		List<DBSource> sources = DBUtil.getDataSourceList();

		assertTrue(sources.size() >= 2, sources.stream().map(source -> source.name()).toList().toString());

		assertNotNull(DBUtil.getDataSource(MAIN_DB));
		assertNotNull(DBUtil.getDataSource(SUB_DB));

		// main = true が付いているものが1つだけメインになる
		assertEquals(MAIN_DB, DBUtil.getMainDataSource().name());

	}

	@Test
	@DisplayName("サブ DB に書いたものはメインからは見えない")
	void isolated () throws Exception {

		try (DB sub = DBUtil.getDB(SUB_DB)) {
			sub.insert("INSERT INTO sub_item (name) VALUES (?)", "サブの行");
		}

		try (DB sub = DBUtil.getDB(SUB_DB)) {
			Data row = sub.select("SELECT name FROM sub_item WHERE name = ?", "サブの行").orElse(null);
			assertNotNull(row, "サブ DB から読めない");
			assertEquals("サブの行", row.getString("name"));
		}

		// メイン側に同じテーブルは無い。失敗は例外（2.0。要件 D-193）
		try (DB main = DBUtil.getMainDB()) {

			assertThrows(SqlExecuteException.class, () -> main.select("SELECT name FROM sub_item")
				, "メインからサブのテーブルが見えている");

		}

	}

	@Test
	@DisplayName("メインとサブは別の接続を使う")
	void separateConnections () throws Exception {

		try (DB main = DBUtil.getMainDB();
			 DB sub = DBUtil.getDB(SUB_DB)) {

			String mainSchema = main.select("SELECT %s AS schema_name".formatted(TestDdl.currentDatabase(main))).orElseThrow().getString("schema_name");
			String subSchema = sub.select("SELECT %s AS schema_name".formatted(TestDdl.currentDatabase(sub))).orElseThrow().getString("schema_name");

			assertNotEquals(mainSchema, subSchema);
			assertEquals(MAIN_DB, mainSchema);
			assertEquals(SUB_DB, subSchema);

		}

	}

	@Test
	@DisplayName("トランザクションはデータソースごとに独立している")
	void independentTransactions () throws Exception {

		try (DB main = DBUtil.getMainDB();
			 DB sub = DBUtil.getDB(SUB_DB)) {

			try (Tx mainTransaction = main.begin();
				 Tx subTransaction = sub.begin()) {

				main.insert("INSERT INTO main_item (name) VALUES (?)", "残る行");
				sub.insert("INSERT INTO sub_item (name) VALUES (?)", "消える行");

				/*
				 * 片方だけ戻す。
				 * 1つの Tx で両方を巻き込めるわけではない
				 * （分散トランザクションはやらない）。
				 */
				mainTransaction.commit();
				subTransaction.rollback();

			}

		}

		try (DB main = DBUtil.getMainDB()) {
			assertTrue(main.select("SELECT id FROM main_item WHERE name = ?", "残る行").isPresent()
				, "コミットしたのに残っていない");
		}

		try (DB sub = DBUtil.getDB(SUB_DB)) {
			assertTrue(sub.select("SELECT id FROM sub_item WHERE name = ?", "消える行").isEmpty()
				, "ロールバックしたのに残っている");
		}

	}

	@Test
	@DisplayName("ぶら下げたサブ DB を newSubDB で辿れる")
	void subDb () throws Exception {

		/*
		 * 「別のデータソース」（DBUtil.getDB）と
		 * 「データソースにぶら下がるサブ」（DB.newSubDB）は別物である。
		 * 前者は db { 名前 { ... } }、後者は db { 親 { subs { 名前 { ... } } } }。
		 *
		 * 名前で引く形が似ているので取り違えやすい。
		 * 取り違えると getSubDBSource が null を返し、
		 * その先で NullPointerException になる（何も言わずに落ちる）。
		 */
		try (DB main = DBUtil.getMainDB()) {

			try (DB sub = main.newSubDB("sub")) {

				assertEquals(SUB_DB
					, sub.select("SELECT %s AS schema_name".formatted(TestDdl.currentDatabase(sub))).orElseThrow().getString("schema_name"));

			}

		}

	}

	@Test
	@DisplayName("無いサブ DB を引いたら、何が悪いか分かる形で落ちる")
	void unknownSubDb () throws Exception {

		try (DB main = DBUtil.getMainDB()) {

			/*
			 * 名前を間違えると getSubDBSource が null を返し、
			 * DB のコンストラクタで NullPointerException になる。
			 * 「設定に無い」ことが分からないので、ここで固定して落とし穴に載せる。
			 */
			assertThrows(Exception.class, () -> main.newSubDB("そんな名前は無い"));

		}

	}

}
