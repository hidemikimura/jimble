package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 組み立てで黙って間違える書き方を、例外か正しい SQL にする（要件 D-190）
 *
 * <p>
 * どれも 1.4 までは<b>例外が出ずに、違う SQL が組み上がっていた</b>。
 * 組み上がった SQL の字面とパラメータで固定する。
 * </p>
 */
@SuppressWarnings("removal")  // 1.x の書き方も確かめている（2.0 で消す。要件 D-192）
class BuilderTrapTest {

	private static final Column SITE_ID = TestSchema.Site.id;

	private static final Column SITE_NAME = TestSchema.Site.name;

	private static final Column FEED_COUNT = TestSchema.Site.feed_count;

	// region ON

	@Test
	@DisplayName("D-190 ON は直前の JOIN に付く（inner(...).on(...) の続けて書く形）")
	void onAttachesToJoin () {

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.left(TestSchema.Feed.instance()).on(SITE_ID.eq(TestSchema.Feed.site_id));

		String sql = builder.sql();
		assertTrue(sql.contains("LEFT JOIN `feed` ON (( `site`.`id` = `feed`.`site_id`))"), sql);

	}

	@Test
	@DisplayName("D-190 JOIN の無いところの on(...) は例外（1.4 までは ON が黙って消えていた）")
	void onWithoutJoinThrows () {

		SqlBuildException e = assertThrows(SqlBuildException.class, () -> SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.on(SITE_ID.eq(1L)));
		assertTrue(e.getMessage().contains("inner(...) か left(...)"), e.getMessage());

		assertThrows(SqlBuildException.class, () -> new SelectBuilder(SITE_ID).on(SITE_ID.eq(1L)));
		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().on(SITE_ID.eq(1L)));

	}

	@Test
	@DisplayName("D-190 on(...) を2度呼ぶと AND でつながる（1.4 までは先の条件が消えていた）")
	void onTwiceAppends () {

		String sql = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.inner(TestSchema.Feed.instance())
			.on(SITE_ID.eq(TestSchema.Feed.site_id))
			.on(TestSchema.Feed.title.is_not_null())
			.sql();

		assertTrue(sql.contains("ON (( `site`.`id` = `feed`.`site_id`) AND ( `feed`.`title` IS NOT NULL))"), sql);

	}

	// endregion

	// region 比較

	@Test
	@DisplayName("D-190 列に直接 and / or は例外（1.4 までは null を返していた）")
	void columnAndOrThrow () {

		assertThrows(SqlBuildException.class, () -> SITE_ID.and(SITE_NAME.eq("a")));
		assertThrows(SqlBuildException.class, () -> SITE_ID.or(SITE_NAME.eq("a")));

	}

	@Test
	@DisplayName("D-190 1つの条件に比較を2つ重ねると例外（1.4 までは前のが上書きされていた）")
	void secondComparisonThrows () {

		SqlBuildException e = assertThrows(SqlBuildException.class, () -> SITE_ID.ge(1L).le(9L));
		assertTrue(e.getMessage().contains("between"), e.getMessage());

		// and でつないだ先の比較は別の条件なので通る
		IWhere ok = SITE_ID.ge(1L).and(SITE_ID.le(9L));
		SelectBuilder builder = SQL.select(SITE_ID).from(TestSchema.Site.instance()).where(ok);
		assertEquals(List.of(1L, 9L), builder.params(), builder.sql());

	}

	// endregion

	// region 四則演算

	@Test
	@DisplayName("D-190 四則演算は書いた順に全部出る（1.4 までは最初の1つだけ）")
	void arithmeticChains () {

		SelectBuilder builder = SQL
			.select(FEED_COUNT.plus(1).multiply(2).as("x"))
			.from(TestSchema.Site.instance());

		String sql = builder.sql();
		assertTrue(sql.startsWith("SELECT (`site`.`feed_count` + ?) * ? AS `x` FROM"), sql);
		assertEquals(List.of(1, 2), builder.params(), sql);

	}

	@Test
	@DisplayName("D-190 演算が1つなら括弧を付けない（字面が 1.4 と同じ＝結果キャッシュの鍵が変わらない）")
	void singleArithmeticUnchanged () {

		String sql = SQL
			.select(FEED_COUNT.minus(1).as("x"))
			.from(TestSchema.Site.instance())
			.sql();

		assertTrue(sql.startsWith("SELECT `site`.`feed_count` - ? AS `x` FROM"), sql);

	}

	@Test
	@DisplayName("D-190 divide は割り算。subtract は非推奨で、振る舞いは割り算のまま")
	@SuppressWarnings("removal")
	void divideAndSubtract () {

		String divide = SQL.select(FEED_COUNT.divide(2).as("x")).from(TestSchema.Site.instance()).sql();
		String subtract = SQL.select(FEED_COUNT.subtract(2).as("x")).from(TestSchema.Site.instance()).sql();

		assertTrue(divide.contains("`site`.`feed_count` / ? AS `x`"), divide);
		assertEquals(divide, subtract);

	}

	@Test
	@DisplayName("D-190 値（SelectValue）の四則演算が null を返さない")
	void selectValueArithmetic () {

		SelectBuilder builder = SQL.select(Dsl.value(3).plus(4)).from(TestSchema.Site.instance());
		String sql = builder.sql();
		assertTrue(sql.contains("? + ?"), sql);
		assertEquals(List.of(3, 4), builder.params(), sql);

	}

	// endregion

	// region Dsl.and / or / allOf / anyOf

	@Test
	@DisplayName("D-190 anyOf は括弧でまとめ、引数を書き換えない")
	void anyOfGroups () {

		IWhere a = TestSchema.Site.group_id.eq(1L);
		IWhere b = TestSchema.Site.group_id.eq(2L);

		SelectBuilder builder = SQL
			.select(SITE_ID)
			.from(TestSchema.Site.instance())
			.where(SITE_NAME.eq("x"), Dsl.anyOf(a, b));

		String sql = builder.sql();
		assertTrue(sql.endsWith("WHERE ( `site`.`name` = ?) AND (  `site`.`group_id` = ? OR  `site`.`group_id` = ?)"), sql);
		assertEquals(List.of("x", 1L, 2L), builder.params(), sql);

		// a は書き換わっていない：単独で使っても OR が付かない
		String alone = SQL.select(SITE_ID).from(TestSchema.Site.instance()).where(SITE_NAME.eq("x"), a).sql();
		assertTrue(!alone.contains(" OR "), alone);

	}

	@Test
	@DisplayName("D-190 Dsl.or に素の列を渡すと、ClassCastException ではなく直し方つきの例外")
	void dslOrWithBareColumn () {

		SqlBuildException e = assertThrows(SqlBuildException.class, () -> Dsl.or(SITE_ID));
		assertTrue(e.getMessage().contains("anyOf"), e.getMessage());
		assertThrows(SqlBuildException.class, () -> Dsl.allOf());

	}

	// endregion

	// region Data から組む

	@Test
	@DisplayName("D-190 orderBy(Data) は asc / desc 以外を例外にする（1.4 までは綴り違いが黙って ASC）")
	void orderByDataRejectsTypos () {

		Data ok = Data.fromJsonString("{\"order\":{\"site\":{\"id\":\"DESC\",\"name\":\"asc\"}}}");
		String sql = SQL.select(SITE_ID).from(TestSchema.Site.instance()).orderBy(ok).sql();
		assertTrue(sql.contains("`site`.`id` DESC"), sql);

		Data typo = Data.fromJsonString("{\"order\":{\"site\":{\"id\":\"descending\"}}}");
		assertThrows(SqlBuildException.class,
			() -> SQL.select(SITE_ID).from(TestSchema.Site.instance()).orderBy(typo));

	}

	@Test
	@DisplayName("D-190 where(Data) の between に2つ揃わなければ例外（1.4 までは条件ごと消えて全件）")
	void betweenNeedsTwo () {

		Data one = Data.fromJsonString("{\"where\":{\"site\":{\"created_at|between\":[\"2026-01-01\"]}}}");
		assertThrows(SqlBuildException.class,
			() -> SQL.select(SITE_ID).from(TestSchema.Site.instance()).where(one));

	}

	// endregion

	// region 写し

	@Test
	@DisplayName("D-190 simpleSql は builder を書き換えない（1.4 までは呼ぶたびに PK 列が増えていた）")
	void simpleSqlDoesNotMutate () {

		SelectBuilder builder = SQL.select(SITE_NAME).from(TestSchema.Site.instance()).where(SITE_ID.gt(1L));
		String before = builder.sql();

		String simple1 = builder.simpleSql();
		String simple2 = builder.simpleSql();

		assertEquals(simple1, simple2, "呼ぶたびに変わっています");
		assertEquals(before, builder.sql(), "builder が書き換わっています");

	}

	@Test
	@DisplayName("D-190 copy() の句は元と別のリスト")
	void copyIsIndependent () {

		SelectBuilder builder = SQL.select(SITE_ID).from(TestSchema.Site.instance()).where(SITE_ID.gt(1L)).limit(5);
		String before = builder.sql();

		SelectBuilder copy = builder.copy();
		assertEquals(before, copy.sql());

		copy.clearWhere().where(SITE_ID.eq(9L)).limit(-1);
		assertEquals(before, builder.sql(), "写しを触ったら元が変わっています");

	}

	// endregion

}
