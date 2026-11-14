package io.jimble.db.data;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列ごとの読み方を 1 回だけ決める読み方（{@link RowPlan}。2.8.0）が、これまでの読み方（{@link ResultSetConverter}）と同じ行を返すか
 *
 * <p>
 * 同じ行を両方で読み、<b>キーの並び・値・値のクラス</b>が全部一致することを、
 * MySQL（dbTest）と PostgreSQL（pgTest）の主な型 × 値あり・NULL で見る。
 * </p>
 */
@Tag("db")
class RowPlanIntegrationTest {

	private static DB db;

	private static boolean mysql;

	@BeforeAll
	static void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), RowPlanIntegrationTest.class);

		db = DBUtil.getMainDB();
		mysql = MySqlDialect.NAME.equals(db.dialect().name());

		db.execute("DROP TABLE IF EXISTS rp_types");
		db.execute("DROP TABLE IF EXISTS rp_b");
		db.execute("DROP TABLE IF EXISTS rp_a");

		if (mysql) {

			db.execute("""
				CREATE TABLE rp_types (
					id bigint primary key,
					c_tinyint tinyint null, c_bool tinyint(1) null, c_smallint smallint null, c_mediumint mediumint null,
					c_int int null, c_int_u int unsigned null, c_bigint bigint null, c_bigint_u bigint unsigned null,
					c_decimal decimal(12,3) null, c_float float null, c_double double null,
					c_bit1 bit(1) null, c_bit8 bit(8) null,
					c_char char(3) null, c_varchar varchar(20) null, c_text text null,
					c_enum enum('a','b') null, c_set set('x','y') null,
					c_binary binary(2) null, c_varbinary varbinary(10) null, c_blob blob null,
					c_date date null, c_time time null, c_datetime datetime null, c_datetime3 datetime(3) null,
					c_timestamp timestamp null, c_year year null,
					c_json json null, c_geom geometry null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
				""");

			db.execute("""
				INSERT INTO rp_types VALUES (1,
					-7, 1, -300, 70000,
					-2147483648, 4294967295, -9223372036854775808, 18446744073709551615,
					12345.678, 1.5, 2.25,
					b'1', b'10100101',
					'abc', 'あいう', 'テキスト',
					'b', 'x,y',
					x'0102', x'030405', x'060708',
					'2026-11-14', '12:34:56', '2026-11-14 12:34:56', '2026-11-14 12:34:56.789',
					'2026-11-14 12:34:56', 2026,
					'{"a": [1, 2], "b": {"c": "d"}}', ST_GeomFromText('POINT(1 2)'))
				""");

		} else {

			db.execute("""
				CREATE TABLE rp_types (
					id bigint primary key,
					c_smallint smallint null, c_int integer null, c_bigint bigint null,
					c_numeric numeric(12,3) null, c_real real null, c_double double precision null, c_bool boolean null,
					c_char char(3) null, c_varchar varchar(20) null, c_text text null, c_bytea bytea null,
					c_date date null, c_time time null, c_timestamp timestamp null, c_timestamptz timestamptz null,
					c_json json null, c_jsonb jsonb null, c_uuid uuid null, c_interval interval null,
					c_inet inet null, c_cidr cidr null, c_macaddr macaddr null, c_varbit bit varying(8) null,
					c_tsvector tsvector null, c_range int4range null, c_tstzrange tstzrange null,
					c_nan numeric null, c_neg_scale numeric(5,-2) null, c_money money null, c_xml xml null
				)
				""");

			db.execute("""
				INSERT INTO rp_types VALUES (1,
					-300, -2147483648, -9223372036854775808,
					12345.678, 1.5, 2.25, true,
					'abc', 'あいう', 'テキスト', '\\x0102',
					'2026-11-14', '12:34:56', '2026-11-14 12:34:56.789', '2026-11-14 12:34:56+09',
					'[1, 2]', '{"b": {"c": "d"}}', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '1 day 2 hours',
					'10.0.0.1', '10.0.0.0/8', '08:00:2b:01:02:03', B'1010',
					'a fat cat', '[1,10)', '[2026-11-14 00:00+09,2026-11-15 00:00+09)',
					'NaN', 1234, 12.34, '<a>x</a>')
				""");

		}

		db.execute("INSERT INTO rp_types (id) VALUES (2)");

		String tail = mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
		db.execute("CREATE TABLE rp_a (id bigint primary key, name varchar(20) null, amount decimal(10,2) null)" + tail);
		db.execute("CREATE TABLE rp_b (id bigint primary key, a_id bigint not null, note varchar(20) null)" + tail);
		db.execute("INSERT INTO rp_a VALUES (1, 'a1', 10.50), (2, null, null), (3, 'a3', 0)");
		db.execute("INSERT INTO rp_b VALUES (10, 1, 'b10'), (11, 3, null)");

	}

	@AfterAll
	static void tearDown () {

		db.execute("DROP TABLE IF EXISTS rp_types");
		db.execute("DROP TABLE IF EXISTS rp_b");
		db.execute("DROP TABLE IF EXISTS rp_a");
		db.close();
		Conf.reload();

	}

	@Test
	@DisplayName("主な型 × 値あり・NULL で、これまでの読み方と同じ行になる")
	void allTypes () throws Exception {

		List<Data> rows = compare("SELECT * FROM rp_types ORDER BY id", false);

		assertEquals(2, rows.size());
		// 型を指定した getter で読む列が、getObject と同じクラスで返っていること
		assertTrue(rows.getFirst().get("c_bigint") instanceof Long);
		assertTrue(rows.getFirst().get("c_varchar") instanceof String);

	}

	@Test
	@DisplayName("表名で入れ子にする列と直下の列が混ざっても、キーの並び・全部 NULL の表の扱いが同じ（設定のオン・オフ両方）")
	void nestedAndTopLevel () throws Exception {

		String sql = """
			SELECT a.id AS rp_a__id, 1 AS plain, a.name AS rp_a__name, b.id AS rp_b__id, b.note AS rp_b__note,
				a.amount AS rp_a__amount, 'x' AS other
			FROM rp_a a LEFT JOIN rp_b b ON b.a_id = a.id
			ORDER BY a.id
			""";

		List<Data> kept = compare(sql, false);
		List<Data> removed = compare(sql, true);

		assertEquals(3, kept.size());
		// a.id = 2 には rp_b が無い：落とさなければ全部 NULL の表が残り、落とせば消える
		assertTrue(kept.get(1).containsKey("rp_b"));
		assertTrue(!removed.get(1).containsKey("rp_b"));
		assertEquals(List.of("rp_a", "plain", "rp_b", "other"), new ArrayList<>(kept.getFirst().keySet()));

	}

	@Test
	@DisplayName("PostgreSQL：Java のクラスを持たない型（String と答えるが PGobject で返る）と numeric の特別な値も、これまでどおり")
	void postgresSpecialValues () throws Exception {

		if (mysql) {
			return;
		}

		List<Data> rows = compare("""
			SELECT ROW(1, 'a') AS c_row, 'Infinity'::numeric AS c_inf, '-Infinity'::numeric AS c_ninf,
				'NaN'::numeric AS c_nan, '192.168.0.1'::inet AS c_inet, 'abc'::varchar AS c_varchar
			""", false);

		assertEquals(1, rows.size());
		assertTrue("org.postgresql.util.PGobject".equals(rows.getFirst().get("c_inet").getClass().getName()), "inet が PGobject で返っていない");
		assertTrue("org.postgresql.util.PGobject".equals(rows.getFirst().get("c_row").getClass().getName()), "複合型が PGobject で返っていない");
		assertEquals(Double.NaN, rows.getFirst().get("c_nan"));

	}

	@Test
	@DisplayName("同じ名前の列が 2 つある結果は、段取りを作らずこれまでの読み方に任せる")
	void duplicateLabelsFallBack () throws Exception {

		try (Connection connection = DBUtil.getMainDataSource().getWriteDataSource().getConnection();
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery("SELECT 1 AS x, 2 AS x")) {

			List<ResultSetIterator.KeyInfo> keys = new ArrayList<>();
			List<Integer> types = new ArrayList<>();
			ResultSetIterator.readColumns(rs.getMetaData(), db.dialect(), keys, types);

			assertEquals(null, RowPlan.of(rs.getMetaData(), db.dialect(), keys, types, false));

		}

	}

	/**
	 * SQL の結果を、段取りとこれまでの読み方の両方で読み、行ごとに同じか確かめる
	 *
	 * @param sql	SQL
	 * @param removeAllNullTableData	全部 null の表を落とすか
	 * @return	段取りで読んだ行
	 */
	private static List<Data> compare (String sql, boolean removeAllNullTableData) throws Exception {

		Dialect dialect = db.dialect();
		List<Data> rows = new ArrayList<>();

		try (Connection connection = DBUtil.getMainDataSource().getWriteDataSource().getConnection();
			 Statement statement = connection.createStatement();
			 ResultSet rs = statement.executeQuery(sql)) {

			List<ResultSetIterator.KeyInfo> keys = new ArrayList<>();
			List<Integer> types = new ArrayList<>();
			ResultSetIterator.readColumns(rs.getMetaData(), dialect, keys, types);

			RowPlan plan = RowPlan.of(rs.getMetaData(), dialect, keys, types, removeAllNullTableData);
			assertNotNull(plan, "段取りを作れなかった");

			int number = 0;
			while (rs.next()) {
				number++;
				Data expected = ResultSetConverter.convert(rs, keys, types, dialect, removeAllNullTableData);
				Data actual = plan.read(rs, dialect);
				assertNotNull(expected);
				assertNotNull(actual);
				assertSameRow(expected, actual, "行 " + number);
				rows.add(actual);
			}

		}

		return rows;

	}

	/**
	 * キーの並び・値・値のクラスが同じか（入れ子の Data も）
	 */
	private static void assertSameRow (Map<String, Object> expected, Map<String, Object> actual, String where) {

		assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(actual.keySet()), where + " のキーの並び");

		for (Map.Entry<String, Object> entry : expected.entrySet()) {

			String at = where + " の " + entry.getKey();
			Object e = entry.getValue();
			Object a = actual.get(entry.getKey());

			if (e == null || a == null) {
				assertEquals(e, a, at);
				continue;
			}

			assertEquals(e.getClass(), a.getClass(), at + " のクラス");

			if (e instanceof Data ed) {
				assertSameRow(ed, (Data) a, at);
			} else if (e instanceof byte[] eb) {
				assertTrue(Arrays.equals(eb, (byte[]) a), at);
			} else if (e instanceof java.sql.SQLXML ex) {
				// equals を持たないので中身で比べる
				try {
					assertEquals(ex.getString(), ((java.sql.SQLXML) a).getString(), at);
				} catch (java.sql.SQLException sqlEx) {
					throw new IllegalStateException(sqlEx);
				}
			} else {
				assertEquals(e, a, at);
			}

		}

	}

}
