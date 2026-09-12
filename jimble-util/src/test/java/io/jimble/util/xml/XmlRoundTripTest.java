package io.jimble.util.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XML の読み書き（要件 F-U-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code XmlData} には棚があったが、読む {@code XmlParser} と
 * 書く {@code XmlBuilder} は1度も動かしていなかった。</b>
 * </p>
 *
 * <p>
 * <b>XML の壊れ方も静かである。</b>{@code &} を書き逃せば読む側が落ちるが、
 * <b>落ちるのは相手のシステムの中</b>で、こちらのログには何も出ない。
 * </p>
 */
class XmlRoundTripTest {

	@Test
	@DisplayName("書いたものが、そのまま読める")
	void whatIsWrittenCanBeRead () throws Exception {

		XmlData root = new XmlData("order");

		root.addAttribute("id", "1");
		root.addChild(new XmlData("name", "りんご"));
		root.addChild(new XmlData("qty", "3"));

		String xml = new XmlBuilder().build(root);

		XmlData back = XmlParser.parse(xml);

		assertEquals("order", back.getTagName());
		assertEquals("1", back.getAttribute("id"));
		assertEquals("りんご", back.getChild("name").getString());
		assertEquals(3, back.getChild("qty").getInt());

	}

	@Test
	@DisplayName("記号は書くときに逃がし、読むときに戻す")
	void specialCharactersSurvive () throws Exception {

		/*
		 * <b>ここが抜けると、相手のパーサが落ちる。</b>
		 * しかも落ちるのは<b>相手のシステムの中</b>なので、
		 * こちらのログには何も残らない。
		 */
		String raw = "a & b < c > d \" e ' f";

		XmlData root = new XmlData("v", raw);

		root.addAttribute("attr", raw);

		String xml = new XmlBuilder().build(root);

		assertFalse(xml.contains("a & b"), "& を逃がしていません: " + xml);

		XmlData back = XmlParser.parse(xml);

		assertEquals(raw, back.getString());
		assertEquals(raw, back.getAttribute("attr"));

	}

	@Test
	@DisplayName("同じ名前の子が並んでいても、全部取れる")
	void repeatedChildrenAreAllKept () throws Exception {

		XmlData root = new XmlData("list");

		root.addChild(new XmlData("item", "1"));
		root.addChild(new XmlData("item", "2"));
		root.addChild(new XmlData("item", "3"));

		XmlData back = XmlParser.parse(new XmlBuilder().build(root));

		List<XmlData> items = back.getListChild("item");

		assertEquals(3, items.size(), "同じ名前の子を1つに潰しています");
		assertEquals("1", items.get(0).getString());
		assertEquals("3", items.get(2).getString());

		// 1つだけ欲しいときは先頭が返る
		assertEquals("1", back.getChild("item").getString());

	}

	@Test
	@DisplayName("入れ子は深さを保つ")
	void nestingIsKept () throws Exception {

		XmlData root = new XmlData("a");
		XmlData b = new XmlData("b");
		XmlData c = new XmlData("c", "おく");

		b.addChild(c);
		root.addChild(b);

		XmlData back = XmlParser.parse(new XmlBuilder().build(root));

		assertEquals("おく", back.getChild("b").getChild("c").getString());

	}

	@Test
	@DisplayName("日本語が化けない")
	void japaneseSurvives () throws Exception {

		XmlData root = new XmlData("v", "犬と猫と🐕");

		assertEquals("犬と猫と🐕", XmlParser.parse(new XmlBuilder().build(root)).getString());

	}

	@Test
	@DisplayName("無い子は null（Optional のほうは、その場で作って足す）")
	void missingChildrenDependOnHowYouAsk () throws Exception {

		XmlData root = XmlParser.parse(new XmlBuilder().build(new XmlData("root")));

		assertNull(root.getChild("いない"), "無い子に null 以外が返っています");

		/*
		 * <b>D-161 {@code getListChild} は空の一覧を返す。</b>
		 * 以前は {@code null} だったので、
		 * {@code for (XmlData x : xml.getListChild("items"))} と書くと
		 * <b>要素が0本のときだけ NullPointerException になった</b>——
		 * 試したデータに1本でも入っていれば通るので、<b>気づかないまま本番へ出る</b>。
		 */
		assertNotNull(root.getListChild("いない"), "null に戻っています");
		assertTrue(root.getListChild("いない").isEmpty());

		// 木に繋がっていない一覧なので、足そうとすると落ちる（黙って消えない）
		assertThrows(UnsupportedOperationException.class
			, () -> root.getListChild("いない").add(new XmlData("x")));

		// 読んだだけでは足されない
		assertFalse(root.getChildKeys().contains("いない"), "読んだだけで木が変わっています");

		/*
		 * <b>{@code ...Optional} は「あれば返す」ではなく「無ければ作って足す」である。</b>
		 * <b>読んだつもりで木が変わる</b>ので、そのあと書き出すと
		 * <b>元には無かった空の要素が付く</b>。
		 */
		assertNotNull(root.getChildOptional("いない"));
		assertTrue(root.getChildKeys().contains("いない"), "読んだだけでは足されていません");

		assertTrue(new XmlBuilder().build(root).contains("いない")
			, "読んだだけの子が書き出しに出てきません（挙動が変わりました）");

	}

	@Test
	@DisplayName("壊れた XML は null で戻る（例外は出ない）")
	void brokenXmlIsNull () {

		/*
		 * <b>落ちない代わりに、{@code null} が返る。</b>
		 * <b>受け取った側が見ないと、次の行で NullPointerException になる</b>——
		 * 外から来た XML を読むところでは<b>必ず null を見ること</b>。
		 */
		assertNull(XmlParser.parse("<a><b></a>"));
		assertNull(XmlParser.parse(""));

	}

	// region ここで固定していないこと

	/*
	 * - <b>出来上がる XML の見た目</b>（改行・字下げ・宣言の書き方）は固定していない。
	 *   見ているのは<b>読み直せること</b>だけである
	 * - <b>名前空間</b>（{@code xmlns}）は見ていない。{@code XmlData} は
	 *   接頭辞込みのタグ名として持つだけで、<b>解決はしていない</b>
	 * - <b>{@code ...Optional} が読んだだけで木を変えること</b>は<b>直していない</b>。
	 *   組み立てるときに便利な口で（{@code getChildOptional("a").setTextContent(...)}）、
	 *   {@code XmlBuilder} 自身も使っている。<b>名前が「あれば返す」に読める</b>のが問題なので、
	 *   1.0 の前に改名する候補として記録した（要件 D-161）
	 * - <b>DTD や外部実体</b>も見ていない。外から来た XML を読むところでは、
	 *   {@code XmlParser} の設定（外部実体の禁止）が効いていることを
	 *   <b>別途たしかめる必要がある</b>
	 */

	// endregion

}
