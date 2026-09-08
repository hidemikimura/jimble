package io.jimble.db.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方言そのものの確認（要件 F-D-30）
 *
 * <p>
 * <b>DB 接続なし。</b>出す文字列をそのまま固定する。
 * ここが緩いと「MySQL の出力を変えていない」を目で確かめるしかなくなる。
 * </p>
 */
class DialectTest {

	/* MySQL */
	private static final Dialect MYSQL = MySqlDialect.INSTANCE;

	/* PostgreSQL */
	private static final Dialect POSTGRESQL = PostgreSqlDialect.INSTANCE;

	// region 製品名

	@Test
	@DisplayName("設定に書ける製品名")
	void of () {

		assertEquals(MySqlDialect.NAME, Dialects.of("mysql").name());
		assertEquals(MySqlDialect.NAME, Dialects.of("MySQL").name());
		assertEquals(MySqlDialect.NAME, Dialects.of("mariadb").name());
		assertEquals(PostgreSqlDialect.NAME, Dialects.of("postgresql").name());
		assertEquals(PostgreSqlDialect.NAME, Dialects.of("postgres").name());
		assertEquals(PostgreSqlDialect.NAME, Dialects.of("pgsql").name());

	}

	@Test
	@DisplayName("知らない製品名は起動時に落とす")
	void ofUnknown () {

		assertThrows(DialectException.class, () -> Dialects.of("oracle"));

	}

	// endregion

	// region 識別子

	@Test
	@DisplayName("識別子の囲み方")
	void identifier () {

		assertEquals("`site`", MYSQL.identifier("site"));
		assertEquals("\"site\"", POSTGRESQL.identifier("site"));

	}

	@Test
	@DisplayName("囲み文字が名前に入っていたら二重にする")
	void identifierEscape () {

		assertEquals("`a``b`", MYSQL.identifier("a`b"));
		assertEquals("\"a\"\"b\"", POSTGRESQL.identifier("a\"b"));

	}

	// endregion

	// region upsert（生 SQL 用）

	@Test
	@DisplayName("重複したら更新する句")
	void upsert () {

		assertEquals(
			" ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `note` = VALUES(`note`)"
			, Sqls.upsert(MYSQL, List.of("value_key"), "value", "note"));

		assertEquals(
			" ON CONFLICT (\"value_key\") DO UPDATE SET \"value\" = EXCLUDED.\"value\""
				+ ", \"note\" = EXCLUDED.\"note\""
			, Sqls.upsert(POSTGRESQL, List.of("value_key"), "value", "note"));

	}

	@Test
	@DisplayName("PostgreSQL は「どのキーで重複を見るか」が分からないと書けない")
	void upsertWithoutKeys () {

		// MySQL は要らない
		assertEquals(" ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)"
			, Sqls.upsert(MYSQL, List.of(), "value"));

		assertThrows(DialectException.class
			, () -> Sqls.upsert(POSTGRESQL, List.of(), "value"));

	}

	@Test
	@DisplayName("重複を無視する INSERT は前と後ろで場所が違う")
	void insertIgnore () {

		assertEquals("INSERT IGNORE INTO `db_lock`", Sqls.insertIgnoreInto(MYSQL, "db_lock"));
		assertEquals("", Sqls.insertIgnoreTail(MYSQL));

		assertEquals("INSERT INTO \"db_lock\"", Sqls.insertIgnoreInto(POSTGRESQL, "db_lock"));
		assertEquals(" ON CONFLICT DO NOTHING", Sqls.insertIgnoreTail(POSTGRESQL));

	}

	// endregion

	// region 関数

	@Test
	@DisplayName("名前だけ違う関数")
	void functionNames () {

		assertEquals("RAND", MYSQL.function(SqlFunction.RAND));
		assertEquals("RANDOM", POSTGRESQL.function(SqlFunction.RAND));

		assertEquals("IFNULL", MYSQL.function(SqlFunction.IFNULL));
		assertEquals("COALESCE", POSTGRESQL.function(SqlFunction.IFNULL));

		assertEquals("RAND()", MYSQL.call(SqlFunction.RAND));
		assertEquals("RANDOM()", POSTGRESQL.call(SqlFunction.RAND));

	}

	@Test
	@DisplayName("CONCAT は NULL の扱いが逆なので、PostgreSQL では || にする")
	void concat () {

		StringBuilder mysql = new StringBuilder();
		MYSQL.concat(mysql, List.of(() -> mysql.append("a"), () -> mysql.append("b")));
		assertEquals("CONCAT(a,b)", mysql.toString());

		StringBuilder pg = new StringBuilder();
		POSTGRESQL.concat(pg, List.of(() -> pg.append("a"), () -> pg.append("b")));
		assertEquals("(a || b)", pg.toString());

	}

	@Test
	@DisplayName("DATE_FORMAT は書式の言語が違うので、置き換えずに投げる")
	void dateFormat () {

		StringBuilder mysql = new StringBuilder();
		MYSQL.dateFormat(mysql, () -> mysql.append("created_at"), "%Y-%m-%d");
		assertEquals("DATE_FORMAT(created_at, '%Y-%m-%d')", mysql.toString());

		StringBuilder pg = new StringBuilder();
		assertThrows(DialectException.class
			, () -> POSTGRESQL.dateFormat(pg, () -> pg.append("created_at"), "%Y-%m-%d"));

	}

	@Test
	@DisplayName("全文検索は PostgreSQL に無いので投げる")
	void fullTextMatch () {

		StringBuilder pg = new StringBuilder();
		assertThrows(DialectException.class
			, () -> POSTGRESQL.fullTextMatch(pg, () -> pg.append("body"), ""));

	}

