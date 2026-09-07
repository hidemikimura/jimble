package io.jimble.web.server;

import io.jimble.core.context.Context;
import io.jimble.core.executor.AbstractExecutor;
import io.jimble.core.executor.Executor;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispatcher のテスト
 */
class DispatcherTest {

	/* 実行ログ */
	private final List<String> log = new ArrayList<>();

	/* エラーログ */
	private final List<String> errorLog = new ArrayList<>();

	@BeforeEach
	void captureLog () {

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level == org.slf4j.event.Level.ERROR) {
				// メッセージは例外の message。付帯情報は data.objects に入る
				errorLog.add(message + " " + data);
			}
		});

	}

	@AfterEach
	void restoreLog () {

		Log.resetSink();

	}

	/**
	 * ディスパッチして結果のレスポンスを返す
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

	/**
	 * 記録するだけの Executor
	 */
	private Executor<WebContext> recorder (String name) {

		return context -> log.add(name);

	}


	// region 正常系

	@Test
	@DisplayName("ハンドラが送信しなくてもレスポンスは送信される（本文なしは 204）")
	void handlerWithoutSend () {

		JimbleApp app = new JimbleApp() {
			{
				get("/hello", context -> log.add("handler"));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/hello");

		assertEquals(List.of("handler"), log);
		assertTrue(response.isSent());
		// D-18: 本文もステータスコードも設定されていない場合は 204 No Content
		assertEquals(204, response.status());

	}

	@Test
	@DisplayName("ハンドラが json() だけ呼んだ場合も送信される")
	void handlerWithJsonOnly () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api", context -> context.response().json("ok", true));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/api");

		// json() は「内容を積んだ」だけで送信はしていない。
		// Dispatcher の「送信済みなら打ち切る」判定がこれを送信済みと誤認していた
		assertTrue(response.isSent(), "json() だけのハンドラで何も送信されていない");
		assertEquals(200, response.status());
		assertTrue(response.body().contains("\"ok\""), response.body());

	}

	@Test
	@DisplayName("ハンドラが text() だけ呼んだ場合も送信される")
	void handlerWithTextOnly () {

		JimbleApp app = new JimbleApp() {
			{
				get("/text", context -> context.response().text("hello"));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/text");

		assertTrue(response.isSent());
		assertEquals("hello", response.body());

	}

	@Test
	@DisplayName("ステータスコードだけ設定した場合はそのコードで送信される")
	void handlerWithCodeOnly () {

		JimbleApp app = new JimbleApp() {
			{
				get("/hello", context -> context.response().code(200));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/hello");

		assertTrue(response.isSent());
		assertEquals(200, response.status());

	}

	@Test
	@DisplayName("ハンドラ内で Context.current() が使える")
	void contextIsBoundInsideHandler () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> {
					assertTrue(Context.isBound());
					assertSame(context, Context.current(WebContext.class));
					log.add("ok");
				});
			}
		};

		dispatch(app, "GET", "/x");

		assertEquals(List.of("ok"), log);

	}

	@Test
	@DisplayName("マッチしたルートがコンテキストから読める")
	void routeIsAvailableOnContext () {

		JimbleApp app = new JimbleApp() {
			{
				get("/users/{id}", context ->
					log.add("id=" + context.route().variables().get("id")));
			}
		};

		dispatch(app, "GET", "/users/42");

		assertEquals(List.of("id=42"), log);

	}

	@Test
	@DisplayName("onRequest はルート未マッチでも呼ばれる")
	void onRequestIsCalledEvenWhenUnmatched () {

		JimbleApp app = new JimbleApp() {

			{
				get("/x", context -> log.add("handler"));
			}

			@Override
			protected void onRequest (WebContext context) {
				log.add("onRequest");
			}

		};

		dispatch(app, "GET", "/nope");

		assertEquals(List.of("onRequest"), log);

	}

	@Test
	@DisplayName("onComplete は例外が出ても必ず呼ばれる")
	void onCompleteAlwaysRuns () {

		JimbleApp app = new JimbleApp() {

			{
				get("/boom", context -> {
					throw new IllegalStateException("boom");
				});
			}

			@Override
			protected void onComplete (WebContext context) {
				log.add("onComplete");
			}

		};

		dispatch(app, "GET", "/boom");

		assertEquals(List.of("onComplete"), log);

	}

	// endregion


	// region T-8 ステージの打ち切り

	@Test
	@DisplayName("T-8 onRequest が送信したら以降は実行されない")
	void sendInOnRequestStopsEverything () {

		JimbleApp app = new JimbleApp() {

			{
				before(context -> log.add("before"));
				get("/x", context -> log.add("handler"));
				after(context -> log.add("after"));
			}

			@Override
			protected void onRequest (WebContext context) {
				log.add("onRequest");
				context.response().code(403).send("no");
			}

		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/x");

		assertEquals(List.of("onRequest", "after"), log, "before とハンドラは動かない。after は必ず動く");
		assertEquals(403, response.status());

	}

	@Test
	@DisplayName("T-8 before が送信したらハンドラは実行されない")
	void sendInBeforeStopsHandler () {

		JimbleApp app = new JimbleApp() {
			{
				before(context -> {
					log.add("before1");
					context.response().code(401).send();
				});
				before(context -> log.add("before2"));
				get("/x", context -> log.add("handler"));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/x");

		assertEquals(List.of("before1"), log);
		assertEquals(401, response.status());

	}

	@Test
	@DisplayName("T-8 ハンドラが送信したら Executor は実行されない")
	void sendInHandlerStopsExecutors () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> {
					log.add("handler");
					context.addExecutor(recorder("never"));
					context.response().send();
				});
			}
		};

		dispatch(app, "GET", "/x");

		assertEquals(List.of("handler"), log);

	}

	// endregion


	// region T-7 Executorキュー

	@Test
	@DisplayName("T-7 Executor は登録順に実行される")
	void executorsRunInOrder () {

		JimbleApp app = new JimbleApp() {
			{
				post("/save"
					, () -> recorder("validation")
					, () -> recorder("useCase"));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "POST", "/save");

		assertEquals(List.of("validation", "useCase"), log);
		assertTrue(response.isSent());

	}

	@Test
	@DisplayName("T-7 Executor の中から次の Executor を積める")
	void executorCanAppend () {

		JimbleApp app = new JimbleApp() {
			{
				post("/save", () -> context -> {
					log.add("first");
					context.addExecutor(recorder("appended"));
				});
			}
		};

		dispatch(app, "POST", "/save");

		assertEquals(List.of("first", "appended"), log);

	}

	@Test
	@DisplayName("T-7 キャンセルすると残りが破棄されキャンセル処理が走る")
	void executorCancelDiscardsRest () {

		JimbleApp app = new JimbleApp() {
			{
				post("/save"
					, () -> new AbstractExecutor<WebContext>() {

						@Override
						public void execute (WebContext context) {
							log.add("validation");
							cancel();
						}

						@Override
						public void onCancel (WebContext context) {
							log.add("onCancel");
							context.response().code(422).send("invalid");
						}

					}
					, () -> recorder("never"));
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "POST", "/save");

		assertEquals(List.of("validation", "onCancel"), log);
		assertEquals(422, response.status());

	}

	@Test
	@DisplayName("T-7 キャンセル時にレスポンスを送らなくてもレスポンスは返る")
	void cancelWithoutSendStillResponds () {

		JimbleApp app = new JimbleApp() {
			{
				post("/save", () -> new AbstractExecutor<WebContext>() {
					@Override
					public void execute (WebContext context) {
						cancel();
					}
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "POST", "/save");

		assertTrue(response.isSent());

	}

	@Test
	@DisplayName("F-W-19 OPTIONS は Executorキューを回さない")
	void optionsDoesNotRunExecutors () {

		JimbleApp app = new JimbleApp() {
			{
				options("/x", context -> {
					log.add("handler");
					context.addExecutor(recorder("never"));
				});
			}
		};

		dispatch(app, "OPTIONS", "/x");

		assertEquals(List.of("handler"), log);

	}

	// endregion


	// region T-13〜T-16 エラー処理

	@Test
	@DisplayName("T-13 HttpException のステータスコードがエラーハンドラに渡る")
	void httpExceptionStatusCode () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) -> {
					log.add("error:" + statusCode);
					context.response().code(statusCode).send();
				});
				get("/forbidden", context -> {
					throw new HttpException(403, "だめ");
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/forbidden");

		assertEquals(List.of("error:403"), log);
		assertEquals(403, response.status());

	}

	@Test
	@DisplayName("T-13 HttpException 以外は 500")
	void otherExceptionIs500 () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) -> log.add("error:" + statusCode));
				get("/boom", context -> {
					throw new IllegalStateException("boom");
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/boom");

		assertEquals(List.of("error:500"), log);
		assertEquals(500, response.status());

	}

	@Test
	@DisplayName("T-13 resolveStatusCode を上書きすると独自例外にコードを割り当てられる")
	void customStatusCodeResolution () {

		class MyException extends RuntimeException {
		}

		JimbleApp app = new JimbleApp() {

			{
				error((context, cause, statusCode) -> log.add("error:" + statusCode));
				get("/x", context -> {
					throw new MyException();
				});
			}

			@Override
			protected int resolveStatusCode (Throwable cause) {
				if (cause instanceof MyException) {
					return 409;
				}
				return super.resolveStatusCode(cause);
			}

		};

		assertEquals(409, dispatch(app, "GET", "/x").status());
		assertEquals(List.of("error:409"), log);

	}

	@Test
	@DisplayName("T-14 未マッチはトップレベルの error に 404 で届く")
	void unmatchedGoesToRootErrorHandler () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) -> {
					log.add("error:" + statusCode);
					context.response().code(statusCode).send("Not Found");
				});
				get("/x", context -> { });
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/nope");

		assertEquals(List.of("error:404"), log);
		assertEquals(404, response.status());
		assertEquals("Not Found", response.body());

	}

	@Test
	@DisplayName("T-14 error ハンドラが無ければ 404 が返る")
	void unmatchedWithoutErrorHandler () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> { });
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/nope");

		assertTrue(response.isSent());
		assertEquals(404, response.status());

	}

	@Test
	@DisplayName("T-15 error ハンドラが例外を投げても次に進み、最終的にレスポンスが返る")
	void failingErrorHandlerFallsThrough () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) -> log.add("outer"));

				path("/admin", () -> {
					error((context, cause, statusCode) -> {
						log.add("inner");
						throw new IllegalStateException("error handler failed");
					});
					get("/x", context -> {
						throw new HttpException(400, "bad");
					});
				});
			}
		};

		Fakes.FakeResponseSink response = dispatch(app, "GET", "/admin/x");

		assertEquals(List.of("inner", "outer"), log, "内側が失敗しても外側に進むこと");
		assertTrue(response.isSent());
		assertEquals(400, response.status());
		assertTrue(errorLog.stream().anyMatch(message -> message.contains("エラーハンドラで例外")));

	}

	@Test
	@DisplayName("T-16 404 はエラーログに出ない")
	void notFoundIsNotLoggedAsError () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> { });
			}
		};

		dispatch(app, "GET", "/nope");

		assertEquals(List.of(), errorLog);

	}

	@Test
	@DisplayName("T-16 500 はエラーログに出る")
	void serverErrorIsLogged () {

		JimbleApp app = new JimbleApp() {
			{
				get("/boom", context -> {
					throw new IllegalStateException("boom");
				});
			}
		};

		dispatch(app, "GET", "/boom");

		assertEquals(1, errorLog.size());
		assertTrue(errorLog.getFirst().contains("リクエスト処理で例外"), errorLog.toString());

	}

	@Test
	@DisplayName("after が例外を投げても他の after と onComplete は実行される")
	void failingAfterDoesNotStopOthers () {

		JimbleApp app = new JimbleApp() {

			{
				after(context -> {
					log.add("after1");
					throw new IllegalStateException("after failed");
				});
				after(context -> log.add("after2"));
				get("/x", context -> { });
			}

			@Override
			protected void onComplete (WebContext context) {
				log.add("onComplete");
			}

		};

		dispatch(app, "GET", "/x");

		assertEquals(List.of("after1", "after2", "onComplete"), log);

	}

	// endregion

	// region エラーハンドラが組み立てただけのとき

	@Test
	@DisplayName("エラーハンドラが json で組み立てただけでも、その本文が返る")
	void errorHandlerBuildsWithoutSend () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) ->
					context.response().json("error", cause.getMessage()));
			}
		};

		/*
		 * send() を呼ばずに組み立てるのは、通常の経路とまったく同じ書き方である
		 * （そちらは Stage.send が流す）。
		 * ここが send(statusCode) だったときは中身を捨てていて、
		 * 「404 は返るが本文が空」になっていた。
		 */
		Fakes.FakeResponseSink sink = dispatch(app, "GET", "/nope");

		assertEquals(404, sink.status());
		assertTrue(String.valueOf(sink.body()).contains("ルートが見つかりません")
			, String.valueOf(sink.body()));

	}

	@Test
	@DisplayName("エラーハンドラがステータスを変えたらそちらが勝つ")
	void errorHandlerOverridesStatus () {

		JimbleApp app = new JimbleApp() {
			{
				error((context, cause, statusCode) ->
					context.response().code(418).json("error", "ちゃつぼ"));
			}
		};

		Fakes.FakeResponseSink sink = dispatch(app, "GET", "/nope");

		assertEquals(418, sink.status());

	}

	// endregion

}
