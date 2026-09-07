package io.jimble.db.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MigrationSql} のテスト
 *
 * <p>DB に触らないので通常の {@code build} で走る。</p>
 */
class MigrationSqlTest {

	// region up / down の分割

	@Test
	@DisplayName("!Ups と !Downs で分割できる")
	void toUpDown () {

		String[] upDown = MigrationSql.toUpDown("""
			# --- !Ups
			create table t (id int);
			# --- !Downs
			drop table t;
			""");

		assertEquals("create table t (id int);", upDown[0]);
		assertEquals("drop table t;", upDown[1]);

	}

	@Test
	@DisplayName("!Ups がなければ全体が up になる")
	void toUpDownWithoutUpsMarker () {

		String[] upDown = MigrationSql.toUpDown("create table t (id int);");

		assertEquals("create table t (id int);", upDown[0]);
		assertEquals("", upDown[1]);

	}

	@Test
	@DisplayName("!Downs がなければ down は空になる")
	void toUpDownWithoutDownsMarker () {

		String[] upDown = MigrationSql.toUpDown("# --- !Ups\ncreate table t (id int);");

		assertEquals("create table t (id int);", upDown[0]);
		assertEquals("", upDown[1]);

	}

	@Test
	@DisplayName("null でも落ちない")
	void toUpDownNull () {

		String[] upDown = MigrationSql.toUpDown(null);

		assertEquals("", upDown[0]);
		assertEquals("", upDown[1]);

	}

	// endregion

	// region SQL の分割

	@Test
	@DisplayName("セミコロンで複数文に分割する")
	void split () {

		List<String> sqls = MigrationSql.split("create table a (id int); create table b (id int);");

		assertEquals(List.of("create table a (id int);", "create table b (id int);"), sqls);

	}

	@Test
	@DisplayName("文字列リテラルの中のセミコロンでは分割しない")
	void splitKeepsSemicolonInLiteral () {

		List<String> sqls = MigrationSql.split("insert into t (v) values ('a;b'); insert into t (v) values ('c');");

		assertEquals(2, sqls.size());
		assertEquals("insert into t (v) values ('a;b');", sqls.get(0));

	}

	@Test
	@DisplayName("エスケープされたクォートはリテラルを閉じない")
	void splitKeepsEscapedQuote () {

		List<String> sqls = MigrationSql.split("insert into t (v) values ('a\\';b'); select 1;");

		assertEquals(2, sqls.size());
		assertEquals("insert into t (v) values ('a\\';b');", sqls.get(0));

	}

	@Test
	@DisplayName("末尾のセミコロンだけの断片は捨てる")
	void splitDropsBlankStatement () {

		List<String> sqls = MigrationSql.split("select 1;\n\n  \n");

		assertEquals(List.of("select 1;"), sqls);

	}

	@Test
	@DisplayName("セミコロンで終わらない最後の文も拾う")
	void splitKeepsLastStatementWithoutSemicolon () {

		List<String> sqls = MigrationSql.split("select 1; select 2");

		assertEquals(List.of("select 1;", "select 2"), sqls);

	}

	@Test
	@DisplayName("空文字なら空のリスト")
	void splitEmpty () {

		assertTrue(MigrationSql.split("").isEmpty());
		assertTrue(MigrationSql.split(null).isEmpty());

	}

	// endregion

}