	@Test
	@DisplayName("桁を指定した四捨五入は PostgreSQL だけ numeric に寄せる")
	void round () {

		StringBuilder mysql = new StringBuilder();
		MYSQL.round(mysql, () -> mysql.append("price"), 2);
		assertEquals("ROUND(price, 2)", mysql.toString());

		StringBuilder pg = new StringBuilder();
		POSTGRESQL.round(pg, () -> pg.append("price"), 2);
		assertEquals("ROUND((price)::numeric, 2)", pg.toString());

	}

	// endregion

	// region 期間

	@Test
	@DisplayName("いまから ? 秒前")
	void intervalFromNow () {

		assertEquals("CURRENT_TIMESTAMP + INTERVAL - ? SECOND"
			, MYSQL.intervalFromNow("SECOND", true));
		assertEquals("CURRENT_TIMESTAMP + INTERVAL + ? SECOND"
			, MYSQL.intervalFromNow("SECOND", false));

		assertEquals("CURRENT_TIMESTAMP - (? * INTERVAL '1 SECOND')"
			, POSTGRESQL.intervalFromNow("SECOND", true));
		assertEquals("CURRENT_TIMESTAMP + (? * INTERVAL '1 SECOND')"
			, POSTGRESQL.intervalFromNow("SECOND", false));

	}

	// endregion

	// region JSON

	@Test
	@DisplayName("JSON のパス")
	void jsonExtract () {

		StringBuilder mysql = new StringBuilder();
		MYSQL.jsonExtract(mysql, () -> mysql.append("data"), "$.a.b", true);
		assertTrue(mysql.toString().contains("data"), mysql.toString());

		StringBuilder pg = new StringBuilder();
		POSTGRESQL.jsonExtract(pg, () -> pg.append("data"), "$.a.b", true);
		assertEquals("(data -> 'a' ->> 'b')", pg.toString());

	}

	@Test
	@DisplayName("読み替えられない JSON のパスは投げる（黙って NULL を返さない）")
	void jsonExtractUnsupported () {

		for (String path : List.of("$.a[0]", "$.*", "$.\"a.b\"", "a.b")) {
			StringBuilder pg = new StringBuilder();
			assertThrows(DialectException.class
				, () -> POSTGRESQL.jsonExtract(pg, () -> pg.append("data"), path, true)
				, path);
		}

	}

	// endregion

	// region 型

	@Test
	@DisplayName("JSON と地理空間の型名")
	void columnTypes () {

		assertTrue(MYSQL.isJsonType("JSON"));
		assertFalse(MYSQL.isJsonType("jsonb"));

		assertTrue(POSTGRESQL.isJsonType("json"));
		assertTrue(POSTGRESQL.isJsonType("jsonb"));

		assertTrue(MYSQL.isGeometryType("GEOMETRY"));
		assertFalse(MYSQL.isGeometryType("VARBINARY"));

		assertTrue(POSTGRESQL.isGeometryType("geometry"));
		assertFalse(POSTGRESQL.isGeometryType("bytea"));

	}

	// endregion

	// region 接続

	@Test
	@DisplayName("データベースを作るときの繋ぎ先")
	void maintenanceUrl () {

		assertEquals("jdbc:mariadb://127.0.0.1:3306"
			, MYSQL.maintenanceUrl("jdbc:mariadb://127.0.0.1:3306/jimble_test"));

		assertEquals("jdbc:mariadb://127.0.0.1:3306?useSSL=false"
			, MYSQL.maintenanceUrl("jdbc:mariadb://127.0.0.1:3306/jimble_test?useSSL=false"));

		assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres"
			, POSTGRESQL.maintenanceUrl("jdbc:postgresql://127.0.0.1:5432/jimble_test"));

	}

	@Test
	@DisplayName("データベース名の無い URL では、ホストを削らない")
	void maintenanceUrlWithoutDatabase () {

		assertEquals("jdbc:mariadb://127.0.0.1:3306"
			, MYSQL.maintenanceUrl("jdbc:mariadb://127.0.0.1:3306"));

		assertEquals("jdbc:postgresql://127.0.0.1:5432"
			, POSTGRESQL.maintenanceUrl("jdbc:postgresql://127.0.0.1:5432"));

	}

	@Test
	@DisplayName("「そんなデータベースは無い」の見分け方")
	void isUnknownDatabase () {

		assertTrue(MYSQL.isUnknownDatabase("Unknown database 'jimble_test'"));
		assertFalse(MYSQL.isUnknownDatabase("Access denied for user"));
		assertFalse(MYSQL.isUnknownDatabase(null));

		assertTrue(POSTGRESQL.isUnknownDatabase("FATAL: database \"jimble_test\" does not exist"));
		assertFalse(POSTGRESQL.isUnknownDatabase("ERROR: relation \"site\" does not exist"));
		assertFalse(POSTGRESQL.isUnknownDatabase(null));

	}

	// endregion

	// region ロック待ち

	@Test
	@DisplayName("ロック待ちの上限")
	void setLockTimeoutSql () {

		assertEquals("SET SESSION innodb_lock_wait_timeout = 30", MYSQL.setLockTimeoutSql(30));

		// PostgreSQL はミリ秒。SET LOCAL にしないと接続に残る
		assertEquals("SET LOCAL lock_timeout = 30000", POSTGRESQL.setLockTimeoutSql(30));

	}

	// endregion

}
