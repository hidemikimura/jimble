package io.jimble.web.spa;

import io.jimble.util.data.Data;
import io.jimble.web.assets.Resources;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPA の index.html の head に、title・meta・OGP・canonical を入れる
 */
class SpaHeadTest {

	/** Vite が出すような index.html */
	private static final String INDEX = """
		<!doctype html>
		<html lang="ja">
		<head>
		<meta charset="UTF-8">
		<title>Shop</title>
		<meta name="description" content="既定の説明">
		<meta property="og:title" content="Shop">
		<script type="module" crossorigin src="/assets/index-abc123.js"></script>
		</head>
		<body><div id="app"></div></body>
		</html>
		""";

	@BeforeEach
	void clearCache () {

		Resources.clearCache();

	}

	@Test
	@DisplayName("あるタグは差し替え、無いタグは </head> の前に足す。body と JS には触らない")
	void replaceOrInsert () {

		String html = SpaHead.of()
			.title("りんご")
			.description("青森のりんご")
			.og("title", "りんご")
			.og("image", "https://shop.example/a.jpg")
			.twitter("card", "summary_large_image")
			.canonical("https://shop.example/items/1")
			.apply(INDEX);

		assertTrue(html.contains("<title>りんご</title>") && !html.contains("<title>Shop</title>"), html);
		assertTrue(html.contains("<meta name=\"description\" content=\"青森のりんご\">") && !html.contains("既定の説明"), html);
		assertTrue(html.contains("<meta property=\"og:title\" content=\"りんご\">"), html);
		assertEquals(1, count(html, "og:title"), "og:title が 2 つある");

		// 無かったものは </head> の前
		int headEnd = html.indexOf("</head>");
		assertTrue(html.indexOf("og:image") < headEnd && html.indexOf("og:image") > 0, html);
		assertTrue(html.indexOf("twitter:card") < headEnd, html);
		assertTrue(html.indexOf("rel=\"canonical\"") < headEnd, html);

		// CSR に要るものはそのまま
		assertTrue(html.contains("<script type=\"module\" crossorigin src=\"/assets/index-abc123.js\"></script>"), html);
		assertTrue(html.endsWith("<body><div id=\"app\"></div></body>\n</html>\n"), html);

	}

	@Test
	@DisplayName("値はエスケープする（title を閉じて script を入れられない）。JSON-LD の </script> も閉じさせない")
	void escapes () {

		String html = SpaHead.of()
			.title("</title><script>alert(1)</script>")
			.og("description", "\"><script>x</script>")
			.jsonLd("{\"name\":\"</script><script>alert(1)</script>\"}")
			.apply(INDEX);

		assertFalse(html.contains("<script>alert(1)"), html);
		assertFalse(html.contains("<script>x"), html);
		assertTrue(html.contains("&lt;/title&gt;&lt;script&gt;"), html);
		assertTrue(html.contains("\\u003c/script>\\u003cscript>"), html);

	}

	@Test
	@DisplayName("JSON-LD は Data からも作れ、いつも足す")
	void jsonLdFromData () {

		Data product = new Data();
		product.put("@context", "https://schema.org");
		product.put("@type", "Product");
		product.put("name", "りんご");

		String html = SpaHead.of().jsonLd(product).jsonLd("{\"@type\":\"BreadcrumbList\"}").apply(INDEX);

		assertEquals(2, count(html, "application/ld+json"), html);
		assertTrue(html.contains("\"@type\":\"Product\""), html);

	}

	@Test
	@DisplayName("index.html の書き方（大文字・単一引用符・属性の順）が違っても、同じタグを見つける")
	void tolerantMatching () {

		String upper = "<HTML><HEAD><TITLE>A</TITLE><META CONTENT='x' NAME='Description'><LINK HREF='/a' REL='canonical'></HEAD><BODY></BODY></HTML>";

		String html = SpaHead.of().title("B").description("y").canonical("/b").apply(upper);

		assertEquals("<HTML><HEAD><title>B</title><meta name=\"description\" content=\"y\"><link rel=\"canonical\" href=\"/b\"></HEAD><BODY></BODY></HTML>", html);

	}

	@Test
	@DisplayName("同じものを 2 回足すと、あとの値。足しても元の SpaHead は変わらない")
	void latestWinsAndImmutable () {

		SpaHead base = SpaHead.of().title("一");
		SpaHead next = base.title("二");

		assertTrue(next.apply(INDEX).contains("<title>二</title>"));
		assertEquals(1, count(next.apply(INDEX), "<title>"));
		assertTrue(base.apply(INDEX).contains("<title>一</title>"), "元の SpaHead が変わった");

	}

	@Test
	@DisplayName("</head> が無い・何も足していないなら、そのまま返す")
	void unchanged () {

		assertEquals("<p>no head</p>", SpaHead.of().title("x").apply("<p>no head</p>"));
		assertEquals(INDEX, SpaHead.of().apply(INDEX));

	}

	@Test
	@DisplayName("SPA のパスに付けると、パスの値から作った head が入る。null を返せばそのまま")
	void throughSpaHandler () {

		JimbleApp app = new JimbleApp() {
			{
				// docs:begin spa-head
				install(() -> SpaHandler.mount("/", "test-spa-head", spa -> spa
					.route("/items/{id}", SpaHead.rewriter(context -> {
						String id = context.request().bodyPath().getString("id");
						if (id.equals("0")) {
							return null;    // 見つからない：index.html のまま
						}
						return SpaHead.of()
							.title("商品 " + id)
							.description("商品 " + id + " の説明")
							.canonical("https://shop.example/items/" + id)
							.og("type", "product")
							.og("image", "https://shop.example/items/" + id + ".jpg");
					}))
				));
				// docs:end
			}
		};

		Fakes.FakeResponseSink sink = request(app, "/items/42");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("<title>商品 42</title>"), sink.body());
		assertTrue(sink.body().contains("<meta property=\"og:image\" content=\"https://shop.example/items/42.jpg\">"), sink.body());
		assertEquals("private,no-cache", sink.headers().get("Cache-Control"));

		assertTrue(request(app, "/items/0").body().contains("<title>Shop</title>"));

	}

	private static Fakes.FakeResponseSink request (JimbleApp app, String path) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", path), sink)) {
			dispatcher.dispatch(context);
		}

		return sink;

	}

	private static int count (String text, String part) {

		return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;

	}

}
