package io.jimble.db.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL の字面の決まり（D-173。要件 F-D-30）
 *
 * <p>
 * <b>7 個が全部 boolean なので、入れ替わっても型では気づけない。</b>
 * record だったころは位置引数だったため、実際に
 * <b>Javadoc の並びが実物とズレたまま気づかれずにいた</b>
 * （{@code dashCommentNeedsSpace} と {@code backtickQuote}）。
 * </p>
 *
 * <p>
 * ここは「どちらの製品でどの旗が立つか」を1本ずつ書き出して固定する。
 * <b>{@code assertTrue} を7個並べる形にしないこと</b>——
 * 立っていない旗こそ、入れ替わりで黙って立つ側である。
 * </p>
 */
class SqlSyntaxTest {

	/** MySQL / MariaDB */
	@Test
	@DisplayName("MySQL は バックスラッシュ・# コメント・-- の空白・バックティック・実行コメント の5つ")
	void mysql () {

		SqlSyntax syntax = SqlSyntax.MYSQL;

		assertTrue(syntax.backslashEscape(), "MySQL は \\ がエスケープになる");
		assertTrue(syntax.hashComment(), "MySQL は # がコメントになる");
		assertTrue(syntax.dashCommentNeedsSpace(), "MySQL は -- のあとに空白が要る（1--2 はコメントではない）");
		assertTrue(syntax.backtickQuote(), "MySQL は ` で識別子を囲める");
		assertTrue(syntax.executableComment(), "MySQL は /*!... が実行される");

		assertFalse(syntax.dollarQuote(), "MySQL に $tag$ は無い");
		assertFalse(syntax.nestedBlockComment(), "MySQL のブロックコメントは入れ子にできない");

	}

	/** PostgreSQL */
	@Test
	@DisplayName("PostgreSQL は $tag$ と 入れ子ブロックコメント の2つだけ")
	void postgresql () {

		SqlSyntax syntax = SqlSyntax.POSTGRESQL;

		assertTrue(syntax.dollarQuote(), "PostgreSQL は $tag$ が文字列になる");
		assertTrue(syntax.nestedBlockComment(), "PostgreSQL はブロックコメントを入れ子にできる");

		assertFalse(syntax.backslashEscape(),
			"PostgreSQL は standard_conforming_strings=on が既定なので \\ はエスケープではない");
		assertFalse(syntax.hashComment(), "PostgreSQL に # コメントは無い");
		assertFalse(syntax.dashCommentNeedsSpace(), "PostgreSQL は -- のあとに空白が要らない");
		assertFalse(syntax.backtickQuote(),
			"PostgreSQL で ` を囲みとして読むと、書き間違い1つで後ろの SQL が全部くっつく");
		assertFalse(syntax.executableComment(), "PostgreSQL に実行されるコメントは無い");

	}

	/**
	 * 2つの製品で、立っている旗が1つも重ならないこと
	 *
	 * <p>
	 * <b>ここが重なったら、たいてい入れ替わりである。</b>
	 * MySQL 側の5つと PostgreSQL 側の2つは、いまのところ排他になっている。
	 * </p>
	 *
	 * <p>ここで固定していないこと：3つ目の製品を足したときに、この関係が続くかどうか。</p>
	 */
	@Test
	@DisplayName("MySQL と PostgreSQL で立つ旗は重ならない")
	void noOverlap () {

		SqlSyntax mysql = SqlSyntax.MYSQL;
		SqlSyntax postgres = SqlSyntax.POSTGRESQL;

		assertFalse(mysql.backslashEscape() && postgres.backslashEscape());
		assertFalse(mysql.hashComment() && postgres.hashComment());
		assertFalse(mysql.dashCommentNeedsSpace() && postgres.dashCommentNeedsSpace());
		assertFalse(mysql.backtickQuote() && postgres.backtickQuote());
		assertFalse(mysql.executableComment() && postgres.executableComment());
		assertFalse(mysql.dollarQuote() && postgres.dollarQuote());
		assertFalse(mysql.nestedBlockComment() && postgres.nestedBlockComment());

	}

	/** 方言が返すものが、その製品の決まりであること */
	@Test
	@DisplayName("方言が返す決まりが、製品と対応している")
	void dialectsReturnTheirOwn () {

		assertTrue(Dialects.of(MySqlDialect.NAME).sqlSyntax().backtickQuote(),
			"MySQL 方言が MySQL の決まりを返していない");
		assertTrue(Dialects.of(PostgreSqlDialect.NAME).sqlSyntax().dollarQuote(),
			"PostgreSQL 方言が PostgreSQL の決まりを返していない");

	}

}
