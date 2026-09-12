package io.jimble.util.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * URL の組み立て（要件 F-U-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>ドキュメントに名前が載っているのに、テストが1度も触っていなかった。</b>
 * <b>組み立てた URL が壊れても、こちら側では何も起きない</b>——
 * 相手のサーバが「そんなパラメータは知らない」と答えるだけである。
 * </p>
 */
class UrlBuilderTest {

	@Test
	@DisplayName("パラメータを足すと ? と & でつながる")
	void parametersAreJoined () {

		assertEquals("https://example.com/a?x=1&y=2"
			, new UrlBuilder().url("https://example.com/a")
				.addParameter("x", "1")
				.addParameter("y", "2")
				.build());

	}

	@Test
	@DisplayName("パラメータが無ければ、URL はそのまま")
	void withoutParametersTheUrlIsUntouched () {

		assertEquals("https://example.com/a"
			, new UrlBuilder().url("https://example.com/a").build());

	}

	@Test
	@DisplayName("D-161 すでに ? が付いている URL には & で足す")
	void anExistingQueryIsContinued () {

		/*
		 * <b>以前は無条件に {@code ?} を足していた。</b>
		 * 出来上がるのは {@code ...?b=1?c=2} で、<b>2本目から先が
		 * 「b の値の一部」として読まれる</b>——例外は出ない。
		 */
		assertEquals("https://example.com/a?b=1&c=2"
			, new UrlBuilder().url("https://example.com/a?b=1")
				.addParameter("c", "2")
				.build());

		// 末尾が ? だけのときも、? を重ねない
		assertEquals("https://example.com/a?&c=2"
			, new UrlBuilder().url("https://example.com/a?")
				.addParameter("c", "2")
				.build());

	}

	@Test
	@DisplayName("名前も値もエンコードする")
	void namesAndValuesAreEncoded () {

		/*
		 * <b>ここが抜けると、値に {@code &} を入れられた瞬間にパラメータが増える。</b>
		 */
		assertEquals("https://example.com/?q=a%26b%3Dc"
			, new UrlBuilder().url("https://example.com/")
				.addParameter("q", "a&b=c")
				.build());

		// 空白は + （クエリ文字列の決まり）
		assertEquals("https://example.com/?q=%E7%8A%AC+%E3%81%A8+%E7%8C%AB"
			, new UrlBuilder().url("https://example.com/")
				.addParameter("q", "犬 と 猫")
				.build());

	}

	@Test
	@DisplayName("文字コードを変えられる")
	void theCharsetCanBeChanged () {

		Charset sjis = Charset.forName("SHIFT-JIS");

		assertEquals("https://example.com/?q=%8C%A2"
			, new UrlBuilder().url("https://example.com/")
				.addParameter("q", "犬")
				.build(sjis));

		assertEquals("https://example.com/?q=%E7%8A%AC"
			, new UrlBuilder().url("https://example.com/")
				.addParameter("q", "犬")
				.build(StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("同じ名前を何度でも足せる")
	void theSameNameCanRepeat () {

		assertEquals("https://example.com/?id=1&id=2"
			, new UrlBuilder().url("https://example.com/")
				.addParameter("id", "1")
				.addParameter("id", "2")
				.build());

	}

	@Test
	@DisplayName("D-161 値が null でも落ちない")
	void nullValueBecomesEmpty () {

		/*
		 * <b>以前は {@code URLEncoder.encode(null, ...)} で NullPointerException だった。</b>
		 * 値の無いパラメータは「空文字を送る」で足りる。
		 */
		assertEquals("https://example.com/?q="
			, new UrlBuilder().url("https://example.com/")
				.addParameter("q", null)
				.build());

	}

	// region ここで固定していないこと

	/*
	 * - <b>{@code url()} を呼ばずに組み立てたときの形</b>は固定していない
	 *   （{@code "null?x=1"} になる）。<b>使い方の間違い</b>であって、
	 *   ここで形を決めると直せなくなる
	 * - <b>フラグメント（{@code #...}）の位置</b>も見ていない。
	 *   {@code https://example.com/#top} に足すと
	 *   <b>{@code #top?x=1} になって効かない</b>——直すには土台の分解が要るので、
	 *   いまは「フラグメントは最後に自分で付ける」ことにしている
	 */

	// endregion

}
