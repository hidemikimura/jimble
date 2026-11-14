package io.jimble.db.sql;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.select.SelectValue;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 結果の列の読み方を覚えて使い回す（3.0.0）ときに、値や DDL で列の型が変わっても正しく読むか
 *
 * <p>
 * SELECT 句が値（?）を含むと、列の型が値で変わる（MySQL の {@code SELECT ? AS v} は 5 なら BIGINT、"abc" なら VARCHAR）。
 * そうした結果は覚えない。DDL はアプリのマイグレーションで流す前提で、マイグレーションを当てたら（と DBUtil.load で）覚えた読み方を忘れる。
 * </p>
 */
@Tag("db")
class ResultColumnCacheIntegrationTest {

	static final class RcSchema extends AbstractSchema {

		static final RcSchema INSTANCE = new RcSchema();

		@Override
		public String name () {
			return "jimble_test";
		}

	}

	static final class RcNum extends Table {

		static final Column id = new Column(instance(), "id", long.class, false, null, true);
		static final Column n = new Column(instance(), "n", long.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, n);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		private RcNum () { super(RcSchema.INSTANCE, "rc_num"); }

		static RcNum instance () { return new RcNum(); }

	}

	private static DB db;

	private static boolean mysql;

	@BeforeAll
	static void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), ResultColumnCacheIntegrationTest.class);
		db = DBUtil.getMainDB();
		mysql = MySqlDialect.NAME.equals(db.dialect().name());

		db.execute("DROP TABLE IF EXISTS rc_num");
		db.execute("CREATE TABLE rc_num (id bigint primary key, n int null)");
		db.execute("INSERT INTO rc_num VALUES (1, 7)");

	}

	@AfterAll
	static void tearDown () {

		db.execute("DROP TABLE IF EXISTS rc_num");
		db.close();
		Conf.reload();

	}

	@Test
	@DisplayName("SELECT 句に値を含むと、値ごとの型で返る（ビルダー・template。null の行にならない）")
	void parameterInSelectList () {

		reset();

		for (Object value : new Object[] { 5L, "abc", new BigDecimal("1.5"), 6L }) {

			Data row = db.select(SQL.select(RcNum.id, new SelectValue().value(value).as("v"))
				.from(RcNum.instance()).where(RcNum.id.eq(1L))).orElseThrow();
			assertValue(value, row.get("v"));
			assertEquals(1L, row.getData("rc_num").getLong("id"));

		}

		SelectTemplate template = SQL.template(SQL.select(RcNum.id, new SelectValue().value(Dsl.param("v")).as("v"))
			.from(RcNum.instance()).where(RcNum.id.eq(1L)));

		for (Object value : new Object[] { 5L, "abc", new BigDecimal("1.5"), 6L }) {

			List<Data> rows = db.selectList(template.bind("v", value));
			assertEquals(1, rows.size());
			assertNotNull(rows.getFirst(), "null の行が混ざった");
			assertValue(value, rows.getFirst().get("v"));

		}

	}

	@Test
	@DisplayName("コードのマイグレーションで列の型を INT から BIGINT に替えたあとは、覚えた読み方を忘れて正しい値を返す")
	void alterColumnTypeByMigration () {

		reset();
		assertEquals(7L, readN());

		io.jimble.db.migration.code.CodeMigration.clear();
		io.jimble.db.migration.code.CodeMigration.add(new WidenN());
		try {
			io.jimble.db.migration.code.CodeMigration.execute();
		} finally {
			io.jimble.db.migration.code.CodeMigration.clear();
			db.execute("DELETE FROM migration_code");
		}

		assertEquals(3000000000L, readN());

	}

	@Test
	@DisplayName("マイグレーションを通さずに外で列の型を替えたら、起動し直す（DBUtil.load）と正しい値を返す")
	void alterColumnTypeOutsideThenReload () {

		reset();
		assertEquals(7L, readN());

		widen();

		// 外で DDL を流したら起動し直す前提。DBUtil.load で覚えた読み方を忘れる
		DBUtil.load(Conf.conf().config(), ResultColumnCacheIntegrationTest.class);
		db = DBUtil.getMainDB();

		assertEquals(3000000000L, readN());

	}

	/** 表を INT の列に戻す */
	private static void reset () {

		db.execute("DROP TABLE IF EXISTS rc_num");
		db.execute("CREATE TABLE rc_num (id bigint primary key, n int null)");
		db.execute("INSERT INTO rc_num VALUES (1, 7)");

	}

	/** 列を BIGINT にして、INT に入らない値を入れる */
	private static void widen () {

		db.execute(mysql ? "ALTER TABLE rc_num MODIFY n bigint null" : "ALTER TABLE rc_num ALTER COLUMN n TYPE bigint");
		db.execute("UPDATE rc_num SET n = 3000000000 WHERE id = 1");

	}

	private static long readN () {

		return db.select(SQL.select().from(RcNum.instance()).where(RcNum.id.eq(1L))).orElseThrow().getData("rc_num").getLong("n");

	}

	/** 列を広げるコードのマイグレーション */
	static final class WidenN extends io.jimble.db.migration.code.AbstractCodeMigration {

		@Override
		protected int versionYyyyMmDd () {
			return 20261115;
		}

		@Override
		protected void execute () {
			widen();
		}

	}

	private static void assertValue (Object expected, Object actual) {

		assertNotNull(actual);
		if (expected instanceof BigDecimal decimal) {
			assertInstanceOf(BigDecimal.class, actual);
			assertEquals(0, decimal.compareTo((BigDecimal) actual));
		} else if (expected instanceof Long number) {
			assertEquals(number.longValue(), ((Number) actual).longValue());
		} else {
			assertEquals(expected, actual);
		}

	}

}
