package io.jimble.util.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * XML 1.0 のエスケープ（{@link XmlEscape}）
 *
 * <p>
 * commons-text の {@code StringEscapeUtils.escapeXml10} から自前に置き換えたときの答えを
 * 固定してある（要件 D-121）。BMP の全 65,536 文字で突き合わせて、
 * <b>1 文字も違わない</b>ことを確かめたうえで書いた。
 * </p>
 */
class XmlEscapeTest {

	@Test
	@DisplayName("XML の特別な5文字を実体参照にする")
	void entities () {

		assertEquals("&lt;a&gt;", XmlEscape.escape("<a>"));
		assertEquals("a &amp; b", XmlEscape.escape("a & b"));
		assertEquals("&quot;x&quot;", XmlEscape.escape("\"x\""));
		assertEquals("it&apos;s", XmlEscape.escape("it's"));
		assertEquals("&lt;&gt;&amp;&quot;&apos;", XmlEscape.escape("<>&\"'"));

	}

	@Test
	@DisplayName("XML 1.0 に書けない文字は捨てる")
	void drops () {

		// 見えない文字なので番号で書く
		assertEquals("ab", XmlEscape.escape("a\u0000b"));
		assertEquals("ab", XmlEscape.escape("a\u0008b"));
		assertEquals("ab", XmlEscape.escape("a\u000Bb"));
		assertEquals("ab", XmlEscape.escape("a\u000Cb"));
		assertEquals("ab", XmlEscape.escape("a\u001Fb"));
		assertEquals("ab", XmlEscape.escape("a\uFFFE\uFFFFb"));

		// タブ・改行・復帰は書けるので残す
		assertEquals("a\t\n\rb", XmlEscape.escape("a\t\n\rb"));

	}

	@Test
	@DisplayName("書けるが読みにくい制御文字は数値参照にする")
	void numeric () {

		assertEquals("&#127;", XmlEscape.escape("\u007F"));
		assertEquals("&#128;", XmlEscape.escape("\u0080"));
		assertEquals("&#132;", XmlEscape.escape("\u0084"));
		assertEquals("&#134;", XmlEscape.escape("\u0086"));
		assertEquals("&#159;", XmlEscape.escape("\u009F"));

		// U+0085 は改行を表す文字なので、そのまま残す
		assertEquals("\u0085", XmlEscape.escape("\u0085"));

	}

	@Test
	@DisplayName("直すところが無ければ、同じ文字列をそのまま返す")
	void untouched () {

		String value = "ふつうの文字列 abc 123 😀";

		assertSame(value, XmlEscape.escape(value));

	}

	@Test
	@DisplayName("null と空文字はそのまま")
	void empty () {

		assertEquals(null, XmlEscape.escape(null));
		assertEquals("", XmlEscape.escape(""));

	}

}
