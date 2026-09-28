package io.jimble.db;

import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.data.SelectListResponse;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

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
		DBUtil.load(Conf.conf().config(), DbTrapIntegrationTest.class);

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

		return DBUtil.getMainDB().select("SELECT COUNT(*) AS c FROM trap_rows").orElseThrow().getLong("c");

	}

	// region トランザクション
	//
	// 合流（入れ子）の約束は TxIntegrationTest で見る。1.x の DBTransaction の確かめは 2.0 で消した（要件 D-193）

	@Test
	@DisplayName("D-190 失敗した文のあと、巻き戻しに成功したら例外を出さない（1.4 までは古い DB_999 が DB_002 で出ていた）")
	void rollbackAfterFailedStatementDoesNotThrow () {

		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			assertThrows(SqlExecuteException.class, () -> db.select("SELECT * FROM trap_no_such_table"));
			tx.rollback();   // 例外にならない
		}

	}

	@Test
	@DisplayName("D-190 失敗した文のあとの commit は DB_004（古い DB_999 に上書きされない）")
	void commitAfterFailedStatementKeepsDb004 () {

		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "x");
			assertThrows(SqlExecuteException.class, () -> db.select("SELECT * FROM trap_no_such_table"));
			TransactionException e = assertThrows(TransactionException.class, tx::commit);
			assertEquals("DB_004", e.getCode(), e.getMessage());
			assertTrue(e.getMessage().contains("trap_no_such_table"), "失敗した文のエラーが出ていません: " + e.getMessage());
		}

		assertEquals(0, count());

	}

	// endregion

	// region ロック（要件 D-193）

	@Test
	@DisplayName("D-193 DBLock.lock はトランザクションの外で呼ぶと例外（1.x は true を返して何も守らなかった）")
	void dbLockOutsideTransactionThrows () {

		DB db = DBUtil.getMainDB();
		io.jimble.db.lock.DBLock.create(db, "trap-lock");

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> io.jimble.db.lock.DBLock.lock(db, "trap-lock"));
		assertTrue(e.getMessage().contains("トランザクション"), e.getMessage());

		// 中なら取れる
		db.transaction(tx -> io.jimble.db.lock.DBLock.lock(db, "trap-lock"));

	}

	@Test
	@DisplayName("D-193 DBLock.lock はキーが無ければ例外（1.x は false）")
	void dbLockMissingKeyThrows () {

		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> io.jimble.db.lock.DBLock.lock(db, "trap-no-such-key-" + System.nanoTime()));
			assertTrue(e.getMessage().contains("DBLock.create"), e.getMessage());
			assertFalse(tx.isFinished());
		}

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

		assertEquals(1, response.list().size());
		assertEquals(3, response.rowCount());

	}

	@Test
	@DisplayName("D-193 一覧が失敗したら例外（1.x は list() が null）")
	void listFailureThrows () {

		DB db = DBUtil.getMainDB();

		assertThrows(SqlExecuteException.class, () -> db.selectListWithRowCount("SELECT 1 AS a FROM trap_no_such_table"));

	}

	@Test
	@DisplayName("D-193 FROM の無い SQL の selectListWithRowCount は例外")
	void rowCountWithoutFromThrows () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException e = assertThrows(SqlExecuteException.class, () -> db.selectListWithRowCount("SELECT 1 AS a"));
		assertTrue(e.getMessage().contains("FROM"), e.getMessage());

	}

	@Test
	@DisplayName("D-190 件数の SQL だけが失敗しても例外（1.4 までは 0 件に化けていた）")
	void rowCountFailureIsNotZero () {

		DB db = DBUtil.getMainDB();
		// PostgreSQL では「SELECT 1 ... UNION SELECT 文字列」の型が合わず、件数の SQL だけが落ちる
		org.junit.jupiter.api.Assumptions.assumeTrue(
			db.dialect().getClass().getSimpleName().toLowerCase().contains("postgres"), "PostgreSQL だけで見る");
		db.insert("INSERT INTO trap_rows (from_date) VALUES (?)", "d1");

		// 一覧だけなら通る（確かめ方の前提）
		assertEquals(1, db.selectList("SELECT from_date FROM trap_rows UNION SELECT from_date FROM trap_rows").size());

		assertThrows(SqlExecuteException.class, () -> db.selectListWithRowCount(
			"SELECT from_date FROM trap_rows UNION SELECT from_date FROM trap_rows"), "件数が失敗したのに成功扱いです");

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
			SqlExecuteException e = assertThrows(SqlExecuteException.class, () -> db.select("SELECT 1"));
			String message = e.getCause().getMessage();
			assertNotNull(message);
			assertFalse(message.contains("null"), message);
			assertTrue(message.toLowerCase().contains("closed") || message.contains("コネクション"), message);
		} finally {
			// 次のテストのために繋ぎ直す
			DBUtil.stop();
			Conf.reload();
			DBUtil.load(Conf.conf().config(), DbTrapIntegrationTest.class);
		}

	}

	// endregion

}
