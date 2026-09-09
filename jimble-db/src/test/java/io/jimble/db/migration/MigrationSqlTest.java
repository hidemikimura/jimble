package io.jimble.db.migration;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
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

	/** MySQL の読み方 */
	private static final Dialect MYSQL = MySqlDialect.INSTANCE;

	/** PostgreSQL の読み方 */
	private static final Dialect POSTGRESQL = PostgreSqlDialect.INSTANCE;

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
	@DisplayName("MySQL ではエスケープされたクォートはリテラルを閉じない")
	void splitKeepsEscapedQuote () {

		List<String> sqls = MigrationSql.split(
			"insert into t (v) values ('a\\';b'); select 1;", MYSQL);

		assertEquals(2, sqls.size());
		assertEquals("insert into t (v) values ('a\\';b');", sqls.get(0));

	}

	@Test
	@DisplayName("PostgreSQL ではバックスラッシュはただの文字（standard_conforming_strings）")
	void splitBackslashIsPlainOnPostgreSql () {

		/*
		 * 'c:\' で文字列は閉じている。エスケープとして読むと閉じていないことになり、
		 * 後ろの SQL が全部この1文にくっつく
		 */
		List<String> sqls = MigrationSql.split(
			"insert into t (v) values ('c:\\'); select 1;", POSTGRESQL);

		assertEquals(List.of("insert into t (v) values ('c:\\');", "select 1;"), sqls);

	}

	@Test
	@DisplayName("PostgreSQL でも E'...' の中だけはエスケープになる")
	void splitEscapeStringOnPostgreSql () {

		List<String> sqls = MigrationSql.split(
			"insert into t (v) values (E'a\\';b'); select 1;", POSTGRESQL);

		assertEquals(List.of("insert into t (v) values (E'a\\';b');", "select 1;"), sqls);

	}

	@Test
	@DisplayName("MySQL のバックスラッシュを PostgreSQL の読み方にすると分かれ方が変わる")
	void splitBackslashDiffersByProduct () {

		String sql = "insert into t (v) values ('c:\\'); select 1;";

		// MySQL の読み方だと \' はエスケープなので、文字列が閉じずに1文になる
		assertEquals(1, MigrationSql.split(sql, MYSQL).size());
		assertEquals(2, MigrationSql.split(sql, POSTGRESQL).size());

	}

	@Test
	@DisplayName("2つ続けたクォートはリテラルの中の「'」")
	void splitKeepsDoubledQuote () {

		List<String> sqls = MigrationSql.split(
			"insert into t (v) values ('it'';s'); select 1;", POSTGRESQL);

		assertEquals(List.of("insert into t (v) values ('it'';s');", "select 1;"), sqls);

	}

	@Test
	@DisplayName("識別子の中のセミコロンでは分割しない")
	void splitKeepsSemicolonInIdentifier () {

		assertEquals(List.of("select `a;b` from t"), MigrationSql.split("select `a;b` from t", MYSQL));
		assertEquals(List.of("select \"a;b\" from t"), MigrationSql.split("select \"a;b\" from t", POSTGRESQL));

		// PostgreSQL にバックティックは無い。囲みとして読むと後ろが全部くっつく
		assertEquals(List.of("select 1;", "` ;", "select 2;")
			, MigrationSql.split("select 1; ` ; select 2;", POSTGRESQL));

	}

	@Test
	@DisplayName("行コメントの中のセミコロンでは分割しない")
	void splitKeepsSemicolonInLineComment () {

		List<String> sqls = MigrationSql.split("-- 作る; いれる\ncreate table a (id int);", MYSQL);

		assertEquals(List.of("-- 作る; いれる\ncreate table a (id int);"), sqls);

	}

	@Test
	@DisplayName("# の行コメントは MySQL だけ")
	void splitHashCommentIsMySqlOnly () {

		assertEquals(List.of("# a; b\nselect 1;"), MigrationSql.split("# a; b\nselect 1;", MYSQL));
		assertEquals(List.of("# a;", "b\nselect 1;"), MigrationSql.split("# a; b\nselect 1;", POSTGRESQL));

	}

	@Test
	@DisplayName("MySQL の -- は空白が要る（1--2 はコメントではない）")
	void splitDashCommentNeedsSpaceOnMySql () {

		assertEquals(List.of("select 1--2;", "select 3;")
			, MigrationSql.split("select 1--2; select 3;", MYSQL));

		// PostgreSQL は空白が無くてもコメント
		assertEquals(List.of("select 1--2; select 3;")
			, MigrationSql.split("select 1--2; select 3;", POSTGRESQL));

	}

	@Test
	@DisplayName("ブロックコメントの中のセミコロンでは分割しない")
	void splitKeepsSemicolonInBlockComment () {

		List<String> sqls = MigrationSql.split("/* a; b */ select 1;", MYSQL);

		assertEquals(List.of("/* a; b */ select 1;"), sqls);

	}

	@Test
	@DisplayName("PostgreSQL のブロックコメントは入れ子にできる")
	void splitNestedBlockComment () {

		String sql = "/* a /* b */ ; */ select 1;";

		assertEquals(List.of("/* a /* b */ ; */ select 1;"), MigrationSql.split(sql, POSTGRESQL));

		// MySQL は最初の */ で閉じるので、中の ; で切れる（前半はコメントだけなので捨てる）
		assertEquals(List.of("*/ select 1;"), MigrationSql.split(sql, MYSQL));

	}

	@Test
	@DisplayName("コメントだけの断片は捨てる")
	void splitDropsCommentOnly () {

		// 末尾のコメントだけを実行すると「Query was empty」で落ちる
		assertEquals(List.of("select 1;")
			, MigrationSql.split("select 1; -- おわり", POSTGRESQL));

		assertTrue(MigrationSql.split("-- 説明だけ", POSTGRESQL).isEmpty());

	}

	@Test
	@DisplayName("文の前のコメントは、その文といっしょに残る")
	void splitKeepsLeadingComment () {

		// 改行が残っているので、行コメントは行末で終わる
		assertEquals(List.of("-- 作る;\ncreate table a (id int);")
			, MigrationSql.split("-- 作る;\ncreate table a (id int);", POSTGRESQL));

	}

	@Test
	@DisplayName("MySQL の /*! ... */ は捨てない（実行されるコメント）")
	void splitKeepsExecutableComment () {

		assertEquals(List.of("/*!40101 SET NAMES utf8 */;", "select 1;")
			, MigrationSql.split("/*!40101 SET NAMES utf8 */; select 1;", MYSQL));

		// MariaDB の書き方も
		assertEquals(List.of("/*M!100301 SET x = 1 */;", "select 1;")
			, MigrationSql.split("/*M!100301 SET x = 1 */; select 1;", MYSQL));

		// PostgreSQL ではただのコメントなので捨てる
		assertEquals(List.of("select 1;")
			, MigrationSql.split("/*!40101 SET NAMES utf8 */; select 1;", POSTGRESQL));

	}

	@Test
	@DisplayName("識別子の中の $ をドル引用符と読まない")
	void splitDollarInIdentifier () {

		// a$b$c をドル引用符と読むと、後ろの SQL が全部この1文にくっつく
		assertEquals(List.of("create table a$b$c (id int);", "select 1;")
			, MigrationSql.split("create table a$b$c (id int); select 1;", POSTGRESQL));

		// 位置パラメータも引用符の始まりではない
		assertEquals(List.of("select $1;", "select 2;")
			, MigrationSql.split("select $1; select 2;", POSTGRESQL));

	}

	@Test
	@DisplayName("方言を渡さなければ既定の方言で読む")
	void splitUsesDefaultDialect () {

		String sql = "insert into t (v) values ('c:\\'); select 1;";

		try {

			Dialects.defaultDialect(POSTGRESQL);
			assertEquals(2, MigrationSql.split(sql).size());

			Dialects.defaultDialect(MYSQL);
			assertEquals(1, MigrationSql.split(sql).size());

		} finally {
			Dialects.reset();
		}

	}

	@Test
	@DisplayName("PostgreSQL のドル引用符の中では分割しない")
	void splitKeepsSemicolonInDollarQuote () {

		String sql = """
			create function f() returns void as $$
			begin
				insert into t (v) values ('a');
				insert into t (v) values ('b');
			end;
			$$ language plpgsql;
			select 1;""";

		List<String> sqls = MigrationSql.split(sql, POSTGRESQL);

		assertEquals(2, sqls.size(), sqls.toString());
		assertTrue(sqls.get(0).endsWith("language plpgsql;"), sqls.get(0));

	}

	@Test
	@DisplayName("目印つきのドル引用符も読む")
	void splitKeepsTaggedDollarQuote () {

		List<String> sqls = MigrationSql.split(
			"do $body$ begin perform 1; end $body$; select 1;", POSTGRESQL);

		assertEquals(List.of("do $body$ begin perform 1; end $body$;", "select 1;"), sqls);

	}

	@Test
	@DisplayName("閉じていない文字列やコメントでも落ちない")
	void splitUnclosed () {

		assertEquals(List.of("select 'a"), MigrationSql.split("select 'a", POSTGRESQL));
		assertEquals(List.of("select $$a"), MigrationSql.split("select $$a", POSTGRESQL));
		assertTrue(MigrationSql.split("/* a", POSTGRESQL).isEmpty());

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
