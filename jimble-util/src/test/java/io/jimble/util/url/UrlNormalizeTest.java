package io.jimble.util.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * URL を送れる形に整える（{@link UrlUtil#normalizeUrl(String)}）
 *
 * <p>
 * <b>いちばん大事なのはべき等であること。</b>
 * 未エンコードで渡されるかエンコード済みで渡されるかは、
 * 受け取る側では決められないことが多い。
 * 2回通すと壊れる関数は、<b>どこかで必ず2回通る</b>。
 * </p>
 */
class UrlNormalizeTest {

	/**
	 * 整えた結果を確かめ、あわせて<b>2回通しても変わらない</b>ことも確かめる
	 *
	 * @param input		入力
	 * @param expected	期待する結果
	 */
	private static void assertNormalized (String input, String expected) {

		String once = UrlUtil.normalizeUrl(input);

		assertEquals(expected, once, "1回目: " + input);
		assertEquals(once, UrlUtil.normalizeUrl(once), "2回通すと変わった: " + input);

	}

	@Test
	@DisplayName("未エンコードの URL をエンコードする")
	void encodesRawUrl () {

		assertNormalized("https://example.com/a b/c?q=x y"
			, "https://example.com/a%20b/c?q=x%20y");

		assertNormalized("https://example.com/a|b", "https://example.com/a%7Cb");

		assertNormalized("/relative/path with space", "/relative/path%20with%20space");

	}

	@Test
	@DisplayName("エンコード済みの URL はそのまま（二重エンコードしない）")
	void keepsEncodedUrl () {

		assertNormalized("https://example.com/a%20b/c?q=x%20y"
			, "https://example.com/a%20b/c?q=x%20y");

		// %2F はパス区切りではない。デコードしてはいけない
		assertNormalized("https://example.com/a%2Fb", "https://example.com/a%2Fb");

	}

	@Test
	@DisplayName("16進は大文字に揃える")
	void upperCaseHex () {

		assertNormalized("https://example.com/%e3%81%82", "https://example.com/%E3%81%82");

	}

	@Test
	@DisplayName("16進が続いていない % は %25 にする（それもべき等）")
	void lonepercent () {

		assertNormalized("https://example.com/100%", "https://example.com/100%25");
		assertNormalized("https://example.com/100%25", "https://example.com/100%25");

		// 末尾が中途半端でも壊れない
		assertNormalized("https://example.com/a%2", "https://example.com/a%252");
		assertNormalized("https://example.com/a%zz", "https://example.com/a%25zz");

	}

	@Test
	@DisplayName("ホストは punycode にする（パーセントエンコードしない）")
	void hostBecomesPunycode () {

		/*
		 * <b>ホスト名をパーセントエンコードすると DNS が引けない。</b>
		 * 移送元の fullUrlEncode は %E4%BE%8B%E3%81%88.com を作っていた。
		 */
		assertNormalized("https://例え.com/あ/い?q=あ#節"
			, "https://xn--r8jz45g.com/%E3%81%82/%E3%81%84?q=%E3%81%82#%E7%AF%80");

		// すでに punycode なら触らない
		assertNormalized("https://xn--r8jz45g.com/%E3%81%82"
			, "https://xn--r8jz45g.com/%E3%81%82");

	}

	@Test
	@DisplayName("スキームとホストは小文字に、パスの大小は変えない")
	void lowerCasesSchemeAndHost () {

		assertNormalized("HTTPS://EXAMPLE.com/AbC", "https://example.com/AbC");

	}

	@Test
	@DisplayName("区切りと port、userinfo、IPv6 を壊さない")
	void keepsStructure () {

		assertNormalized("http://example.com:8080/a?b=1&c=2#d"
			, "http://example.com:8080/a?b=1&c=2#d");

		assertNormalized("https://user:pw@example.com/a b"
			, "https://user:pw@example.com/a%20b");

		assertNormalized("https://[::1]:8080/a b", "https://[::1]:8080/a%20b");

		assertNormalized("https://example.com", "https://example.com");
		assertNormalized("https://example.com?q=a b", "https://example.com?q=a%20b");

	}

	@Test
	@DisplayName("+ は + のまま（パスでは空白にしない）")
	void plusStaysPlus () {

		/*
		 * URLEncoder は空白を + にするが、<b>それはフォームの書式であってパスではない</b>。
		 * パスの + は「プラス記号」なので、空白に読み替えてはいけない。
		 * 移送元の urlToEncodeUrl は空白を + にしていた（/a b → /a+b）。
		 */
		assertNormalized("https://example.com/a+b?q=1+2", "https://example.com/a+b?q=1+2");

		assertNormalized("https://example.com/a b", "https://example.com/a%20b");

	}

	@Test
	@DisplayName("サロゲートペア（絵文字）も1文字として扱う")
	void surrogatePair () {

		assertNormalized("https://example.com/🍣", "https://example.com/%F0%9F%8D%A3");

	}

	@Test
	@DisplayName("スキームだけのもの・相対パス・null・空も落ちない")
	void doesNotThrow () {

		assertNormalized("mailto:a@example.com", "mailto:a@example.com");
		assertNormalized("", "");
		assertNormalized(null, null);

		for (String odd : List.of("://", "?", "#", "//", "a", "%")) {
			// 何を返すかは決めない。落ちないことと、2回通して変わらないことだけ見る
			String once = UrlUtil.normalizeUrl(odd);
			assertEquals(once, UrlUtil.normalizeUrl(once), "2回通すと変わった: " + odd);
		}

	}

}
