package io.jimble.db.sql;

import io.jimble.db.dialect.CastType;
import io.jimble.db.dialect.DateUnit;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.internal.sql.query.dsl.select.WindowFrame;
import io.jimble.db.sql.query.select.ISelect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 足した DSL が両方の製品で正しい SQL になることの確認（要件 F-D-31）
 *
 * <p>
 * <b>DB 接続なし。</b>出す SQL をそのまま固定する。
 * 実際に流れるかどうかは {@code DslIntegrationTest} が
 * {@code dbTest} と {@code pgTest} の両方で確かめる。
 * </p>
 */
class SqlDslFunctionTest {

	/* MySQL */
	private static final Dialect MYSQL = MySqlDialect.INSTANCE;

	/* PostgreSQL */
	private static final Dialect POSTGRESQL = PostgreSqlDialect.INSTANCE;

	// region 小物

	/**
	 * 式だけを取り出す
	 *
	 * @param dialect	方言
	 * @param select	式
	 * @return	SQL
	 */
	private static String sql (Dialect dialect, ISelect select) {

		String sql = SQL.select(select).from(TestSchema.Site.instance()).sql(dialect);

		/*
		 * EXTRACT(YEAR FROM "site"."deleted_at") のように、
		 * <b>式の中にも FROM "site" が出る</b>。最初で切ると式が途中で切れるので、
		 * 後ろから探す。
		 */
		String table = " FROM " + dialect.identifier("site");
		int from = sql.lastIndexOf(table);

		return sql.substring("SELECT ".length(), from).trim();

	}

	/**
	 * WHERE だけを取り出す
	 *
	 * @param dialect	方言
	 * @param where		条件
	 * @return	SQL
	 */
	private static String where (Dialect dialect, io.jimble.db.sql.query.where.IWhere where) {

		String sql = SQL.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance())
			.where(where)
			.sql(dialect);

