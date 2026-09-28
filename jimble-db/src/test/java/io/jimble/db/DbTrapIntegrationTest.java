package io.jimble.db;

import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.data.SelectListResponse;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.exception.CodeException;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB で黙って間違えていたところ（要件 D-190）
 *
 * <p>
 * どれも 1.4 までは<b>例外もログも出ずに</b>、成功したように見えていた。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-db:pgTest}</p>
 */
@Tag("db")
@SuppressWarnings("removal")  // 1.x の書き方も確かめている（2.0 で消す。要件 D-192）
class DbTrapIntegrationTest {

	/** 表 */
	public static final class Trap extends Table {

		private Trap () {
			super(io.jimble.db.sql.TestSchema.INSTANCE, "trap_rows");
		}

		/** 実体 */
		public static Trap instance () {
			return new Trap();
		}

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** from_date（FROM を語として探しているかを見るための列名） */
		public static final Column from_date = new Column(instance(), "from_date", String.class, true, null, false);

	}

	@BeforeAll
	static void load () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), DbTrapIntegrationTest.class), "DB に接続できませんでした");

		DB db = DBUtil.getMainDB();
		db.execute("DROP TABLE IF EXISTS trap_rows");
		TestDdl.execute(db, """
			CREATE TABLE trap_rows (
				id         bigint unsigned auto_increment primary key,
				from_date  varchar(50) null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

	}

	@AfterAll
	static void stop () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS trap_rows");
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("DELETE FROM trap_rows");

	}

	private static long count () {

		return DBUtil.getMainDB().select("SELECT COUNT(*) AS c FROM trap_rows").getLong("c");

	}

	// region トランザクション

	@Test
	@DisplayName("D-190 合流した DBTransaction の rollback は、外の commit を DB_005 で断らせる（1.4 までは黙って commit）")
	void joinedRollbackMakesOuterRollbackOnly () throws Exception {

		DB db = DBUtil.getMainDB();

		try (DBTransaction outer = new DBTransaction(db)) {
			outer.beginTransaction();
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "outer");

			try (DBTransaction inner = new DBTransaction(db)) {
				inner.beginTransaction();
				db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "inner");
				inner.rollbackEndTransaction();
			}

			assertTrue(db.isTransaction(), "中の rollback が外のトランザクションを終わらせています");
			CodeException e = assertThrows(CodeException.class, outer::commitEndTransaction);
			assertEquals("DB_005", e.getCode(), e.getMessage());
			assertTrue(e.getMessage().contains("rollbackEndTransaction()"), e.getMessage());
		}

		assertEquals(0, count(), "巻き戻し専用なのに書き込まれています");

	}

	@Test
	@DisplayName("D-190 合流した側が例外で抜けたら、外は巻き戻し専用になる")
	void joinedExceptionMakesOuterRollbackOnly () throws Exception {

		DB db = DBUtil.getMainDB();

		try (DBTransaction outer = new DBTransaction(db)) {
			outer.beginTransaction();
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "outer");

			assertThrows(IllegalStateException.class, () -> DBTransaction.transaction(db, tx -> {
				db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "inner");
				throw new IllegalStateException("中で失敗");
			}));

			CodeException e = assertThrows(CodeException.class, outer::commitEndTransaction);
			assertEquals("DB_005", e.getCode());
		}

		assertEquals(0, count());

	}

	@Test
	@DisplayName("D-190 合流した側が commit すれば、外の commit で両方入る（合流は壊していない）")
	void joinedCommitStillWorks () throws Exception {

		DB db = DBUtil.getMainDB();

		DBTransaction.transaction(db, outer -> {
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "outer");
			DBTransaction.transaction(db, inner ->
				db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "inner"));
			assertTrue(db.isTransaction(), "中の commitEndTransaction が外を終わらせています");
		});

		assertEquals(2, count());

	}

	@Test
	@DisplayName("D-190 作ってから外が始まっても合流する（1.4 までは中の commitEndTransaction が外を終わらせていた）")
	void joinDecidedAtBegin () throws Exception {

		DB db = DBUtil.getMainDB();

		DBTransaction inner = new DBTransaction(db);   // まだ誰も始めていない
		try (DBTransaction outer = new DBTransaction(db)) {
			outer.beginTransaction();
			inner.beginTransaction();
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "x");
			inner.commitEndTransaction();
			assertTrue(db.isTransaction(), "中が外のトランザクションを終わらせています");
			outer.rollbackEndTransaction();
		}

		assertEquals(0, count(), "外で巻き戻したのに残っています");

	}

	@Test
	@DisplayName("D-190 失敗した文のあと、巻き戻しに成功したら例外を出さない（1.4 までは古い DB_999 が DB_002 で出ていた）")
	void rollbackAfterFailedStatementDoesNotThrow () throws Exception {

		DB db = DBUtil.getMainDB();

		try (DBTransaction tx = new DBTransaction(db)) {
			tx.beginTransaction();
			assertNull(db.select("SELECT * FROM trap_no_such_table"));
			assertTrue(db.isError());
			tx.rollbackEndTransaction();   // 例外にならない
		}

	}

	@Test
	@DisplayName("D-190 失敗した文のあとの commitEndTransaction は DB_004（古い DB_999 に上書きされない）")
	void commitAfterFailedStatementKeepsDb004 () throws Exception {

		DB db = DBUtil.getMainDB();

		try (DBTransaction tx = new DBTransaction(db)) {
			tx.beginTransaction();
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "x");
			db.select("SELECT * FROM trap_no_such_table");
			CodeException e = assertThrows(CodeException.class, tx::commitEndTransaction);
			assertEquals("DB_004", e.getCode(), e.getMessage());
		}

		assertEquals(0, count());

	}

	// endregion

	// region 読む側

	@Test
	@DisplayName("D-190 selectListPerformance は渡した builder を書き換えない")
	void performanceDoesNotMutateBuilder () {

		DB db = DBUtil.getMainDB();
		for (int i = 0; i < 5; i++) {
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "d" + i);
		}

		SelectBuilder builder = SQL.select(Trap.id, Trap.from_date)
			.from(Trap.instance())
			.where(Trap.from_date.like("d%"))
			.orderBy(Trap.id.asc())
			.limit(2);
		String before = builder.sql(db.dialect());

		List<Data> rows = db.selectListPerformance(builder);
		assertEquals(2, rows.size());
		assertEquals(before, builder.sql(db.dialect()), "builder が書き換わっています");

		SelectListResponse response = db.selectListWithRowCountPerformance(builder);
		assertEquals(5, response.rowCount());
		assertEquals(before, builder.sql(db.dialect()), "builder が書き換わっています");

	}

	@Test
	@DisplayName("D-190 selectListWithRowCount(String) は FROM を語として探す（from_date に当たらない）")
	void rowCountFindsFromAsWord () {

		DB db = DBUtil.getMainDB();
		for (int i = 0; i < 3; i++) {
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "d" + i);
		}

		SelectListResponse response = db.selectListWithRowCount(
			"SELECT from_date FROM trap_rows WHERE from_date LIKE ? ORDER BY from_date LIMIT 1", "d%");

		assertFalse(db.isError(), String.valueOf(db.getError()));
		assertEquals(1, response.list().size());
		assertEquals(3, response.rowCount());

	}

	@Test
	@DisplayName("D-190 一覧が失敗したら list() が null")
	void listFailureIsNull () {

		DB db = DBUtil.getMainDB();

		SelectListResponse response = db.selectListWithRowCount("SELECT 1 AS a FROM trap_no_such_table");
		assertNull(response.list());
		assertTrue(db.isError());

	}

	@Test
	@DisplayName("D-190 件数の SQL だけが失敗しても list() が null（1.4 までは 0 件に化けていた）")
	void rowCountFailureIsNotZero () {

		DB db = DBUtil.getMainDB();
		// PostgreSQL では「SELECT 1 ... UNION SELECT 文字列」の型が合わず、件数の SQL だけが落ちる
		org.junit.jupiter.api.Assumptions.assumeTrue(
			db.dialect().getClass().getSimpleName().toLowerCase().contains("postgres"), "PostgreSQL だけで見る");
		db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "d1");

		SelectListResponse response = db.selectListWithRowCount(
			"SELECT from_date FROM trap_rows UNION SELECT from_date FROM trap_rows");
		assertTrue(db.isError(), "件数の SQL が通っています（この確かめ方が使えません）");
		assertNull(response.list(), "件数が失敗したのに成功扱いです");
		assertEquals(0, response.rowCount());

	}

	@Test
	@DisplayName("D-190 SELECT 句に ? があっても件数のパラメータがずれない（1.4 までは後ろからしか捨てなかった）")
	void rowCountParamsAlignWithSelectPlaceholders () {

		DB db = DBUtil.getMainDB();
		for (int i = 0; i < 3; i++) {
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "d" + i);
		}
		db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "other");

		SelectListResponse response = db.selectListWithRowCount(
			"SELECT CAST(? AS CHAR(10)) AS tag, from_date FROM trap_rows WHERE from_date LIKE ? ORDER BY from_date LIMIT 1",
			"x", "d%");

		assertFalse(db.isError(), String.valueOf(db.getError()));
		assertEquals(1, response.list().size());
		assertEquals(3, response.rowCount(), "件数の WHERE に SELECT 句の値が入っています");

	}

	@Test
	@DisplayName("D-190 接続を取れないときのエラーはプールの言葉で出る（1.4 までは connection is null の NPE）")
	void connectionFailureIsReported () {

		DB db = DBUtil.getMainDB();
		HikariDataSource pool = (HikariDataSource) DBUtil.getMainDataSource().dataSource();
		pool.close();
		try {
			assertNull(db.select("SELECT 1"));
			assertTrue(db.isError());
			String message = db.getError().getMessage();
			assertNotNull(message);
			assertFalse(message.contains("null"), message);
			assertTrue(message.toLowerCase().contains("closed") || message.contains("コネクション"), message);
		} finally {
			// 次のテストのために繋ぎ直す
			DBUtil.stop();
			Conf.reload();
			assertTrue(DBUtil.load(Conf.conf().config(), DbTrapIntegrationTest.class));
		}

	}

	// endregion

}
