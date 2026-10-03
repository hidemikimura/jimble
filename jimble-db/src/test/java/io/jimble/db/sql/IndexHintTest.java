package io.jimble.db.sql;

import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * インデックスのヒント（D-265。DB 接続なし）
 */
class IndexHintTest {

	@Test
	@DisplayName("FROM のテーブル名のあとに FORCE INDEX を書く。列の並べ方とパラメータは変わらない")
	void forceIndex () {

		SelectBuilder builder = SQL.select()
			.from(TestSchema.Site.instance().forceIndex("site__group_id"))
			.where(TestSchema.Site.group_id.eq(3L));

		String sql = builder.sql(MySqlDialect.INSTANCE);

		assertTrue(sql.contains(" FROM `site` FORCE INDEX (`site__group_id`) WHERE "), sql);
		assertTrue(sql.contains("`site`.`name` AS `site__name`"), "from のテーブルから列を並べていない: " + sql);
		assertEquals(List.of(3L), builder.params());

	}

	@Test
	@DisplayName("JOIN するテーブルにも付けられる（USE INDEX）。名前を並べれば、すべて書く（IGNORE INDEX）")
	void joinAndMany () {

		String sql = SQL.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance().ignoreIndex("PRIMARY", "site__name"))
			.left(TestSchema.Feed.instance().useIndex("feed__site_id"))
			.on(TestSchema.Feed.site_id.eq(TestSchema.Site.id))
			.sql(MySqlDialect.INSTANCE);

		assertTrue(sql.contains(" FROM `site` IGNORE INDEX (`PRIMARY`, `site__name`) LEFT JOIN `feed` USE INDEX (`feed__site_id`) ON ("), sql);

	}

	@Test
	@DisplayName("PostgreSQL にはヒントが無いので、組み立てたところで DialectException（黙って外さない）")
	void postgres () {

		SelectBuilder builder = SQL.select().from(TestSchema.Site.instance().forceIndex("site__group_id"));

		DialectException ex = assertThrows(DialectException.class, () -> builder.sql(PostgreSqlDialect.INSTANCE));
		assertTrue(ex.getMessage().contains("FORCE INDEX"), ex.getMessage());

	}

	@Test
	@DisplayName("名前が無い・名前に見えないもの（空白・引用符・括弧）は、組み立てる前に断る")
	void rejectsNames () {

		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().forceIndex());
		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().forceIndex((String) null));
		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().forceIndex("site name"));
		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().useIndex("a`) UNION SELECT 1 -- "));
		assertThrows(SqlBuildException.class, () -> TestSchema.Site.instance().ignoreIndex(""));

	}

}
