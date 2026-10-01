package io.jimble.db.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MySQL の文字列リテラル（D-205）
 *
 * <p>
 * {@code jsonExtract} のパスは<b>何もエスケープせずに</b> SQL に入れていた。
 * {@code dateFormat} / {@code groupConcat} の区切り / コメントは {@code '} を重ねるだけで、
 * MySQL の既定で効くバックスラッシュで抜けられた（{@code \'} が {@code \''} になり、2つ目の {@code '} でリテラルが閉じる）。
 * </p>
 */
class MySqlLiteralTest {

	private static final Dialect MYSQL = MySqlDialect.INSTANCE;

	@Test
	@DisplayName("' と \\ を重ねる")
	void literal () {

		assertEquals("'abc'", MySqlDialect.literal("abc"));
		assertEquals("'it''s'", MySqlDialect.literal("it's"));
		assertEquals("'\\\\'''", MySqlDialect.literal("\\'"));
		assertEquals("'a\\\\b'", MySqlDialect.literal("a\\b"));

	}

	@Test
	@DisplayName("jsonExtract：パスで抜けられない")
	void jsonExtract () {

		StringBuilder sb = new StringBuilder();
		MYSQL.jsonExtract(sb, () -> sb.append("attrs"), "$.a') , (SELECT password FROM admin)) -- ", false);

		assertEquals("JSON_EXTRACT(attrs, '$.a'') , (SELECT password FROM admin)) -- ')", sb.toString());

	}

	@Test
	@DisplayName("dateFormat：バックスラッシュで抜けられない")
	void dateFormat () {

		StringBuilder sb = new StringBuilder();
		MYSQL.dateFormat(sb, () -> sb.append("created_at"), "\\' , (SELECT password FROM admin)) -- ");

		assertEquals("DATE_FORMAT(created_at, '\\\\'' , (SELECT password FROM admin)) -- ')", sb.toString());

	}

	@Test
	@DisplayName("groupConcat の区切り：バックスラッシュで抜けられない")
	void groupConcat () {

		StringBuilder sb = new StringBuilder();
		MYSQL.groupConcat(sb, () -> sb.append("name"), "\\'", false);

		assertEquals("GROUP_CONCAT(name SEPARATOR '\\\\''')", sb.toString());

	}

}
