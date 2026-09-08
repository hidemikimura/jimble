package io.jimble.web.spa;

import io.jimble.web.assets.Resources;
import io.jimble.web.context.WebContext;
import io.jimble.web.mpa.MpaHandler;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
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
 * SPA / MPA 配信のテスト（要件 F-W-17 / F-R-14 / F-W-20）
 */
class SpaMpaTest {

	@BeforeEach
	void clearCache () {

		Resources.clearCache();

	}

	/**
	 * ディスパッチする
	 *
	 * @param app	アプリケーション
	 * @param path	パス
	 * @return	レスポンス
	 */
	private Fakes.FakeResponseSink request (JimbleApp app, String path) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", path), sink)) {
			dispatcher.dispatch(context);
		}

		return sink;

	}

	// region SPA

	/**
	 * SPA を組み込んだアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp spaApp () {

		return new JimbleApp() {
			{
				// 通常のルートと共存できること（要件 F-W-17）
				get("/api/items", context -> context.response().send("api"));
				// docs:begin spa-mount
				install(() -> SpaHandler.mount("/app", "test-spa"));
				// docs:end
			}
		};

	}

	@Test
	@DisplayName("SPA をルート（/）に置いたとき、/ 自身も index.html を返す")
	void spaAtRootServesRoot () {

		/*
		 * "/*" は "/" に当たらない（セグメントが0個）。
		 * 足していないと<b>トップページだけ 404</b> になり、
		 * /any は 200 なので気づきにくい。
		 */
		JimbleApp app = new JimbleApp() {
			{
				install(() -> SpaHandler.mount("/", "test-spa"));
			}
		};

		Fakes.FakeResponseSink root = request(app, "/");

		assertEquals(200, root.status());

		assertEquals(200, request(app, "/anything").status());

	}

	@Test
	@DisplayName("実ファイルがあればそれを返す")
	void spaServesFile () {

		Fakes.FakeResponseSink sink = request(spaApp(), "/app/app.js");

		assertEquals(200, sink.status());
		assertEquals("spa-asset", sink.body());

	}

	@Test
	@DisplayName("ファイルが無ければ index.html を返す")
	void spaFallsBackToIndex () {

		Fakes.FakeResponseSink sink = request(spaApp(), "/app/items/42");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("SPA"), sink.body());

	}

	@Test
	@DisplayName("入れ子の index.html があればそちらを使う")
	void spaUsesNestedIndex () {

		Fakes.FakeResponseSink sink = request(spaApp(), "/app/nested/anything");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("NESTED"), sink.body());

	}

	@Test
	@DisplayName("ルート直下も index.html を返す")
	void spaRoot () {

		assertTrue(request(spaApp(), "/app").body().contains("SPA"));

	}

	@Test
	@DisplayName("通常のルートは SPA に食われない")
	void spaCoexistsWithRoutes () {

		assertEquals("api", request(spaApp(), "/api/items").body());

	}

	@Test
	@DisplayName("「..」は 404 で止める")
	void spaRejectsTraversal () {

		// 移送元はログを出すだけで、そのまま配信に進んでいた
		Fakes.FakeResponseSink sink = request(spaApp(), "/app/../secret.txt");

		assertEquals(404, sink.status());
		assertFalse("secret".equals(sink.body()));

	}

	// endregion

	// region SPA の index.html 書き換え

	@Test
	@DisplayName("パスにマッチしたら index.html を書き換える")
	void spaRewrite () {

		JimbleApp app = new JimbleApp() {
			{
				// docs:begin spa-rewrite
				install(() -> SpaHandler.mount("/app", "test-spa", spa -> spa
					.route("/app/items/{id}", (context, html) ->
						html.replace("<!--title-->", "<title>item " + context.request().bodyPath().getString("id") + "</title>"))
				));
				// docs:end
			}
		};

		Fakes.FakeResponseSink sink = request(app, "/app/items/42");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("<title>item 42</title>"), sink.body());

	}

	@Test
	@DisplayName("マッチしなければそのまま index.html を返す")
	void spaRewriteNoMatch () {

		JimbleApp app = new JimbleApp() {
			{
				install(() -> SpaHandler.mount("/app", "test-spa", spa -> spa
					.route("/app/items/{id}", (context, html) -> html.replace("<!--title-->", "X"))
				));
			}
		};

		Fakes.FakeResponseSink sink = request(app, "/app/other");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("<!--title-->"), sink.body());

	}

	/**
	 * SPA のマッチだけを見る
	 *
	 * @param spa	SPA ルーターの組み立て
	 * @param path	生のパス
	 * @return	当たったルートのパス（当たらなければ null）
	 */
	private String spaMatch (java.util.function.Consumer<SpaRouter> spa, String path) {

		SpaRouter router = new SpaRouter();
		spa.accept(router);

		try (WebContext context = Fakes.context("GET", path)) {
			SpaRoute route = router.match(context, path);
			return route == null ? null : route.path();
		}

	}

	@Test
	@DisplayName("D-75 パスの「.」はメタ文字にならない")
	void spaRouteQuotesLiterals () {

		// 移送元はパスをそのまま正規表現に埋めていたので /axb/1 にもマッチした
		java.util.function.Consumer<SpaRouter> spa =
			router -> router.route("/a.b/{id}", (context, html) -> html);

		assertEquals("/a.b/{id}", spaMatch(spa, "/a.b/1"));
		assertNull(spaMatch(spa, "/axb/1"));

	}

	@Test
	@DisplayName("D-75 パスパラメータが取れる")
	void spaRouteVariables () {

		SpaRouter router = new SpaRouter();
		router.route("/items/{id}/tabs/{tab}", (context, html) -> html);

		try (WebContext context = Fakes.context("GET", "/items/42/tabs/detail")) {

			assertNotNull(router.match(context, "/items/42/tabs/detail"));
			assertEquals("42", context.request().bodyPath().getString("id"));
			assertEquals("detail", context.request().bodyPath().getString("tab"));

		}

		try (WebContext context = Fakes.context("GET", "/x")) {
			assertNull(router.match(context, "/items/42/tabs"), "足りないパスがマッチしている");
			assertNull(router.match(context, "/items/42/tabs/a/b"), "余分なパスがマッチしている");
		}

	}

	@Test
	@DisplayName("D-75 固定セグメントがパスパラメータより優先される（書いた順によらない）")
	void spaRouteStaticWinsOverVariable () {

		// 変数のほうを先に書いても、具体的なほうが勝つ
		java.util.function.Consumer<SpaRouter> spa = router -> router
			.route("/app/items/{id}", (context, html) -> html)
			.route("/app/items/new", (context, html) -> html);

		assertEquals("/app/items/new", spaMatch(spa, "/app/items/new"));
		assertEquals("/app/items/{id}", spaMatch(spa, "/app/items/42"));

	}

	@Test
	@DisplayName("D-75 ワイルドカードが書ける")
	void spaRouteWildcard () {

		java.util.function.Consumer<SpaRouter> spa =
			router -> router.route("/app/docs/*", (context, html) -> html);

		assertEquals("/app/docs/*", spaMatch(spa, "/app/docs/a/b/c"));
		assertNull(spaMatch(spa, "/app/other"));

	}

	@Test
	@DisplayName("D-75 %2F を含む値がセグメントの区切りにならない")
	void spaRoutePercentEncoded () {

		SpaRouter router = new SpaRouter();
		router.route("/app/items/{id}", (context, html) -> html);

		try (WebContext context = Fakes.context("GET", "/app/items/a%2Fb")) {

			assertNotNull(router.match(context, "/app/items/a%2Fb"));
			assertEquals("a/b", context.request().bodyPath().getString("id"));

		}

	}

	@Test
	@DisplayName("D-75 同じパスを2回書いたら落ちる（黙って先勝ちにしない）")
	void spaRouteDuplicate () {

		SpaRouter router = new SpaRouter();
		router.route("/app/items/{id}", (context, html) -> html);

		assertThrows(IllegalStateException.class,
			() -> router.route("/app/items/{id}", (context, html) -> html));

		/*
		 * 変数名だけ違うもの（/app/items/{other}）は落ちない。
		 * ツリーが変数名でノードを分けるためで、本体のルーターと同じ挙動である
		 * （到達できないルートの検出は残件 D-10）。
		 * <b>ここだけ別の挙動にしないことのほうが大事。</b>
		 */

	}

	// endregion

	// region MPA

	/**
	 * MPA を組み込んだアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp mpaApp () {

		return new JimbleApp() {
			{
				// docs:begin mpa-mount
				install(() -> MpaHandler.mount("/docs", "test-mpa"));
				// docs:end
			}
		};

	}

	@Test
	@DisplayName("拡張子が無いパスは index.html を返す")
	void mpaIndex () {

		Fakes.FakeResponseSink sink = request(mpaApp(), "/docs/guide");

		assertEquals(200, sink.status());
		assertEquals("<!doctype html>MPA-GUIDE", sink.body());

	}

	@Test
	@DisplayName("拡張子があるパスはファイルを返す")
	void mpaFile () {

		Fakes.FakeResponseSink sink = request(mpaApp(), "/docs/guide/a.png");

		assertEquals(200, sink.status());
		assertEquals("image/png", sink.headers().get("Content-Type"));

	}

	@Test
	@DisplayName("トップは index.html")
	void mpaTop () {

		assertEquals("<!doctype html>MPA-TOP", request(mpaApp(), "/docs").body());

	}

	@Test
	@DisplayName("無いページは 404")
	void mpaNotFound () {

		assertEquals(404, request(mpaApp(), "/docs/nothing").status());

	}

	@Test
	@DisplayName("「..」は 404 で止める")
	void mpaRejectsTraversal () {

		// 移送元は MPA 側にトラバーサルの確認がまったく無かった
		for (String path : List.of("/docs/../secret.txt", "/docs/guide/../../secret.txt")) {
			Fakes.FakeResponseSink sink = request(mpaApp(), path);
			assertEquals(404, sink.status(), path);
			assertFalse("secret".equals(sink.body()), path);
		}

	}

	@Test
	@DisplayName("解決は拡張子で決める（OS 設定に依存しない）")
	void mpaResolveByExtension () {

		MpaHandler handler = new MpaHandler("site");

		assertEquals("/site/index.html", handler.resolve(""));
		assertEquals("/site/guide/index.html", handler.resolve("guide"));
		assertEquals("/site/guide/index.html", handler.resolve("guide/"));
		assertEquals("/site/guide/a.png", handler.resolve("guide/a.png"));
		assertEquals("/site/a.b/index.html", handler.resolve("a.b"), "知らない拡張子はディレクトリ扱い");
		assertNull(handler.resolve("../secret.txt"));

	}

	// endregion

	// region 存在確認

	@Test
	@DisplayName("ディレクトリはファイルとして扱わない")
	void resourcesDirectoryIsNotFile () {

		assertTrue(Resources.isFile("/test-spa/index.html"));
		assertFalse(Resources.isFile("/test-spa/nested"), "ディレクトリをファイルと判定している");
		assertFalse(Resources.isFile("/test-spa/nothing"));

	}

	@Test
	@DisplayName("存在確認のキャッシュには上限がある")
	void resourcesCacheIsBounded () {

		for (int i = 0; i < Resources.MAX_CACHE_SIZE + 100; i++) {
			Resources.isFile("/nothing-" + i);
		}

		assertTrue(Resources.cacheSize() <= Resources.MAX_CACHE_SIZE,
			"キャッシュが増え続けている: " + Resources.cacheSize());

	}

	// endregion

}
