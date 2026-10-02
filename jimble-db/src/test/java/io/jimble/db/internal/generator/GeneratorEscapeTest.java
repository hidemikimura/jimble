package io.jimble.db.internal.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DB のコメントを、生成するソースに書くときのエスケープ（D-252）
 */
class GeneratorEscapeTest {

	@TempDir
	static java.nio.file.Path out;

	/* コメントが閉じると、続きがコードになる（ここではわざとコンパイルできないコード） */
	private static final List<String> HOSTILE = List.of(
		"*/ int broken = ; /*",
		"\\u002a/ int broken = ; /*",
		"a\\u000a int broken = ;",
		"\" + broken + \"\\",
		"改行\nと\r\nを含む");

	@Test
	@DisplayName("D-252 コメントの中に書いたものは、コメントのまま閉じない（/* */ と Javadoc）")
	void javaCommentStaysComment () {

		for (String comment : HOSTILE) {
			String escaped = Generator.javaComment(comment);
			assertCompiles("class C1 { /* %s */ }".formatted(escaped), comment);
			assertCompiles("/**\n * %s\n */\nclass C2 { }".formatted(escaped), comment);
		}

	}

	@Test
	@DisplayName("D-252 文字列リテラルの中に書いたものは、元の文字列に戻る")
	void escapeCommentStaysString () {

		for (String comment : HOSTILE) {
			assertCompiles("class C3 { String s = \"%s\"; }".formatted(Generator.escapeComment(comment)), comment);
		}

	}

	@Test
	@DisplayName("D-252 MySQL のコメントの ' と \\ を重ねる")
	void sqlLiteral () {

		assertEquals("it''s \\\\", Generator.sqlLiteral("it's \\"));

	}

	@Test
	@DisplayName("ふつうのコメントは変えない（codegenCheck の差分を出さない）")
	void plainUnchanged () {

		assertEquals("顧客 & 注文 <テーブル>", Generator.javaComment("顧客 & 注文 <テーブル>"));
		assertEquals("顧客 & 注文", Generator.escapeComment("顧客 & 注文"));

	}

	private static void assertCompiles (String source, String comment) {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		SimpleJavaFileObject file = new SimpleJavaFileObject(URI.create("string:///C.java"), SimpleJavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent (boolean ignoreEncodingErrors) {
				return source;
			}
		};

		java.io.StringWriter messages = new java.io.StringWriter();
		boolean ok = compiler.getTask(messages, null, null, List.of("-proc:none", "-d", out.toString()), null, List.of(file)).call();

		assertTrue(ok, "コンパイルできない: " + comment + "\n" + source + "\n" + messages);

	}

}
