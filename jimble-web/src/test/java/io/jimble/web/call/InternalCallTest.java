package io.jimble.web.call;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 内部呼び出しのテスト（要件 F-W-27）
 */
class InternalCallTest {

	/* 実行ログ */
	private final List<String> log = new ArrayList<>();

	/**
	 * 外側を1本流して、その中で内部呼び出しを行う
	 */
	private Fakes.FakeResponseSink dispatch (JimbleApp app, String method, String rawPath) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource(method, rawPath);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
			return sink;
		}

	}

	@Test
	@DisplayName("実装済みのハンドラを HTTP を通さずに呼べる")
	void callsExistingHandler () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/posts", context -> context.response().json("count", 2));

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/posts"));
					context.response().text("code=%d body=%s".formatted(response.code(), response.text()));
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/inner");

		assertEquals(200, response.status());
		assertTrue(response.body().contains("code=200"), response.body());
		assertTrue(response.body().contains("\\\"count\\\":2") || response.body().contains("\"count\":2")
			, response.body());

	}

	@Test
	@DisplayName("パス変数とクエリが内側のハンドラに届く")
	void passesPathAndQuery () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/posts/{id}", context -> context.response()
					.json("id", context.request().bodyPath().getString("id"))
					.json("page", context.request().bodyQuery().getStringOptional("page")));

				get("/inner", context -> {
					CallResponse response = context.dispatcher().call(context
						, CallRequest.of("GET", "/api/posts/12").query("page", "3"));
					context.response().text(response.text());
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/inner");

		assertTrue(response.body().contains("12"), response.body());
		assertTrue(response.body().contains("3"), response.body());

	}

	@Test
	@DisplayName("JSON の本文が内側のハンドラに届く")
	void passesJsonBody () {

		JimbleApp app = new JimbleApp() {
			{
				post("/api/posts", context -> context.response()
					.json("title", context.request().bodyJson().getString("title")));

				get("/inner", context -> {
					Data body = new Data();
					body.put("title", "こんにちは");

					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("POST", "/api/posts").json(body));

					context.response().text(response.json().getString("title"));
				});
			}
		};

		assertEquals("こんにちは", dispatch(app, "GET", "/inner").body());

	}

	@Test
	@DisplayName("内側のルートの before / after も効く")
	void runsHooks () {

		JimbleApp app = new JimbleApp() {
			{
				path("/api", () -> {
					before(context -> log.add("before"));
					after(context -> log.add("after"));
					get("/posts", context -> {
						log.add("handler");
						context.response().json("ok", true);
					});
				});

				get("/inner", context -> {
					context.dispatcher().call(context, CallRequest.of("GET", "/api/posts"));
					context.response().text("done");
				});
			}
		};

		dispatch(app, "GET", "/inner");

		assertEquals(List.of("before", "handler", "after"), log);

	}

	@Test
	@DisplayName("before が止めたら内側のハンドラは動かない")
	void beforeCanStop () {

		JimbleApp app = new JimbleApp() {
			{
				path("/api", () -> {
					before(context -> {
						context.response().code(401);
						context.response().send();
					});
					get("/posts", context -> log.add("handler"));
				});

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/posts"));
					context.response().text(String.valueOf(response.code()));
				});
			}
		};

		assertEquals("401", dispatch(app, "GET", "/inner").body());
		assertTrue(log.isEmpty(), "before で止めたのにハンドラが動いた");

	}

	@Test
	@DisplayName("外側のヘッダと Cookie を引き継ぐ")
	void inheritsHeadersAndCookies () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/me", context -> context.response()
					.json("token", context.request().header().getStringOptional("x-token"))
					.json("cookie", context.request().cookie("sid")));

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/me"));
					context.response().text(response.text());
				});
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/inner")
			.header("x-token", "abc")
			.cookie("sid", "s1");
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
		}

		assertTrue(sink.body().contains("abc"), sink.body());
		assertTrue(sink.body().contains("s1"), sink.body());

	}

	@Test
	@DisplayName("ヘッダは呼び出し側で上書きできる")
	void canOverrideHeader () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/me", context -> context.response()
					.json("token", context.request().header().getStringOptional("x-token")));

				get("/inner", context -> {
					CallResponse response = context.dispatcher().call(context
						, CallRequest.of("GET", "/api/me").header("X-Token", "zzz"));
					context.response().text(response.text());
				});
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/inner").header("x-token", "abc");
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
		}

		assertTrue(sink.body().contains("zzz"), sink.body());
		assertFalse(sink.body().contains("abc"), sink.body());

	}

	@Test
	@DisplayName("内側で発行した Cookie は外側のレスポンスに出る")
	void cookiesReachTheClient () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/mark", context -> {
					context.cookies().put("mark", "1");
					context.response().json("ok", true);
				});

				get("/inner", context -> {
					context.dispatcher().call(context, CallRequest.of("GET", "/api/mark"));
					context.response().text("done");
				});
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/inner");
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
		}

		/*
		 * 内側が別の Cookies を持っていたころは、
		 * ここで発行した Cookie は内側の出力口に書かれて捨てられていた。
		 * セッションを新しく作った場合は「DB に行はあるが相手は id を知らない」になる。
		 */
		assertEquals(1, sink.setCookies().size(), String.valueOf(sink.setCookies()));
		assertTrue(sink.setCookies().get(0).startsWith("mark=1"), sink.setCookies().get(0));

	}

	@Test
	@DisplayName("パスは内側でもデコード済みで見える")
	void decodedPath () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/tags/{name}", context -> context.response().text(context.request().path()));

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/tags/%E6%97%A5%E6%9C%AC%E8%AA%9E"));
					context.response().text(response.text());
				});
			}
		};

		assertEquals("/api/tags/日本語", dispatch(app, "GET", "/inner").body());

	}

	@Test
	@DisplayName("実行IDを外側から引き継ぐ")
	void inheritsExecutionId () {

		List<String> ids = new ArrayList<>();

		JimbleApp app = new JimbleApp() {
			{
				get("/api/x", context -> {
					ids.add(context.executionId());
					context.response().json("ok", true);
				});

				get("/inner", context -> {
					ids.add(context.executionId());
					context.dispatcher().call(context, CallRequest.of("GET", "/api/x"));
					context.response().text("done");
				});
			}
		};

		dispatch(app, "GET", "/inner");

		assertEquals(2, ids.size());
		assertEquals(ids.get(0), ids.get(1), "内部呼び出しで実行IDが変わっている");

	}

	@Test
	@DisplayName("未マッチのパスは 404 が返る（例外にしない）")
	void notFound () {

		JimbleApp app = new JimbleApp() {
			{
				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/nope"));
					context.response().text(String.valueOf(response.code()));
				});
			}
		};

		assertEquals("404", dispatch(app, "GET", "/inner").body());

	}

	@Test
	@DisplayName("内側が例外を投げても外側は壊れない（500 として返る）")
	void innerFailure () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/boom", context -> {
					throw new IllegalStateException("失敗");
				});

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/boom"));
					context.response().text(String.valueOf(response.code()));
				});
			}
		};

		assertEquals("500", dispatch(app, "GET", "/inner").body());

	}

	@Test
	@DisplayName("呼び出しが循環しても上限で止まる")
	void stopsRunawayRecursion () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/loop", context -> {
					log.add("loop");
					context.dispatcher().call(context, CallRequest.of("GET", "/api/loop"));
					context.response().json("ok", true);
				});

				get("/inner", context -> {
					CallResponse response = context.dispatcher()
						.call(context, CallRequest.of("GET", "/api/loop"));
					context.response().text(String.valueOf(response.code()));
				});
			}
		};

		/*
		 * 自分を呼ぶルートを1つ作ると、そのまま無限に潜る。
		 * StackOverflowError で落ちる前に、上限で止まることが要点である。
		 */
		assertEquals("200", dispatch(app, "GET", "/inner").body());
		assertEquals(Calls.MAX_DEPTH, log.size(), "上限まで潜ってから止まっていない");

	}

	@Test
	@DisplayName("外側が無い内部呼び出しは作れない")
	void requiresOuter () {

		assertThrows(NullPointerException.class, () -> WebContext.internal(
			new Fakes.FakeRequestSource("GET", "/x"), new Fakes.FakeResponseSink(), null));

	}

	@Test
	@DisplayName("内部呼び出しのコンテキストは isInternal が true")
	void marksInternal () {

		List<Boolean> flags = new ArrayList<>();

		JimbleApp app = new JimbleApp() {
			{
				get("/api/x", context -> {
					flags.add(context.isInternal());
					context.response().json("ok", true);
				});

				get("/inner", context -> {
					flags.add(context.isInternal());
					context.dispatcher().call(context, CallRequest.of("GET", "/api/x"));
					context.response().text("done");
				});
			}
		};

		dispatch(app, "GET", "/inner");

		assertEquals(List.of(false, true), flags);

	}

	@Test
	@DisplayName("ディスパッチしたコンテキストからディスパッチャが取れる")
	void exposesDispatcher () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> {
					assertNotNull(context.dispatcher());
					context.response().text("ok");
				});
			}
		};

		assertEquals("ok", dispatch(app, "GET", "/x").body());

	}

}