		return sql.substring(sql.indexOf(" WHERE ") + " WHERE ".length()).trim();

	}

	// endregion

	// region 文字列

	@Test
	@DisplayName("文字数はバイト数ではない（MySQL の LENGTH はバイト数）")
	void length () {

		assertEquals("CHAR_LENGTH(`site`.`name`)", sql(MYSQL, Dsl.length(TestSchema.Site.name)));
		assertEquals("LENGTH(\"site\".\"name\")", sql(POSTGRESQL, Dsl.length(TestSchema.Site.name)));

		assertEquals("LENGTH(`site`.`name`)", sql(MYSQL, Dsl.byteLength(TestSchema.Site.name)));
		assertEquals("OCTET_LENGTH(\"site\".\"name\")", sql(POSTGRESQL, Dsl.byteLength(TestSchema.Site.name)));

	}

	@Test
	@DisplayName("名前が同じ文字列関数はそのまま出る")
	void strings () {

		assertEquals("LOWER(`site`.`name`)", sql(MYSQL, Dsl.lower(TestSchema.Site.name)));
		assertEquals("LOWER(\"site\".\"name\")", sql(POSTGRESQL, Dsl.lower(TestSchema.Site.name)));

		assertEquals("SUBSTRING(`site`.`name`, ?, ?)"
			, sql(MYSQL, Dsl.substring(TestSchema.Site.name, 1, 3)));

		assertEquals("LPAD(`site`.`name`, ?, ?)"
			, sql(MYSQL, Dsl.lpad(TestSchema.Site.name, 8, "0")));

	}

	@Test
	@DisplayName("LOCATE と POSITION（引数の並びは変えない）")
	void locate () {

		assertEquals("LOCATE(?, `site`.`name`)"
			, sql(MYSQL, Dsl.locate("あ", TestSchema.Site.name)));

		/*
		 * STRPOS(対象, 探すもの) は引数の並びが逆になり、
		 * <b>書き出す順とバインドする順がずれる</b>。
		 * POSITION(探すもの IN 対象) なら並びが変わらない。
		 */
		assertEquals("POSITION(? IN \"site\".\"name\")"
			, sql(POSTGRESQL, Dsl.locate("あ", TestSchema.Site.name)));

	}

	@Test
	@DisplayName("引数の並びが逆でも、バインドの順は変わらない")
	void locateParameters () {

		SelectBuilder builder = SQL
			.select(Dsl.locate("あ", TestSchema.Site.name))
			.from(TestSchema.Site.instance());

		/*
		 * PostgreSQL は STRPOS(対象, 探すもの) と並びが逆になるが、
		 * <b>? は「探すもの」の1つだけ</b>なので順は変わらない。
		 * 列が ? になっていたら、ここがずれる。
		 */
		assertEquals(List.of("あ"), builder.params());

	}

	@Test
	@DisplayName("引数が2つとも ? でも、バインドの順が入れ替わらない")
	void locateBothBound () {

		SelectBuilder builder = SQL
			.select(Dsl.locate("@", "a@b.com"))
			.from(TestSchema.Site.instance());

		/*
		 * PostgreSQL で STRPOS(対象, 探すもの) と書き出すと、
		 * <b>? の並びだけが逆になって値が入れ替わる</b>（0 が返る）。
		 * 書き出す順を宣言順と同じにしてあることを固定する。
		 */
		assertEquals(List.of("@", "a@b.com"), builder.params());

		assertTrue(builder.sql(MYSQL).contains("LOCATE(?, ?)"), builder.sql(MYSQL));
		assertTrue(builder.sql(POSTGRESQL).contains("POSITION(? IN ?)"), builder.sql(POSTGRESQL));

	}

	@Test
	@DisplayName("秒の差も、引数が2つとも ? でバインドの順が入れ替わらない")
	void secondsBetweenBothBound () {

		SelectBuilder builder = SQL
			.select(Dsl.secondsBetween("2026-01-01 10:00:00", "2026-01-01 09:00:00"))
			.from(TestSchema.Site.instance());

		/*
		 * MySQL の TIMESTAMPDIFF(SECOND, to, from) は並びが逆になり、
		 * <b>符号が反転した秒数</b>が黙って返る。
		 */
		assertEquals(List.of("2026-01-01 10:00:00", "2026-01-01 09:00:00"), builder.params());

		assertTrue(builder.sql(MYSQL).contains("(UNIX_TIMESTAMP(?) - UNIX_TIMESTAMP(?))"), builder.sql(MYSQL));

	}

	// endregion

	// region 数値

	@Test
	@DisplayName("数値関数")
	void numbers () {

		assertEquals("ABS(`site`.`feed_count`)", sql(MYSQL, Dsl.abs(TestSchema.Site.feed_count)));
		assertEquals("ABS(\"site\".\"feed_count\")", sql(POSTGRESQL, Dsl.abs(TestSchema.Site.feed_count)));

		assertEquals("MOD(`site`.`feed_count`, ?)"
			, sql(MYSQL, Dsl.mod(TestSchema.Site.feed_count, 3)));

		assertEquals("GREATEST(`site`.`feed_count`, ?)"
			, sql(MYSQL, Dsl.greatest(TestSchema.Site.feed_count, 0)));

	}

	// endregion

	// region 日付

	@Test
	@DisplayName("日付の一部を取り出す書き方は製品でまるで違う")
	void datePart () {

		assertEquals("YEAR(`site`.`deleted_at`)", sql(MYSQL, Dsl.year(TestSchema.Site.deleted_at)));
		assertEquals("(EXTRACT(YEAR FROM \"site\".\"deleted_at\"))::int"
			, sql(POSTGRESQL, Dsl.year(TestSchema.Site.deleted_at)));

	}

	@Test
	@DisplayName("曜日は起点が違うので揃える（日曜が 1）")
	void dayOfWeek () {

		assertEquals("DAYOFWEEK(`site`.`deleted_at`)"
			, sql(MYSQL, Dsl.dayOfWeek(TestSchema.Site.deleted_at)));

		// PostgreSQL の DOW は日曜が 0。+ 1 して MySQL に合わせる
		assertEquals("(EXTRACT(DOW FROM \"site\".\"deleted_at\") + 1)::int"
			, sql(POSTGRESQL, Dsl.dayOfWeek(TestSchema.Site.deleted_at)));

	}

	@Test
	@DisplayName("週は ISO に揃える")
	void weekOfYear () {

		// MySQL の WEEK は既定が ISO ではない。WEEKOFYEAR が ISO
		assertEquals("WEEKOFYEAR(`site`.`deleted_at`)"
			, sql(MYSQL, Dsl.weekOfYear(TestSchema.Site.deleted_at)));

		assertEquals("(EXTRACT(WEEK FROM \"site\".\"deleted_at\"))::int"
			, sql(POSTGRESQL, Dsl.weekOfYear(TestSchema.Site.deleted_at)));

	}

	@Test
	@DisplayName("日時の足し引き")
	void dateAdd () {

		assertEquals("DATE_ADD(`site`.`deleted_at`, INTERVAL ? DAY)"
			, sql(MYSQL, Dsl.dateAdd(TestSchema.Site.deleted_at, 7, DateUnit.DAY)));

		assertEquals("(\"site\".\"deleted_at\" + (? * INTERVAL '1 DAY'))"
			, sql(POSTGRESQL, Dsl.dateAdd(TestSchema.Site.deleted_at, 7, DateUnit.DAY)));

		assertEquals("DATE_SUB(`site`.`deleted_at`, INTERVAL ? DAY)"
			, sql(MYSQL, Dsl.dateSub(TestSchema.Site.deleted_at, 7, DateUnit.DAY)));

	}

	@Test
	@DisplayName("日時の足し引きのパラメータは1つ")
	void dateAddParameters () {

		SelectBuilder builder = SQL
			.select(Dsl.dateAdd(TestSchema.Site.deleted_at, 7, DateUnit.DAY))
			.from(TestSchema.Site.instance());

		assertEquals(List.of(7L), builder.params());

	}

	@Test
	@DisplayName("今日の日付")
	void curDate () {

		assertEquals("CURDATE()", sql(MYSQL, Dsl.curDate()));
		assertEquals("CURRENT_DATE", sql(POSTGRESQL, Dsl.curDate()));

	}

	// endregion

	// region 条件・型変換

	@Test
	@DisplayName("PostgreSQL に IF は無いので CASE WHEN になる")
	void ifThenElse () {

		ISelect select = Dsl.ifThenElse(TestSchema.Site.feed_count.gt(0), "あり", "なし");

		assertEquals("IF(`site`.`feed_count` > ?, ?, ?)", sql(MYSQL, select));
		assertEquals("(CASE WHEN \"site\".\"feed_count\" > ? THEN ? ELSE ? END)", sql(POSTGRESQL, select));

	}

	@Test
	@DisplayName("条件で分けるときのバインドの順は 条件 → 真 → 偽")
	void ifThenElseParameters () {

		SelectBuilder builder = SQL
			.select(Dsl.ifThenElse(TestSchema.Site.feed_count.gt(0), "あり", "なし"))
			.from(TestSchema.Site.instance());

		assertEquals(List.of(0, "あり", "なし"), builder.params());

	}

	@Test
	@DisplayName("型名は製品でまるで違う")
	void cast () {

		assertEquals("CAST(`site`.`id` AS CHAR)"
			, sql(MYSQL, Dsl.cast(TestSchema.Site.id, CastType.STRING)));

		assertEquals("CAST(\"site\".\"id\" AS text)"
			, sql(POSTGRESQL, Dsl.cast(TestSchema.Site.id, CastType.STRING)));

		assertEquals("CAST(`site`.`name` AS DECIMAL(10,2))"
			, sql(MYSQL, Dsl.castDecimal(TestSchema.Site.name, 10, 2)));

		assertEquals("CAST(\"site\".\"name\" AS numeric(10,2))"
			, sql(POSTGRESQL, Dsl.castDecimal(TestSchema.Site.name, 10, 2)));

	}

	@Test
	@DisplayName("COALESCE / NULLIF は両製品で同じ")
	void coalesce () {

		assertEquals("COALESCE(`site`.`name`, ?)"
			, sql(MYSQL, Dsl.coalesce(TestSchema.Site.name, "")));

		assertEquals("NULLIF(\"site\".\"feed_count\", ?)"
			, sql(POSTGRESQL, Dsl.nullif(TestSchema.Site.feed_count, 0)));

	}

	@Test
	@DisplayName("正規表現の書き方が違う")
	void regexp () {

		assertEquals("(`site`.`name` REGEXP ?)"
			, where(MYSQL, Dsl.regexp(TestSchema.Site.name, "^[0-9]+$")));

		assertEquals("(\"site\".\"name\" ~ ?)"
			, where(POSTGRESQL, Dsl.regexp(TestSchema.Site.name, "^[0-9]+$")));

		assertEquals("(\"site\".\"name\" ~* ?)"
			, where(POSTGRESQL, Dsl.regexpIgnoreCase(TestSchema.Site.name, "^a")));

	}

	// endregion

	// region 集約

	@Test
	@DisplayName("重複を除いた件数")
	void countDistinct () {

		assertEquals("COUNT(DISTINCT `site`.`group_id`)"
			, sql(MYSQL, Dsl.countDistinct(TestSchema.Site.group_id)));

		assertEquals("COUNT(DISTINCT \"site\".\"group_id\")"
			, sql(POSTGRESQL, Dsl.countDistinct(TestSchema.Site.group_id)));

	}

	@Test
	@DisplayName("GROUP_CONCAT と STRING_AGG は区切りの書き方がまるで違う")
	void groupConcat () {

		assertEquals("GROUP_CONCAT(`site`.`name` SEPARATOR ',')"
			, sql(MYSQL, Dsl.groupConcat(TestSchema.Site.name)));

		// STRING_AGG は文字列でないと受け取らないので text にする
		assertEquals("STRING_AGG((\"site\".\"name\")::text, ',')"
			, sql(POSTGRESQL, Dsl.groupConcat(TestSchema.Site.name)));

		assertEquals("GROUP_CONCAT(DISTINCT `site`.`name` SEPARATOR '/')"
			, sql(MYSQL, Dsl.groupConcatDistinct(TestSchema.Site.name, "/")));

		assertEquals("STRING_AGG(DISTINCT (\"site\".\"name\")::text, '/')"
			, sql(POSTGRESQL, Dsl.groupConcatDistinct(TestSchema.Site.name, "/")));

	}

	@Test
	@DisplayName("標準偏差と分散は標本のほうに揃える")
	void stddev () {

		assertEquals("STDDEV_SAMP(`site`.`feed_count`)"
			, sql(MYSQL, Dsl.stddev(TestSchema.Site.feed_count)));

		assertEquals("VAR_SAMP(\"site\".\"feed_count\")"
			, sql(POSTGRESQL, Dsl.variance(TestSchema.Site.feed_count)));

	}

	// endregion

	// region ウィンドウ関数

	@Test
	@DisplayName("ウィンドウ関数は両製品で同じ書き方")
	void window () {

		ISelect select = Dsl.rank()
			.partitionBy(TestSchema.Site.group_id)
			.orderBy(TestSchema.Site.feed_count.desc())
			.as("rank");

		assertEquals("RANK() OVER (PARTITION BY `site`.`group_id` ORDER BY `site`.`feed_count` DESC) AS `rank`"
			, sql(MYSQL, select));

		assertEquals("RANK() OVER (PARTITION BY \"site\".\"group_id\" ORDER BY \"site\".\"feed_count\" DESC) AS \"rank\""
			, sql(POSTGRESQL, select));

	}

	@Test
	@DisplayName("集約もウィンドウ関数にできる")
	void windowAggregate () {

		ISelect select = Dsl.over(Dsl.sum(TestSchema.Site.feed_count))
			.orderBy(TestSchema.Site.id.asc())
			.rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
			.as("total");

		assertEquals("SUM(`site`.`feed_count`) OVER (ORDER BY `site`.`id` ASC"
			+ " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS `total`"
			, sql(MYSQL, select));

	}

	@Test
	@DisplayName("LAG / ROW_NUMBER")
	void windowValue () {

		assertTrue(sql(MYSQL, Dsl.rowNumber().orderBy(TestSchema.Site.id.asc()).as("n"))
			.startsWith("ROW_NUMBER() OVER ("));

		/*
		 * 行数は<b>リテラル</b>で出す。MySQL の LAG / LEAD / NTILE は
		 * ? を置くと実行時に弾かれる。
		 */
		assertEquals("LAG(`site`.`feed_count`, 1) OVER (ORDER BY `site`.`id` ASC)"
			, sql(MYSQL, Dsl.lag(TestSchema.Site.feed_count, 1).orderBy(TestSchema.Site.id.asc())));

		assertEquals("NTILE(4) OVER (ORDER BY \"site\".\"id\" ASC)"
			, sql(POSTGRESQL, Dsl.nTile(4).orderBy(TestSchema.Site.id.asc())));

	}

	@Test
	@DisplayName("ウィンドウの ORDER BY に ? があっても数え落とさない")
	void windowOrderByParameters () {

		SelectBuilder builder = SQL
			.select(
				TestSchema.Site.id
				, Dsl.rank()
					.orderBy(new io.jimble.db.internal.sql.query.order_by.OrderByQuery(
						Dsl.ifThenElse(TestSchema.Site.feed_count.ge(20), 1, 0)).desc())
					.as("rank"))
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.group_id.eq(9L));

		/*
		 * ? を1つ出して1つも数えないと、<b>以降のバインドが全部ずれる</b>。
		 * ここでは SELECT に3つ、WHERE に1つ。
		 */
		assertEquals(List.of(20, 1, 0, 9L), builder.params());

	}

	@Test
	@DisplayName("ROWS BETWEEN は片方だけでは受け付けない")
	void windowFrameHalfOpen () {

		// 片方だけ通すと、フレームごと消えて「全体の合計」になる
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class
			, () -> Dsl.over(Dsl.sum(TestSchema.Site.feed_count))
				.rowsBetween(WindowFrame.unboundedPreceding(), null));

	}

	@Test
	@DisplayName("ウィンドウ関数の中身に別名や計算は付けられない")
	void windowInnerDecoration () {

		// 黙って落とすと SUM(x) * 2 が SUM(x) になる
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class
			, () -> Dsl.over(Dsl.sum(TestSchema.Site.feed_count).multiply(2)));

		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class
			, () -> Dsl.over(Dsl.sum(TestSchema.Site.feed_count).as("x")));

	}

	@Test
	@DisplayName("DECIMAL への変換は桁が要る")
	void castDecimalNeedsDigits () {

		// DECIMAL(0,0) は PostgreSQL では落ち、MySQL では黙って小数を捨てる
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class
			, () -> Dsl.cast(TestSchema.Site.name, CastType.DECIMAL));

	}

	@Test
	@DisplayName("関数の引数に一覧は渡せない")
	void listArgument () {

		/*
		 * ? は1つしか出ないのに、バインドは2つになる
		 * （Parameter#flatten が広げる）。以降が全部ずれる。
		 */
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class
			, () -> sql(MYSQL, Dsl.coalesce(TestSchema.Site.name, List.of(1, 2))));

	}

	@Test
	@DisplayName("ウィンドウ関数は条件に書けない")
	void windowFunctionInWhere () {

		/*
		 * Dsl.rowNumber().over(...).eq(1) と書けてしまうと、
		 * WHERE に ROW_NUMBER() OVER (...) = ? が並ぶ。
		 * SQL の決まりで禁じられているので DB が落とすが、
		 * <b>落ちるのは投げたときで、書いたときではない</b>。
		 */
		org.junit.jupiter.api.Assertions.assertThrows(SqlBuildException.class
			, () -> Dsl.rowNumber().partitionBy(TestSchema.Site.group_id).eq(1));

		// 選択の側（本来の使い方）は塞がない。<b>塞ぎ方が広すぎない</b>ことの確認
		assertEquals("ROW_NUMBER() OVER (PARTITION BY `site`.`group_id`)"
			, sql(MYSQL, Dsl.rowNumber().partitionBy(TestSchema.Site.group_id)));

	}

	@Test
	@DisplayName("in に空の一覧を渡したら組み立てた時点で落ちる")
	void inWithEmptyList () {

		// IN () という構文エラーの SQL を DB に投げない（要件 F-D-07）
		org.junit.jupiter.api.Assertions.assertThrows(SqlBuildException.class
			, () -> SQL.select(TestSchema.Site.id)
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.in(List.of()))
				.sql(MYSQL));

		org.junit.jupiter.api.Assertions.assertThrows(SqlBuildException.class
			, () -> SQL.select(TestSchema.Site.id)
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.not_in(new Object[0]))
				.sql(POSTGRESQL));

		// 1件でも入っていれば通る
		org.junit.jupiter.api.Assertions.assertTrue(
			SQL.select(TestSchema.Site.id)
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.in(List.of(1)))
				.sql(MYSQL)
				.contains("IN (?)"));

	}

	@Test
	@DisplayName("JSON の where から空の一覧が来ても落ちる（IN (NULL) にしない）")
	void inWithEmptyListFromJson () {

		/*
		 * クライアントから {"where": {"site": {"id|in": []}}} が飛んでくるのが、
		 * F-D-07 がいちばん効いてほしいところである。
		 *
		 * 以前は空の一覧が null に潰され、IN (?) ＋ NULL のバインドになっていた。
		 * <b>IN (NULL) はどの行にも当たらないので、黙って 0 件</b>になり、
		 * 例外も DB のエラーも出なかった。not_in なら逆に本来の全件が 0 件になる。
		 */
		io.jimble.util.data.Data where = new io.jimble.util.data.Data();
		io.jimble.util.data.Data site = new io.jimble.util.data.Data();
		site.put("id|in", List.of());
		io.jimble.util.data.Data tables = new io.jimble.util.data.Data();
		tables.put("site", site);
		where.put("where", tables);

		org.junit.jupiter.api.Assertions.assertThrows(SqlBuildException.class
			, () -> SQL.select()
				.from(TestSchema.Site.instance())
				.where(where)
				.sql(MYSQL));

	}

	@Test
	@DisplayName("in に null を渡しても落ちる")
	void inWithNull () {

		// IN (NULL) もどの行にも当たらない。取り違えが表に出ない
		org.junit.jupiter.api.Assertions.assertThrows(SqlBuildException.class
			, () -> SQL.select(TestSchema.Site.id)
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.in(null))
				.sql(MYSQL));

	}

	// endregion

}
