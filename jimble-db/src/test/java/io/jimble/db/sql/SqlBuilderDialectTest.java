package io.jimble.db.sql;

import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.select.SelectQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同じビルダーが製品ごとに違う SQL を出すことの確認（要件 F-D-30）
 *
 * <p>
 * <b>DB 接続なし。</b>アプリ側のコードは1行も変えず、
 * 渡す方言だけを変えて出力を突き合わせる。
 * </p>
 *
 * <p>
 * MySQL 側の出力は<b>方言化する前と同じ</b>でなければならない。
 * ここを固定しておかないと、PostgreSQL 対応のついでに
 * <b>いま動いているアプリの SQL が静かに変わる</b>。
 * </p>
 */
class SqlBuilderDialectTest {

	@Test
	@DisplayName("SELECT の識別子の囲みが製品で変わる")
	void select () {

		SelectBuilder builder = SQL
			.select(TestSchema.Site.id, TestSchema.Site.name)
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.id.eq(1L));

		String mysql = builder.sql(MySqlDialect.INSTANCE);
		String pg = builder.sql(PostgreSqlDialect.INSTANCE);

		assertTrue(mysql.contains("`site`.`id` AS `site__id`"), mysql);
		assertTrue(pg.contains("\"site\".\"id\" AS \"site__id\""), pg);

		// 別名の区切り（F-D-02）は製品によらず同じ
		assertTrue(pg.contains("site__name"), pg);

	}

	@Test
	@DisplayName("サブクエリにも同じ方言が伝わる")
	void subQuery () {

		SelectBuilder inner = SQL
			.select(TestSchema.Feed.site_id)
			.from(TestSchema.Feed.instance());

		SelectBuilder builder = SQL
			.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.id.in(inner));

		String pg = builder.sql(PostgreSqlDialect.INSTANCE);

		// バッククォートが1つでも混ざったら PostgreSQL は構文エラーになる
		assertTrue(pg.indexOf('`') < 0, pg);

	}

	@Test
	@DisplayName("INSERT ... ON DUPLICATE KEY UPDATE は ON CONFLICT になる")
	void upsert () {

		InsertBuilder builder = SQL
			.insert(TestSchema.Site.instance())
			.value(TestSchema.Site.id, 1L)
			.value(TestSchema.Site.name, "あ")
			.onDuplicateKeyUpdateValues(TestSchema.Site.name);

		assertTrue(builder.sql(MySqlDialect.INSTANCE)
			.contains("ON DUPLICATE KEY UPDATE `name` = VALUES(`site`.`name`)")
			, builder.sql(MySqlDialect.INSTANCE));

		assertTrue(builder.sql(PostgreSqlDialect.INSTANCE)
			.contains("ON CONFLICT (\"id\") DO UPDATE SET \"name\" = EXCLUDED.\"name\"")
			, builder.sql(PostgreSqlDialect.INSTANCE));

	}

	@Test
	@DisplayName("主キーを入れない upsert は、埋まっている一意キーで衝突を見る")
	void upsertOnUniqueKey () {

		InsertBuilder builder = SQL
			.insert(TestSchema.Customer.instance())
			.value(TestSchema.Customer.code, "C1")
			.value(TestSchema.Customer.name, "顧客1")
			.onDuplicateKeyUpdateValues(TestSchema.Customer.name);

		/*
		 * MySQL は「どの一意キーでも」発火するので書かなくてよい。
		 * PostgreSQL は書かせるので、id ではなく code を選ぶ必要がある。
		 * ここで id を選ぶと、衝突を見つけられずに一意制約違反で落ちる。
		 */
		assertTrue(builder.sql(PostgreSqlDialect.INSTANCE)
			.contains("ON CONFLICT (\"code\") DO UPDATE SET \"name\" = EXCLUDED.\"name\"")
			, builder.sql(PostgreSqlDialect.INSTANCE));

	}

	@Test
	@DisplayName("INSERT IGNORE は前ではなく後ろに付く")
	void insertIgnore () {

		InsertBuilder builder = SQL
			.insert(TestSchema.Site.instance())
			.value(TestSchema.Site.name, "あ")
			.ignore();

		String mysql = builder.sql(MySqlDialect.INSTANCE);
		String pg = builder.sql(PostgreSqlDialect.INSTANCE);

		assertTrue(mysql.startsWith("INSERT IGNORE INTO"), mysql);
		assertTrue(pg.startsWith("INSERT INTO"), pg);
		assertTrue(pg.endsWith(" ON CONFLICT DO NOTHING"), pg);

	}

	@Test
	@DisplayName("CONCAT は PostgreSQL では || になる（NULL の扱いを合わせる）")
	void concat () {

		SelectBuilder builder = SQL
			.select(new SelectQuery().dsl(
				Dsl.concat(TestSchema.Site.name, "-", TestSchema.Site.id)).as("label"))
			.from(TestSchema.Site.instance());

		assertTrue(builder.sql(MySqlDialect.INSTANCE).contains("CONCAT(")
			, builder.sql(MySqlDialect.INSTANCE));

		String pg = builder.sql(PostgreSqlDialect.INSTANCE);
		assertTrue(pg.contains(" || "), pg);
		assertTrue(!pg.contains("CONCAT("), pg);

	}

	@Test
	@DisplayName("書けないものは組み立てたところで落ちる（実行してから落ちない）")
	void unsupported () {

		SelectBuilder builder = SQL
			.select(Dsl.dateFormat(TestSchema.Site.deleted_at, "%Y-%m-%d"))
			.from(TestSchema.Site.instance());

		// MySQL では出せる
		assertTrue(builder.sql(MySqlDialect.INSTANCE).contains("DATE_FORMAT(")
			, builder.sql(MySqlDialect.INSTANCE));

		assertThrows(DialectException.class, () -> builder.sql(PostgreSqlDialect.INSTANCE));

	}

	@Test
	@DisplayName("引数なしの sql() は既定の方言（設定が無ければ MySQL）")
	void defaultDialect () {

		SelectBuilder builder = SQL
			.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance());

		assertEquals(builder.sql(MySqlDialect.INSTANCE), builder.sql());

	}

}
