package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.query.dsl.Dsl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移送した SQL ビルダーが動くことの確認
 *
 * <p>
 * <b>DB 接続なしで、生成される SQL とバインドパラメータを突き合わせる</b>（要件 F-D-06 / NF-T-01）。
 * 推測ではなく実際の出力で固定する。
 * </p>
 */
class SqlBuilderPortingTest {

	/* site.id */
	private static final Column SITE_ID = TestSchema.Site.id;

	/* site.name */
	private static final Column SITE_NAME = TestSchema.Site.name;

	@Test
	@DisplayName("F-D-02 単一テーブルの SELECT でも列に テーブル名__列名 の別名が付く")
	void selectAlwaysAliasesColumns () {

		SelectBuilder builder = SQL
			.select()
			.from(TestSchema.Site.instance())
			.where(SITE_ID.eq(1L));

		String sql = builder.sql();

		assertTrue(sql.contains("`site`.`id` AS `site__id`"), sql);
		assertTrue(sql.contains("`site`.`name` AS `site__name`"), sql);
		assertEquals(List.of(1L), builder.params());

	}

	@Test
	@DisplayName("WHERE の条件式とパラメータの順序")
	void whereConditions () {

		SelectBuilder builder = SQL
			.select(SITE_ID, SITE_NAME)
			.from(TestSchema.Site.instance())
			.where(
				TestSchema.Site.group_id.eq(10L)
					.and(SITE_NAME.like("%まとめ%"))
					.and(TestSchema.Site.deleted_at.is_null())
			);

		assertEquals(List.of(10L, "%まとめ%"), builder.params(), builder.sql());
		assertTrue(builder.sql().contains("IS NULL"), builder.sql());

	}

	@Test
	@DisplayName("JOIN が組める")
	void join () {

		SelectBuilder builder = SQL
			.select()
			.from(
				TestSchema.Site.instance().left(TestSchema.Feed.instance()).on(
					SITE_ID.eq(TestSchema.Feed.site_id)
				)
			)
			.where(TestSchema.Feed.title.is_not_null());

		String sql = builder.sql();

		assertTrue(sql.contains("LEFT JOIN"), sql);
		assertTrue(sql.contains("`feed`.`title` AS `feed__title`"), sql);
		assertTrue(sql.contains("`site`.`id` AS `site__id`"), sql);

	}

	@Test
	@DisplayName("ORDER BY / LIMIT / OFFSET")
	void orderLimitOffset () {

		SelectBuilder builder = SQL
			.select()
			.from(TestSchema.Site.instance())
			.orderBy(TestSchema.Site.feed_count.desc(), SITE_ID)
			.limit(20)
			.offset(40);

		String sql = builder.sql();

		assertTrue(sql.contains("ORDER BY"), sql);
		assertTrue(sql.contains("DESC"), sql);
		assertTrue(sql.contains("LIMIT"), sql);
		assertTrue(sql.contains("OFFSET"), sql);

	}

	@Test
	@DisplayName("INSERT が組める")
	void insert () {

		InsertBuilder builder = SQL
			.insert(TestSchema.Site.instance())
			.value(TestSchema.Site.group_id, 1L)
			.value(SITE_NAME, "俺的まとめ");

		String sql = builder.sql();

		assertTrue(sql.startsWith("INSERT"), sql);
		assertTrue(sql.contains("`site`"), sql);
		assertEquals(List.of(1L, "俺的まとめ"), builder.params());

	}

	@Test
	@DisplayName("UPDATE が組める。SQL リテラルはプレースホルダにならない")
	void update () {

		UpdateBuilder builder = SQL
			.update(TestSchema.Site.instance())
			.set(SITE_NAME, "新しい名前")
			.set(TestSchema.Site.deleted_at, Dsl.now())
			.where(SITE_ID.eq(5L));

		String sql = builder.sql();

		assertTrue(sql.startsWith("UPDATE"), sql);
		assertTrue(sql.toUpperCase().contains("NOW()"), sql);
		assertEquals(List.of("新しい名前", 5L), builder.params(), "Dsl.now() はパラメータにならない");

	}

	@Test
	@DisplayName("DELETE が組める")
	void delete () {

		DeleteBuilder builder = SQL
			.delete(TestSchema.Site.instance())
			.where(SITE_ID.eq(3L));

		assertTrue(builder.sql().startsWith("DELETE"), builder.sql());
		assertEquals(List.of(3L), builder.params());

	}

	@Test
	@DisplayName("in() は値の数だけプレースホルダを作る")
	void in () {

		SelectBuilder builder = SQL
			.select()
			.from(TestSchema.Site.instance())
			.where(SITE_ID.in(List.of(1L, 2L, 3L)));

		assertEquals(List.of(1L, 2L, 3L), builder.params(), builder.sql());

	}

	@Test
	@DisplayName("F-D-07【既知の落とし穴】in() に空コレクションを渡すと壊れた SQL になる")
	void emptyInIsBroken () {

		SelectBuilder builder = SQL
			.select()
			.from(TestSchema.Site.instance())
			.where(SITE_ID.in(List.of()));

		String sql = builder.sql();

		// 現状の仕様を明示的に固定しておく。改善したらこのテストを書き換える
		assertTrue(sql.contains("IN ()") || sql.contains("IN()"), "現状は IN () になる: " + sql);

	}

	@Test
	@DisplayName("集約関数が使える")
	void aggregate () {

		SelectBuilder builder = SQL
			.select(Dsl.count(SITE_ID))
			.from(TestSchema.Site.instance());

		assertTrue(builder.sql().toUpperCase().contains("COUNT("), builder.sql());

	}

	@Test
	@DisplayName("同じビルダーから何度でも SQL を取り出せる")
	void sqlIsRepeatable () {

		SelectBuilder builder = SQL
			.select()
			.from(TestSchema.Site.instance())
			.where(SITE_ID.eq(1L));

		assertEquals(builder.sql(), builder.sql());
		assertEquals(builder.params(), builder.params());

	}

}
