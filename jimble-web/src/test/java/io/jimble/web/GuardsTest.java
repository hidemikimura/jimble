package io.jimble.web;

import io.jimble.web.auth.BasicAuth;
import io.jimble.web.bot.BotBlocker;
import io.jimble.web.context.WebContext;
import io.jimble.web.cors.Cors;
import io.jimble.web.cors.CorsHandler;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS / Basic 認証 / Bot ブロックのテスト（M4 ステップ4）
 *
 * <p>どれも {@code before} に挿す前提なので、{@link Dispatcher} を通して見る。</p>
 */
class GuardsTest {

	/**
	 * ディスパッチする
	 *
	 * @param app		アプリケーション
	 * @param source	リクエスト
	 * @return	レスポンス
	 */
	private Fakes.FakeResponseSink dispatch (JimbleApp app, Fakes.FakeRequestSource source) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
		}

		return sink;

	}

	// region CORS

	/**
	 * CORS を掛けたアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp corsApp () {

		Cors cors = new Cors()
			.addAllowOrigin("https://example.com")
			.addAllowedMethod("GET")
			.addAllowedMethod("POST")
			.addExposeHeader("X-Total-Count");

		return new JimbleApp() {
			{
				before(new CorsHandler(cors));
				get("/items", context -> context.response().send("ok"));
				options("/items", context -> context.response().send());
			}
		};

	}

	@Test
	@DisplayName("許可された origin にはヘッダが付く")
	void corsAllowed () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/items");
		source.header("origin", "https://example.com");

		Fakes.FakeResponseSink sink = dispatch(corsApp(), source);

		assertEquals(200, sink.status());
		assertEquals("https://example.com", sink.headers().get("Access-Control-Allow-Origin"));
		// 既定の GET / POST / OPTIONS に足しても二重にならない
		assertEquals("GET, POST, OPTIONS", sink.headers().get("Access-Control-Allow-Methods"));
		assertEquals("X-Total-Count", sink.headers().get("Access-Control-Expose-Headers"));
		assertEquals("Origin", sink.headers().get("Vary"), "Vary が無いとキャッシュが混ざる");

	}

	@Test
	@DisplayName("許可されない origin は 403 で止まる")
	void corsRejected () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/items");
		source.header("origin", "https://evil.example");

		Fakes.FakeResponseSink sink = dispatch(corsApp(), source);

		// 移送元はフラグを立てるだけで本処理に進んでいた
		assertEquals(CorsHandler.FORBIDDEN_STATUS_CODE, sink.status());
		assertNull(sink.headers().get("Access-Control-Allow-Origin"));

	}

	@Test
	@DisplayName("origin が無ければ CORS は効かない")
	void corsSkippedForSameOrigin () {

		Fakes.FakeResponseSink sink = dispatch(corsApp(), new Fakes.FakeRequestSource("GET", "/items"));

		assertEquals(200, sink.status());
		assertNull(sink.headers().get("Access-Control-Allow-Origin"));

	}

	@Test
	@DisplayName("プリフライトはここで返し切る")
	void corsPreflight () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("OPTIONS", "/items");
		source.header("origin", "https://example.com");
		source.header("access-control-request-method", "POST");

		Fakes.FakeResponseSink sink = dispatch(corsApp(), source);

		assertEquals(CorsHandler.PREFLIGHT_STATUS_CODE, sink.status());
		assertEquals("https://example.com", sink.headers().get("Access-Control-Allow-Origin"));

	}

	@Test
	@DisplayName("許可されないメソッドのプリフライトは 403")
	void corsPreflightRejectsMethod () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("OPTIONS", "/items");
		source.header("origin", "https://example.com");
		source.header("access-control-request-method", "DELETE");

		assertEquals(CorsHandler.FORBIDDEN_STATUS_CODE, dispatch(corsApp(), source).status());

	}

	@Test
	@DisplayName("あとから足した origin も効く")
	void corsMatcherIsNotStale () {

		Cors cors = new Cors().addAllowOrigin("https://a.example");

		assertTrue(cors.matchOrigin("https://a.example"));

		// 移送元は最初のマッチャを使い回すので、これが効かなかった
		cors.addAllowOrigin("https://b.example");

		assertTrue(cors.matchOrigin("https://b.example"));

	}

	// endregion

	// region Basic 認証

	/**
	 * Basic 認証を掛けたアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp basicApp () {

		return new JimbleApp() {
			{
				path("/admin", () -> {
					before(BasicAuth.of("admin", "secret"));
					get("/", context -> context.response().send("秘密"));
				});
				get("/public", context -> context.response().send("公開"));
			}
		};

	}

	/**
	 * Authorization ヘッダの値
	 *
	 * @param username	ユーザー名
	 * @param password	パスワード
	 * @return	ヘッダ値
	 */
	private String basic (String username, String password) {

		return BasicAuth.PREFIX + Base64.getEncoder()
			.encodeToString("%s:%s".formatted(username, password).getBytes(StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("正しい資格情報なら通る")
	void basicAccepts () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/admin/");
		source.header("authorization", basic("admin", "secret"));

		Fakes.FakeResponseSink sink = dispatch(basicApp(), source);

		assertEquals(200, sink.status());
		assertEquals("秘密", sink.body());

	}

	@Test
	@DisplayName("資格情報が無ければ 401 と WWW-Authenticate")
	void basicChallenges () {

		Fakes.FakeResponseSink sink = dispatch(basicApp(), new Fakes.FakeRequestSource("GET", "/admin/"));

		assertEquals(BasicAuth.STATUS_CODE, sink.status());
		assertTrue(sink.headers().get("WWW-Authenticate").startsWith("Basic realm="),
			String.valueOf(sink.headers().get("WWW-Authenticate")));

	}

	@Test
	@DisplayName("パスワードが違えば 401")
	void basicRejectsWrongPassword () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/admin/");
		source.header("authorization", basic("admin", "wrong"));

		assertEquals(BasicAuth.STATUS_CODE, dispatch(basicApp(), source).status());

	}

	@Test
	@DisplayName("Basic 以外のヘッダでも落ちない")
	void basicIgnoresOtherScheme () {

		// 移送元は substring(6) だけで、短いヘッダだと 500 になっていた
		for (String header : List.of("Bearer xyz", "x", "", "Basic")) {

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/admin/");
			source.header("authorization", header);

			assertEquals(BasicAuth.STATUS_CODE, dispatch(basicApp(), source).status(),
				"header=" + header);

		}

	}

	@Test
	@DisplayName("パスワードに「:」が入っていても通る")
	void basicAllowsColonInPassword () {

		JimbleApp app = new JimbleApp() {
			{
				before(BasicAuth.of("admin", "a:b:c"));
				get("/", context -> context.response().send("ok"));
			}
		};

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.header("authorization", basic("admin", "a:b:c"));

		assertEquals(200, dispatch(app, source).status());

	}

	@Test
	@DisplayName("掛けていないルートには影響しない")
	void basicScoped () {

		Fakes.FakeResponseSink sink = dispatch(basicApp(), new Fakes.FakeRequestSource("GET", "/public"));

		assertEquals(200, sink.status());
		assertEquals("公開", sink.body());

	}

	// endregion

	// region Bot ブロック

	@Test
	@DisplayName("Bot 判定に当たれば宣言した処理が走る")
	void botBlocked () {

		JimbleApp app = new JimbleApp() {
			{
				before(new BotBlocker(context -> true, context -> context.response().send(429)));
				get("/", context -> context.response().send("本文"));
			}
		};

		Fakes.FakeResponseSink sink = dispatch(app, new Fakes.FakeRequestSource("GET", "/"));

		assertEquals(429, sink.status());
		assertFalse("本文".equals(sink.body()), "本処理が走っている");

	}

	@Test
	@DisplayName("Bot でなければ素通しする")
	void botPassed () {

		JimbleApp app = new JimbleApp() {
			{
				before(new BotBlocker(context -> false, context -> context.response().send(429)));
				get("/", context -> context.response().send("本文"));
			}
		};

		Fakes.FakeResponseSink sink = dispatch(app, new Fakes.FakeRequestSource("GET", "/"));

		assertEquals(200, sink.status());
		assertEquals("本文", sink.body());

	}

	@Test
	@DisplayName("forbidden() は 403 を返す")
	void botForbidden () {

		JimbleApp app = new JimbleApp() {
			{
				before(new BotBlocker(context -> true, context -> context.response().send(BotBlocker.STATUS_CODE)));
				get("/", context -> context.response().send("本文"));
			}
		};

		assertEquals(BotBlocker.STATUS_CODE, dispatch(app, new Fakes.FakeRequestSource("GET", "/")).status());

	}

	// endregion

}
