package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.db.sql.definition.table.TemporaryTable;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.dsl.FreeSQL;
import io.jimble.db.sql.query.select.SelectQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL の逃げ道と特殊な構文（要件 F-D-09）
 *
 * <p>
 * CASE 式 / 仮テーブル・仮列 / 自由 SQL / 地理空間関数 / 全文検索の MATCH。
 * <b>DB 接続なしで、生成される SQL を突き合わせる</b>（要件 F-D-06 / NF-T-01）。
 * </p>
 */
class SqlDslTest {

	/* site.id */
	private static final Column SITE_ID = TestSchema.Site.id;

	/* site.name */
	private static final Column SITE_NAME = TestSchema.Site.name;

	/* site.feed_count */
	private static final Column SITE_FEED_COUNT = TestSchema.Site.feed_count;

	// region CASE 式

	@Test
	@DisplayName("CASE 式が書ける")
	void caseExpression () {

		/*
		 * Case は IDsl なので、SELECT に置くときは ISelect に包む。
		 * SQL.select(caseWhen) と直接渡すと「ただの値」として ? にバインドされる。
		 */
		SelectBuilder builder = SQL
			.select(new SelectQuery().dsl(Dsl.caseWhen()
				.when(SITE_FEED_COUNT.ge(100L)).then("多い")
				.when(SITE_FEED_COUNT.ge(10L)).then("ふつう")
				.elseCase("少ない")).as("volume"))
			.from(TestSchema.Site.instance());

		String sql = builder.sql();

		assertTrue(sql.contains("CASE"), sql);
		assertTrue(sql.contains("WHEN"), sql);
		assertTrue(sql.contains("ELSE"), sql);
		assertTrue(sql.contains("END"), sql);

		// 値はバインドされる（要件 NF-S-01）
		assertTrue(builder.params().contains("多い"), builder.params().toString());
		assertTrue(builder.params().contains(100L), builder.params().toString());

	}

	// endregion

	// region 仮テーブル・仮列

	@Test
	@DisplayName("仮テーブルと仮列でサブクエリの結果を指せる")
	void temporaryTableAndColumn () {

		TemporaryTable summary = new TemporaryTable("summary");
		TemporaryColumn total = new TemporaryColumn(summary, "total");

		SelectBuilder builder = SQL
			.select(total)
			.from(summary)
			.where(total.gt(0L));

		String sql = builder.sql();

		assertTrue(sql.contains("`summary`.`total`"), sql);
		assertTrue(sql.contains("FROM"), sql);
		assertTrue(sql.contains("`summary`"), sql);

	}

	// endregion

	// region 自由 SQL

	@Test
	@DisplayName("ビルダーで書けないものは自由 SQL で逃げられる")
	void freeSql () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(SITE_ID.eq(new FreeSQL("(SELECT MAX(id) FROM site WHERE group_id = ?)", 3L)));

		String sql = builder.sql();

		assertTrue(sql.contains("SELECT MAX(id) FROM site"), sql);
		assertTrue(builder.params().contains(3L), builder.params().toString());

	}

	@Test
	@DisplayName("用意された自由 SQL（何秒前）が使える")
	void secondsAgo () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.deleted_at.gt(Dsl.secondsAgo(60)));

		String sql = builder.sql();

		assertTrue(sql.contains("CURRENT_TIMESTAMP + INTERVAL - ? SECOND"), sql);
		assertTrue(builder.params().contains(60L), builder.params().toString());

	}

	// endregion

	// region 地理空間

	@Test
	@DisplayName("地理空間の関数が使える")
	void geometry () {

		SelectBuilder builder = SQL
			.select(Dsl.stDistanceSphere(
				Dsl.stGeomFromText("POINT(135.0 35.0)")
				, Dsl.stGeomFromText("POINT(139.0 35.6)")))
			.from(TestSchema.Site.instance());

		String sql = builder.sql();

		assertTrue(sql.contains("ST_Distance_Sphere"), sql);
		assertTrue(sql.contains("ST_GeomFromText"), sql);
		assertTrue(builder.params().contains("POINT(135.0 35.0)"), builder.params().toString());

	}

	@Test
	@DisplayName("ST_Within が条件に使える")
	void within () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(Dsl.stWithin(
				Dsl.stGeomFromText("POINT(135.0 35.0)")
				, Dsl.stGeomFromText("POLYGON((0 0,0 1,1 1,1 0,0 0))")));

		assertTrue(builder.sql().contains("ST_Within"), builder.sql());

	}

	// endregion

	// region 全文検索

	@Test
	@DisplayName("MATCH 構文が書ける")
	void match () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(Dsl.match(SITE_NAME).against("まとめ").inBooleanMode());

		String sql = builder.sql();

		assertTrue(sql.contains("MATCH"), sql);
		assertTrue(sql.contains("AGAINST"), sql);
		assertTrue(sql.contains("IN BOOLEAN MODE"), sql);
		assertTrue(builder.params().contains("まとめ"), builder.params().toString());

	}

	@Test
	@DisplayName("MATCH の検索語もバインドする（要件 NF-S-01）")
	void matchBindsValue () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(Dsl.match(SITE_NAME).against("' OR 1=1 --").defaultMode());

		assertTrue(builder.sql().contains("AGAINST(?"), builder.sql());
		assertEquals("' OR 1=1 --", builder.params().getLast());

	}

	// endregion

	// region Column からのパスパラメータ（要件 F-R-18）

	@Test
	@DisplayName("Column からルートのパスパラメータを作れる")
	void urlPathPlaceholder () {

		/*
		 * パラメータ名とカラム名がずれないようにするための口（要件 F-R-18）。
		 *   get("/sites/" + Site.id.urlPathPlaceholder(), ...)
		 */
		assertEquals("{site.id}", SITE_ID.urlPathPlaceholder());
		assertEquals("{site.name}", SITE_NAME.urlPathPlaceholder());

	}

	// endregion

}
