package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.db.sql.query.dsl.Dsl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Dsl.concat} / {@code Dsl.caseWhen} をそのまま式として書けることの確認
 *
 * <p>
 * 2.7.4 までは {@code IDsl} だけだったので、{@code select(...)} に渡すと
 * 「ただの値」として {@code ?} になり、<b>部品のオブジェクトそのもの</b>
 * （toString が {@code Concat@1e67b872}）がバインドされていた。
 * DB は文字列として受けるので落ちず、<b>毎回違う値が返っていた</b>。
 * </p>
 *
 * <p><b>DB 接続なし。</b>出す SQL とパラメータをそのまま固定する。</p>
 */
class DslAsExpressionTest {

	/* MySQL */
	private static final Dialect MYSQL = MySqlDialect.INSTANCE;

	/* PostgreSQL */
	private static final Dialect POSTGRESQL = PostgreSqlDialect.INSTANCE;

	// region concat

	@Test
	@DisplayName("concat を SELECT 句にそのまま書けて、別名が付く")
	void concatInSelect () {

		SelectBuilder builder = SQL
			.select(TestSchema.Site.id, Dsl.concat(TestSchema.Site.name, "-", TestSchema.Site.id).as("label"))
			.from(TestSchema.Site.instance());

		assertEquals("SELECT `site`.`id` AS `site__id`, CONCAT(`site`.`name`,?,`site`.`id`) AS `label` FROM `site`"
			, builder.sql(MYSQL));
		assertEquals("SELECT \"site\".\"id\" AS \"site__id\", (\"site\".\"name\" || ? || \"site\".\"id\") AS \"label\" FROM \"site\""
			, builder.sql(POSTGRESQL));

		// 部品のオブジェクトではなく、区切りの値だけがバインドされる
		assertEquals(List.of("-"), builder.params());

	}

	@Test
	@DisplayName("concat を別名なしで渡しても ? にならない")
	void concatWithoutAlias () {

		SelectBuilder builder = SQL
			.select(Dsl.concat(TestSchema.Site.name, TestSchema.Site.id))
			.from(TestSchema.Site.instance());

		assertEquals("SELECT CONCAT(`site`.`name`,`site`.`id`) FROM `site`", builder.sql(MYSQL));
		assertEquals("SELECT (\"site\".\"name\" || \"site\".\"id\") FROM \"site\"", builder.sql(POSTGRESQL));
		assertEquals(List.of(), builder.params());

	}

	@Test
	@DisplayName("concat を GROUP BY・ORDER BY・WHERE の左辺に書ける（パラメータは SQL に出る順）")
	void concatInOtherClauses () {

		SelectBuilder builder = SQL
			.select(Dsl.concat(TestSchema.Site.name, "/").as("k"), Dsl.count())
			.from(TestSchema.Site.instance())
			.where(Dsl.concat(TestSchema.Site.name, "#").eq("a#"))
			.groupBy(Dsl.concat(TestSchema.Site.name, "/"))
			.orderBy(Dsl.concat(TestSchema.Site.name, "/").desc());

		assertEquals("SELECT CONCAT(`site`.`name`,?) AS `k`, COUNT(*) FROM `site`"
				+ " WHERE (CONCAT(`site`.`name`,?) = ?)"
				+ " GROUP BY CONCAT(`site`.`name`,?)"
				+ " ORDER BY CONCAT(`site`.`name`,?) DESC"
			, builder.sql(MYSQL));
		assertEquals("SELECT (\"site\".\"name\" || ?) AS \"k\", COUNT(*) FROM \"site\""
				+ " WHERE ((\"site\".\"name\" || ?) = ?)"
				+ " GROUP BY (\"site\".\"name\" || ?)"
				+ " ORDER BY (\"site\".\"name\" || ?) DESC"
			, builder.sql(POSTGRESQL));
		assertEquals(List.of("/", "#", "a#", "/", "/"), builder.params());

	}

	@Test
	@DisplayName("concat を右辺に置いたときの SQL は変わらない")
	void concatAsRightHandSide () {

		SelectBuilder builder = SQL
			.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.name.eq(Dsl.concat("a", TestSchema.Site.id)));

		assertEquals("SELECT `site`.`id` AS `site__id` FROM `site` WHERE ( `site`.`name` = CONCAT(?,`site`.`id`))"
			, builder.sql(MYSQL));
		assertEquals("SELECT \"site\".\"id\" AS \"site__id\" FROM \"site\" WHERE ( \"site\".\"name\" = (? || \"site\".\"id\"))"
			, builder.sql(POSTGRESQL));
		assertEquals(List.of("a"), builder.params());

	}

	// endregion

	// region case

	@Test
	@DisplayName("caseWhen を end() なしで SELECT 句に書けて、別名が付く")
	void caseInSelect () {

		SelectBuilder builder = SQL
			.select(Dsl.caseWhen()
				.when(TestSchema.Site.feed_count.ge(100L)).then("多い")
				.elseCase("少ない").as("volume"))
			.from(TestSchema.Site.instance());

		assertEquals("SELECT CASE WHEN  `site`.`feed_count` >= ? THEN ? ELSE ? END AS `volume` FROM `site`"
			, builder.sql(MYSQL));
		assertEquals("SELECT CASE WHEN  \"site\".\"feed_count\" >= ? THEN ? ELSE ? END AS \"volume\" FROM \"site\""
			, builder.sql(POSTGRESQL));
		assertEquals(List.of(100L, "多い", "少ない"), builder.params());

	}

	// endregion

	// region 式にならない部品

	@Test
	@DisplayName("式にならない部品を SELECT 句に渡すと、? にせず組み立てたところで落ちる")
	void sqlPartInSelect () {

		SqlBuildException match = assertThrows(SqlBuildException.class
			, () -> SQL.select(Dsl.match(TestSchema.Site.name).against("a")));
		assertTrue(match.getMessage().contains("Match"), match.getMessage());

		assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.id.eq(1L)));
		assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.id.asc()));
		assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.instance()));
		assertThrows(SqlBuildException.class
			, () -> SQL.select(List.of(TestSchema.Site.id, SQL.select(TestSchema.Site.id))));

	}

	@Test
	@DisplayName("素の値はこれまでどおり ? にしてバインドする")
	void plainValueInSelect () {

		SelectBuilder builder = SQL.select(TestSchema.Site.id, "x", 1L).from(TestSchema.Site.instance());

		assertEquals("SELECT `site`.`id` AS `site__id`, ?, ? FROM `site`", builder.sql(MYSQL));
		assertEquals(List.of("x", 1L), builder.params());

	}

	// endregion

}
