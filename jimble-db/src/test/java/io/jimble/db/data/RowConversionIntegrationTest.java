package io.jimble.db.data;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 結果の行を Data にするところ（CPU を減らした 2.7.4 の変更で、値が変わらないこと）
 *
 * <ul>
 *   <li>時差を持たない日時（MySQL の DATETIME・PostgreSQL の timestamp）を LocalDateTime で読んでから Timestamp にする。
 *       ドライバの getObject と同じ値になること</li>
 *   <li>入れ子の Data を列ごとに引き直さない。テーブルの列が飛び飛びに並んでも、同じ Data に入ること</li>
 *   <li>db.remove_all_null_table_data を結果ごとに1回だけ読む。これまでどおり効くこと</li>
 * </ul>
 */
@Tag("db")
class RowConversionIntegrationTest {

	private static DB db;

	private static boolean mysql;

	@BeforeAll
	static void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), RowConversionIntegrationTest.class);

		db = DBUtil.getMainDB();
		mysql = MySqlDialect.NAME.equals(db.dialect().name());

		db.execute("DROP TABLE IF EXISTS row_conv_parent");
		db.execute("DROP TABLE IF EXISTS row_conv_child");
		TestDdl.execute(db, """
			CREATE TABLE row_conv_parent (
				id    bigint unsigned primary key,
				name  varchar(50) null,
				at    datetime null,
				at3   datetime(3) null
			)
			""");
		TestDdl.execute(db, """
			CREATE TABLE row_conv_child (
				id         bigint unsigned primary key,
				parent_id  bigint unsigned not null,
				title      varchar(50) null,
				at         datetime null
			)
			""");
		db.execute("INSERT INTO row_conv_parent VALUES (1, 'p1', '2024-01-05 09:00:00', '2024-01-05 09:00:00.123')");
		db.execute("INSERT INTO row_conv_parent VALUES (2, 'p2', '1500-01-01 12:00:00', '9999-12-31 23:59:59.999')");
		db.execute("INSERT INTO row_conv_parent VALUES (3, NULL, NULL, NULL)");
		db.execute("INSERT INTO row_conv_child VALUES (10, 1, 'c1', '2024-01-06 10:30:00')");

	}

	@AfterAll
	static void tearDown () {

		db.execute("DROP TABLE IF EXISTS row_conv_parent");
		db.execute("DROP TABLE IF EXISTS row_conv_child");
		DBUtil.stop();

	}

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	@Test
	@DisplayName("時差を持たない日時は、ドライバの getObject と同じ Timestamp（小数の秒・古い日付・最後の日も）")
	void localDateTimeColumns () {

		List<Data> rows = db.selectList("SELECT * FROM row_conv_parent ORDER BY id");

		assertInstanceOf(Timestamp.class, rows.get(0).get("at"));
		assertEquals(Timestamp.valueOf("2024-01-05 09:00:00"), rows.get(0).get("at"));
		assertEquals(Timestamp.valueOf("2024-01-05 09:00:00.123"), rows.get(0).get("at3"));
		assertEquals(Timestamp.valueOf("1500-01-01 12:00:00"), rows.get(1).get("at"));
		assertEquals(Timestamp.valueOf("9999-12-31 23:59:59.999"), rows.get(1).get("at3"));
		assertNull(rows.get(2).get("at"));
		assertTrue(rows.get(2).containsKey("at"), "null の列もキーは入れる");

		// Data の getter からも同じ
		assertEquals(Timestamp.valueOf("2024-01-05 09:00:00").getTime(), rows.get(0).getDate("at").getTime());

	}

	@Test
	@DisplayName("時差を持つ日時（MySQL の TIMESTAMP・PostgreSQL の timestamptz）は、これまでどおりドライバの getObject で読む")
	void zonedTimestampColumnsKeepTheDriverValue () {

		String type = mysql ? "timestamp null" : "timestamptz";
		db.execute("DROP TABLE IF EXISTS row_conv_zoned");
		db.execute("CREATE TABLE row_conv_zoned (id bigint primary key, at %s)".formatted(type));

		try {
			db.execute("INSERT INTO row_conv_zoned VALUES (1, '2024-01-05 09:00:00')");
			Object at = db.select("SELECT at FROM row_conv_zoned WHERE id = 1").orElseThrow().get("at");
			assertInstanceOf(Timestamp.class, at);
			assertEquals(Timestamp.valueOf("2024-01-05 09:00:00"), at);
		} finally {
			db.execute("DROP TABLE IF EXISTS row_conv_zoned");
		}

	}

	@Test
	@DisplayName("LocalDateTime で表せない値は、ドライバの getObject のまま（MySQL の月が 0 の日付・PostgreSQL の infinity）")
	void valuesLocalDateTimeCannotHold () {

		db.execute("DROP TABLE IF EXISTS row_conv_odd");
		TestDdl.execute(db, "CREATE TABLE row_conv_odd (id bigint unsigned primary key, at datetime null)");

		try {

			if (mysql) {
				// 月が 0 の日付。ドライバは前の月に繰り下げて読む（2.7.3 までと同じ値）
				// 月が 0 の日付は既定の sql_mode では入らない。同じ接続で緩めて入れ、戻す
				db.transaction(tx -> {
					db.execute("SET SESSION sql_mode = ''");
					db.execute("INSERT INTO row_conv_odd VALUES (1, '2024-00-05 00:00:00')");
					db.execute("SET SESSION sql_mode = DEFAULT");
				});
				assertEquals(Timestamp.valueOf("2023-12-05 00:00:00"), db.select("SELECT at FROM row_conv_odd").orElseThrow().get("at"));
			} else {
				db.execute("INSERT INTO row_conv_odd VALUES (1, 'infinity'), (2, '-infinity')");
				List<Data> rows = db.selectList("SELECT at FROM row_conv_odd ORDER BY id");
				// PGStatement.DATE_POSITIVE_INFINITY / DATE_NEGATIVE_INFINITY
				assertEquals(9223372036825200000L, ((Timestamp) rows.get(0).get("at")).getTime());
				assertEquals(-9223372036832400000L, ((Timestamp) rows.get(1).get("at")).getTime());
			}

		} finally {
			db.execute("DROP TABLE IF EXISTS row_conv_odd");
		}

	}

	@Test
	@DisplayName("入れ子の Data：テーブルの列が飛び飛びに並んでも、同じテーブルの Data に入る")
	void nestedColumnsInAnyOrder () {

		Data row = db.select("""
			SELECT p.id AS p__id, c.id AS c__id, p.name AS p__name, c.title AS c__title, p.at AS p__at, plain
			FROM row_conv_parent p
			INNER JOIN row_conv_child c ON c.parent_id = p.id
			CROSS JOIN (SELECT 'x' AS plain) v
			WHERE p.id = 1
			""").orElseThrow();

		assertEquals(Long.parseLong("1"), row.getData("p").getLong("id"));
		assertEquals("p1", row.getData("p").getString("name"));
		assertEquals(Timestamp.valueOf("2024-01-05 09:00:00"), row.getData("p").get("at"));
		assertEquals(10L, row.getData("c").getLong("id"));
		assertEquals("c1", row.getData("c").getString("title"));
		assertEquals("x", row.getString("plain"));
		assertEquals(List.of("p", "c", "plain"), List.copyOf(row.keySet()));

	}

	@Test
	@DisplayName("db.remove_all_null_table_data は、結果ごとに読んでも効く（全部 null のテーブルを落とす）")
	void removeAllNullTableData () {

		String sql = """
			SELECT p.id AS p__id, c.id AS c__id, c.title AS c__title
			FROM row_conv_parent p
			LEFT JOIN row_conv_child c ON c.parent_id = p.id
			WHERE p.id IN (1, 2)
			ORDER BY p.id
			""";

		List<Data> kept = db.selectList(sql);
		assertTrue(kept.get(1).containsKey("c"), "既定では落とさない");

		Conf.replace(ConfigFactory.parseString("db.remove_all_null_table_data = true").withFallback(Conf.conf().config()));

		List<Data> removed = db.selectList(sql);
		assertTrue(removed.get(0).containsKey("c"), "値のあるテーブルは残す");
		assertFalse(removed.get(1).containsKey("c"), "全部 null のテーブルを落としていません");

	}

}
