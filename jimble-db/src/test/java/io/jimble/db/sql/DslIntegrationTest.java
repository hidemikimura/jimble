package io.jimble.db.sql;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.dialect.CastType;
import io.jimble.db.dialect.DateUnit;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.dsl.select.WindowFrame;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 足した DSL が<b>実際に流れて、同じ答えを返す</b>ことの確認（要件 F-D-31）
 *
 * <p>
 * <b>組み立てた SQL が正しいことと、DB が受け取ることは別。</b>
 * {@code SqlDslFunctionTest} は文字列を固定するだけなので、
 * ここで実 DB に投げる。
 * </p>
 *
 * <p>
 * <b>同じテストを MySQL と PostgreSQL の両方で走らせる</b>（要件 F-D-30 / D-16）。
 * 期待する値も同じにしてある。<b>違う値が返るなら、それは吸収できていないということ。</b>
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * ./gradlew :jimble-db:pgTest
 * </pre>
 */
@Tag("db")
class DslIntegrationTest {

	@BeforeAll
	static void setUp () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), DslIntegrationTest.class)
			, "DB に接続できませんでした");

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS dsl_item");
		TestDdl.execute(db, """
			CREATE TABLE dsl_item (
				id       bigint unsigned auto_increment primary key,
				group_id bigint unsigned not null,
				name     varchar(50)     null,
				amount   bigint          not null,
				at       datetime        not null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

		db.execute("DELETE FROM dsl_item");

		insert(db, 1, " あいう ", 10, "2026-03-04 05:06:07");
		insert(db, 1, "ABC", 30, "2026-03-05 05:06:07");
		insert(db, 2, null, 20, "2026-01-01 00:00:00");

	}

	/**
	 * 1行入れる
	 *
	 * @param db		DB
	 * @param groupId	グループ
	 * @param name		名前
	 * @param amount	金額
	 * @param at		日時
	 */
	private static void insert (DB db, long groupId, String name, long amount, String at) {

		db.insert("INSERT INTO dsl_item (group_id, name, amount, at) VALUES (?, ?, ?, ?)"
			, groupId, name, amount, io.jimble.util.date.DateUtil.parseDate(at));

	}

	@AfterAll
	static void tearDown () {

		if (DBUtil.isUseDB()) {
			DBUtil.getMainDB().execute("DROP TABLE IF EXISTS dsl_item");
		}
		DBUtil.stop();

	}

	// region 小物

	/**
	 * 1つの式を引く
	 *
	 * @param select	式
	 * @param where		条件（null なら全部）
	 * @return	結果
	 */
	private static Data one (io.jimble.db.sql.query.select.ISelect select
		, io.jimble.db.sql.query.where.IWhere where) throws Exception {

		SelectBuilder builder = SQL.select(select.as("v")).from(DslSchema.Item.instance());

		if (where != null) {
			builder.where(where);
		}

		try (DB db = DBUtil.getMainDB()) {

			Data row = db.select(builder);

			assertNotNull(row, () -> "SQL が流れませんでした: " + builder.sql(db.dialect())
				+ " / " + db.getError());

			return row;

		}

	}

	// endregion

	// region 文字列

	@Test
	@DisplayName("文字列の関数が両方の製品で同じ答えを返す")
	void strings () throws Exception {

		assertEquals("abc", one(Dsl.lower(DslSchema.Item.name), DslSchema.Item.name.eq("ABC")).getString("v"));
		assertEquals("ABC", one(Dsl.upper(DslSchema.Item.name), DslSchema.Item.name.eq("ABC")).getString("v"));

		assertEquals("あいう"
			, one(Dsl.trim(DslSchema.Item.name), DslSchema.Item.name.eq(" あいう ")).getString("v"));

		// 文字数（バイト数ではない）
		assertEquals(3
			, one(Dsl.length(Dsl.trim(DslSchema.Item.name)), DslSchema.Item.name.eq(" あいう ")).getInt("v"));

		assertEquals("AB"
			, one(Dsl.substring(DslSchema.Item.name, 1, 2), DslSchema.Item.name.eq("ABC")).getString("v"));

		assertEquals("AXC"
			, one(Dsl.replace(DslSchema.Item.name, "B", "X"), DslSchema.Item.name.eq("ABC")).getString("v"));

		assertEquals("00ABC"
			, one(Dsl.lpad(DslSchema.Item.name, 5, "0"), DslSchema.Item.name.eq("ABC")).getString("v"));

		assertEquals("A/B"
			, one(Dsl.concatWs("/", "A", "B"), DslSchema.Item.name.eq("ABC")).getString("v"));

	}

	@Test
	@DisplayName("LOCATE と STRPOS は並びが逆でも同じ答えになる")
	void locate () throws Exception {

		assertEquals(2
			, one(Dsl.locate("B", DslSchema.Item.name), DslSchema.Item.name.eq("ABC")).getInt("v"));

		assertEquals(0
			, one(Dsl.locate("Z", DslSchema.Item.name), DslSchema.Item.name.eq("ABC")).getInt("v"));

	}

	// endregion

	// region 数値

	@Test
	@DisplayName("数値の関数")
	void numbers () throws Exception {

		assertEquals(10, one(Dsl.abs(DslSchema.Item.amount), DslSchema.Item.amount.eq(10)).getInt("v"));
		assertEquals(1, one(Dsl.mod(DslSchema.Item.amount, 3), DslSchema.Item.amount.eq(10)).getInt("v"));
		assertEquals(1, one(Dsl.sign(DslSchema.Item.amount), DslSchema.Item.amount.eq(10)).getInt("v"));
		assertEquals(100, one(Dsl.power(DslSchema.Item.amount, 2), DslSchema.Item.amount.eq(10)).getInt("v"));
		assertEquals(20, one(Dsl.greatest(DslSchema.Item.amount, 20), DslSchema.Item.amount.eq(10)).getInt("v"));
		assertEquals(10, one(Dsl.least(DslSchema.Item.amount, 20), DslSchema.Item.amount.eq(10)).getInt("v"));

	}

	// endregion

	// region 日付

	@Test
	@DisplayName("日付の一部を取り出すと、両方の製品で同じ数が返る")
	void dateParts () throws Exception {

		var where = DslSchema.Item.amount.eq(10);

		assertEquals(2026, one(Dsl.year(DslSchema.Item.at), where).getInt("v"));
		assertEquals(3, one(Dsl.month(DslSchema.Item.at), where).getInt("v"));
		assertEquals(4, one(Dsl.day(DslSchema.Item.at), where).getInt("v"));
		assertEquals(5, one(Dsl.hour(DslSchema.Item.at), where).getInt("v"));
		assertEquals(6, one(Dsl.minute(DslSchema.Item.at), where).getInt("v"));
		assertEquals(7, one(Dsl.second(DslSchema.Item.at), where).getInt("v"));
		assertEquals(1, one(Dsl.quarter(DslSchema.Item.at), where).getInt("v"));

		// 2026-03-04 は水曜日。MySQL の DAYOFWEEK は日曜が 1 なので 4
		assertEquals(4, one(Dsl.dayOfWeek(DslSchema.Item.at), where).getInt("v"));

		assertEquals(63, one(Dsl.dayOfYear(DslSchema.Item.at), where).getInt("v"));

		// ISO 週
		assertEquals(10, one(Dsl.weekOfYear(DslSchema.Item.at), where).getInt("v"));

	}

	@Test
	@DisplayName("日時の足し引きと差")
	void dateMath () throws Exception {

		var where = DslSchema.Item.amount.eq(10);

		assertEquals(11
			, one(Dsl.day(Dsl.dateAdd(DslSchema.Item.at, 7, DateUnit.DAY)), where).getInt("v"));

		assertEquals(2
			, one(Dsl.day(Dsl.dateSub(DslSchema.Item.at, 2, DateUnit.DAY)), where).getInt("v"));

		// 2026-03-05 - 2026-03-04 = 1 日
		assertEquals(1
			, one(Dsl.dateDiff(
				Dsl.date(Dsl.dateAdd(DslSchema.Item.at, 1, DateUnit.DAY))
				, Dsl.date(DslSchema.Item.at)), where).getInt("v"));

		assertEquals(3600
			, one(Dsl.secondsBetween(
				Dsl.dateAdd(DslSchema.Item.at, 1, DateUnit.HOUR)
				, DslSchema.Item.at), where).getLong("v"));

	}

	@Test
	@DisplayName("epoch 秒との行き来")
	void unixTime () throws Exception {

		var where = DslSchema.Item.amount.eq(10);

		long epoch = one(Dsl.unixTimestamp(DslSchema.Item.at), where).getLong("v");

		assertTrue(epoch > 0, "epoch 秒が取れていない: " + epoch);

		assertEquals(2026, one(Dsl.year(Dsl.fromUnixTime(epoch)), where).getInt("v"));

	}

	// endregion

	// region 条件・型変換

	@Test
	@DisplayName("条件で分ける")
	void ifThenElse () throws Exception {

		assertEquals("多い"
			, one(Dsl.ifThenElse(DslSchema.Item.amount.ge(20), "多い", "少ない")
				, DslSchema.Item.amount.eq(30)).getString("v"));

		assertEquals("少ない"
			, one(Dsl.ifThenElse(DslSchema.Item.amount.ge(20), "多い", "少ない")
				, DslSchema.Item.amount.eq(10)).getString("v"));

	}

	@Test
	@DisplayName("NULL の穴埋め")
	void coalesce () throws Exception {

		assertEquals("（無名）"
			, one(Dsl.coalesce(DslSchema.Item.name, "（無名）"), DslSchema.Item.amount.eq(20)).getString("v"));

		assertEquals("ABC"
			, one(Dsl.coalesce(DslSchema.Item.name, "（無名）"), DslSchema.Item.amount.eq(30)).getString("v"));

	}

	@Test
	@DisplayName("型変換")
	void cast () throws Exception {

		assertEquals("10"
			, one(Dsl.cast(DslSchema.Item.amount, CastType.STRING), DslSchema.Item.amount.eq(10)).getString("v"));

		assertEquals(10
			, one(Dsl.cast(Dsl.cast(DslSchema.Item.amount, CastType.STRING), CastType.INT)
				, DslSchema.Item.amount.eq(10)).getInt("v"));

	}

	@Test
	@DisplayName("正規表現")
	void regexp () throws Exception {

		try (DB db = DBUtil.getMainDB()) {

			List<Data> rows = db.selectList(SQL
				.select(DslSchema.Item.name)
				.from(DslSchema.Item.instance())
				.where(Dsl.regexp(DslSchema.Item.name, "^[A-Z]+$")));

			assertNotNull(rows, () -> "SQL が流れませんでした: " + db.getError());
			assertEquals(1, rows.size(), rows.toString());

		}

	}

	// endregion

	// region 集約

	@Test
	@DisplayName("重複を除いた件数と、集めて1つの文字列にする")
	void aggregates () throws Exception {

		try (DB db = DBUtil.getMainDB()) {

			Data row = db.select(SQL
				.select(
					Dsl.countDistinct(DslSchema.Item.group_id).as("groups")
					, Dsl.groupConcat(DslSchema.Item.amount, "/").as("amounts")
					, Dsl.stddev(DslSchema.Item.amount).as("sd"))
				.from(DslSchema.Item.instance()));

			assertNotNull(row, () -> "SQL が流れませんでした: " + db.getError());

			assertEquals(2, row.getInt("groups"));

			// 並びは保証されないので、中身だけ見る
			String amounts = row.getString("amounts");
			assertEquals(3, amounts.split("/").length, amounts);
			assertTrue(amounts.contains("10") && amounts.contains("20") && amounts.contains("30"), amounts);

			assertTrue(row.getDouble("sd") > 0, "標準偏差が 0 になっている");

		}

	}

	// endregion

	// region ウィンドウ関数

	@Test
	@DisplayName("LAG / NTILE も実際に流れる（行数はリテラルでないと MySQL が弾く）")
	void windowOffset () throws Exception {

		try (DB db = DBUtil.getMainDB()) {

			List<Data> rows = db.selectList(SQL
				.select(
					DslSchema.Item.amount
					, Dsl.lag(DslSchema.Item.amount, 1)
						.orderBy(DslSchema.Item.amount.asc()).as("prev")
					, Dsl.nTile(2)
						.orderBy(DslSchema.Item.amount.asc()).as("bucket"))
				.from(DslSchema.Item.instance())
				.orderBy(DslSchema.Item.amount.asc()));

			assertNotNull(rows, () -> "SQL が流れませんでした: " + db.getError());
			assertEquals(3, rows.size());

			assertEquals(0, rows.get(0).getInt("prev"), "1行目に前の行は無い");
			assertEquals(10, rows.get(1).getInt("prev"));
			assertEquals(20, rows.get(2).getInt("prev"));

			assertEquals(1, rows.get(0).getInt("bucket"));
			assertEquals(2, rows.get(2).getInt("bucket"));

		}

	}

	@Test
	@DisplayName("引数が2つとも値でも、入れ替わらない")
	void bothBound () throws Exception {

		var where = DslSchema.Item.amount.eq(10);

		// PostgreSQL で並びが逆になっていたら 0 が返る
		assertEquals(2, one(Dsl.locate("@", "a@b.com"), where).getInt("v"));

		// MySQL で並びが逆になっていたら符号が反転する
		assertEquals(3600
			, one(Dsl.secondsBetween(
				io.jimble.util.date.DateUtil.parseDate("2026-01-01 10:00:00")
				, io.jimble.util.date.DateUtil.parseDate("2026-01-01 09:00:00")), where).getLong("v"));

	}

	@Test
	@DisplayName("ウィンドウ関数が両方の製品で同じ順位を返す")
	void window () throws Exception {

		try (DB db = DBUtil.getMainDB()) {

			List<Data> rows = db.selectList(SQL
				.select(
					DslSchema.Item.amount
					, Dsl.rank()
						.partitionBy(DslSchema.Item.group_id)
						.orderBy(DslSchema.Item.amount.desc()).as("rank")
					, Dsl.over(Dsl.sum(DslSchema.Item.amount))
						.orderBy(DslSchema.Item.amount.asc())
						.rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
						.as("total"))
				.from(DslSchema.Item.instance())
				.orderBy(DslSchema.Item.amount.asc()));

			assertNotNull(rows, () -> "SQL が流れませんでした: " + db.getError());
			assertEquals(3, rows.size());

			// amount = 10 / 20 / 30 の順
			assertEquals(10, rows.get(0).getInt(DslSchema.Item.amount));

			// 累計
			assertEquals(10, rows.get(0).getInt("total"));
			assertEquals(30, rows.get(1).getInt("total"));
			assertEquals(60, rows.get(2).getInt("total"));

			// group_id=1 の中では 30 が1位、10 が2位。group_id=2 の 20 は1位
			assertEquals(2, rows.get(0).getInt("rank"));
			assertEquals(1, rows.get(1).getInt("rank"));
			assertEquals(1, rows.get(2).getInt("rank"));

		}

	}

	// endregion

}
