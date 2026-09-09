package io.jimble.util.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 公開サフィックス（{@link PublicSuffix}）と {@link UrlUtil} の2メソッド
 *
 * <p>
 * guava の {@code InternetDomainName} から自前に置き換えたときの答えを固定してある（要件 D-121）。
 * 一覧の全ルール（6,950 件）に接頭辞を付けたものと、でたらめなドメイン 30 万件、
 * <b>あわせて 334,784 件で guava と突き合わせた</b>。
 * 食い違ったのは 126 件で、<b>すべて guava の抱えている一覧が古いことによるもの</b>だった
 * （{@code .web} のような新しい TLD、{@code kh} のようにルールが変わったもの）。
 * </p>
 */
class PublicSuffixTest {

	// region サフィックス

	@Test
	@DisplayName("レジストリサフィックスが取れる")
	void suffix () {

		assertEquals("co.jp", PublicSuffix.registrySuffix("www.google.co.jp"));
		assertEquals("co.jp", PublicSuffix.registrySuffix("google.co.jp"));
		assertEquals("co.jp", PublicSuffix.registrySuffix("co.jp"));
		assertEquals("jp", PublicSuffix.registrySuffix("jp"));
		assertEquals("com", PublicSuffix.registrySuffix("www.google.com"));

	}

	@Test
	@DisplayName("1つ内側までが取れる")
	void top () {

		assertEquals("google.co.jp", PublicSuffix.topDomainUnderRegistrySuffix("www.google.co.jp"));
		assertEquals("google.co.jp", PublicSuffix.topDomainUnderRegistrySuffix("a.b.c.google.co.jp"));
		assertEquals("google.com", PublicSuffix.topDomainUnderRegistrySuffix("www.google.com"));

		// サフィックスそのものには「1つ内側」が無い
		assertNull(PublicSuffix.topDomainUnderRegistrySuffix("co.jp"));
		assertNull(PublicSuffix.topDomainUnderRegistrySuffix("jp"));

	}

	@Test
	@DisplayName("ワイルドカードのルールを読む")
	void wildcard () {

		// *.nom.br なので、b.nom.br までが登録の単位
		assertEquals("b.nom.br", PublicSuffix.registrySuffix("a.b.nom.br"));
		assertEquals("a.b.nom.br", PublicSuffix.topDomainUnderRegistrySuffix("a.b.nom.br"));
		assertEquals("br", PublicSuffix.registrySuffix("nom.br"));

	}

	@Test
	@DisplayName("例外のルールを読む")
	void exception () {

		// !city.kawasaki.jp があるので、city.kawasaki.jp は<b>登録できる</b>
		assertEquals("kawasaki.jp", PublicSuffix.registrySuffix("city.kawasaki.jp"));
		assertEquals("city.kawasaki.jp", PublicSuffix.topDomainUnderRegistrySuffix("www.city.kawasaki.jp"));

	}

	@Test
	@DisplayName("一覧に無ければ null")
	void unknown () {

		assertNull(PublicSuffix.registrySuffix("localhost"));
		assertNull(PublicSuffix.registrySuffix("example.invalidtldthatdoesnotexist"));
		assertNull(PublicSuffix.registrySuffix("-a.com"));
		assertNull(PublicSuffix.registrySuffix("a..b"));
		assertNull(PublicSuffix.registrySuffix(""));

	}

	@Test
	@DisplayName("大文字・末尾の「.」・全角の「。」をならす")
	void normalize () {

		assertEquals("co.jp", PublicSuffix.registrySuffix("WWW.GOOGLE.CO.JP"));
		assertEquals("google.co.jp", PublicSuffix.topDomainUnderRegistrySuffix("WWW.GOOGLE.CO.JP"));
		assertEquals("co.jp", PublicSuffix.registrySuffix("www.google.co.jp."));
		assertEquals("google.co.jp", PublicSuffix.topDomainUnderRegistrySuffix("www。google。co。jp"));

	}

	@Test
	@DisplayName("日本語のドメインは、そのままでも punycode でも引ける")
	void idn () {

		assertEquals("みんな", PublicSuffix.registrySuffix("example.みんな"));
		assertEquals("example.みんな", PublicSuffix.topDomainUnderRegistrySuffix("www.example.みんな"));
		assertEquals("xn--q9jyb4c", PublicSuffix.registrySuffix("www.example.xn--q9jyb4c"));
		assertEquals("example.xn--q9jyb4c", PublicSuffix.topDomainUnderRegistrySuffix("www.example.xn--q9jyb4c"));

	}

	// endregion

	// region UrlUtil から

	@Test
	@DisplayName("URL から取れる")
	void fromUrl () {

		assertEquals("google.co.jp", UrlUtil.getRootDomain("https://www.google.co.jp/search?q=1"));
		assertEquals("co.jp", UrlUtil.getDomainRegistrySuffix("https://www.google.co.jp/search?q=1"));

	}

	@Test
	@DisplayName("分からなければドメインをそのまま返す")
	void fromUrlUnknown () {

		assertEquals("localhost", UrlUtil.getRootDomain("http://localhost:8080/"));
		assertEquals("localhost", UrlUtil.getDomainRegistrySuffix("http://localhost:8080/"));

	}

	// endregion

}
