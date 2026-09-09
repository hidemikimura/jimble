package io.jimble.web.context;

import io.jimble.core.trace.RecordingTracer;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;
import io.jimble.web.http.RequestSource;
import io.jimble.web.router.Handler;
import io.jimble.web.router.Router;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストをトレースに出しているか（要件 NF-O-05）
 *
 * <p>
 * <b>スパンはリクエストの<u>始まり</u>で開いていないといけない。</b>
 * 終わってから作ると、その間に出た SQL のスパンが<b>親を見つけられず、
 * バラバラの根なしスパンとして届く</b>——トレースを見ても何も繋がっていない、
 * という一番がっかりする壊れ方をする。ここで固定しておく。
 * </p>
 */
class WebContextTraceTest {

	/** 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	/** 出す先 */
	private RecordingTracer tracer;

	@BeforeEach
	void use () {

		tracer = new RecordingTracer();
		Tracing.use(tracer);

	}

	@AfterEach
	void off () {

		Tracing.off();

	}

	// region 1リクエスト1スパン

	@Test
	@DisplayName("1リクエストで1つ、種類は server")
	void oneSpanPerRequest () {

		try (WebContext context = Fakes.context("GET", "/posts")) {
			context.run(() -> { });
		}

		assertEquals(1, tracer.spans().size());

		RecordingTracer.Recorded span = tracer.spans().get(0);

		assertEquals(SpanKind.server, span.kind());
		assertTrue(span.closed(), "閉じられていない");
		assertEquals("GET", span.attributes().get("http.request.method"));
		assertEquals(200L, span.attributes().get("http.response.status_code"));

	}

	@Test
	@DisplayName("名前はルートの型（生のパスではない）")
	void nameIsRoutePattern () {

		Router router = new Router();
		router.get("/posts/{id}", NOOP);

		try (WebContext context = Fakes.context("GET", "/posts/12345")) {
			context.route(router.match("GET", "/posts/12345"));
			context.run(() -> { });
		}

		RecordingTracer.Recorded span = tracer.spans().get(0);

		assertEquals("GET /posts/{id}", span.name());
		assertEquals("/posts/{id}", span.attributes().get("http.route"));

	}

	@Test
	@DisplayName("ID が違っても名前は増えない")
	void nameDoesNotGrow () {

		Router router = new Router();
		router.get("/posts/{id}", NOOP);

		for (int i = 0; i < 50; i++) {

			String path = "/posts/" + i;

			try (WebContext context = Fakes.context("GET", path)) {
				context.route(router.match("GET", path));
				context.run(() -> { });
			}

		}

		assertEquals(1, tracer.spans().stream().map(RecordingTracer.Recorded::name).distinct().count()
			, tracer.spans().stream().map(RecordingTracer.Recorded::name).distinct().toList().toString());

	}

	@Test
	@DisplayName("どのルートにも当たらなかったものは1つにまとめる")
	void unmatched () {

		try (WebContext context = Fakes.context("GET", "/存在しない/12345")) {
			context.run(() -> { });
		}

		assertEquals("GET (unmatched)", tracer.spans().get(0).name());
		assertNull(tracer.spans().get(0).attributes().get("http.route"));

	}

	// endregion

	// region 中で起きたことが子になる

	@Test
	@DisplayName("リクエストの途中で始めたスパンは、その子になる")
	void childOfRequest () {

		try (WebContext context = Fakes.context("GET", "/posts")) {

			context.run(() -> {

				/*
				 * <b>ここが本題である。</b>スパンをリクエストの終わりに作っていると、
				 * この子は親を見つけられない
				 */
				try (var child = Tracing.start("画像の変換", SpanKind.internal)) {
					assertNotNull(child.traceId());
				}

			});

		}

		RecordingTracer.Recorded child = tracer.find("画像の変換");

		assertNotNull(child, "子のスパンが無い");
		assertNotNull(child.parent(), "親が付いていない");
		assertEquals(SpanKind.server, child.parent().kind());

	}

	@Test
	@DisplayName("SQL のスパンもリクエストの子になる")
	void sqlIsChildOfRequest () {

		try (WebContext context = Fakes.context("GET", "/posts")) {

			context.run(() -> io.jimble.core.context.Context
				.recordSqlExecution(3_000_000L, "SELECT * FROM post WHERE id = ?"));

		}

		RecordingTracer.Recorded sql = tracer.find("SELECT post");

		assertNotNull(sql, "SQL のスパンが無い");
		assertNotNull(sql.parent(), "親が付いていない");
		assertEquals(SpanKind.client, sql.kind());
		assertEquals(3_000_000L, sql.elapsedNanos());

	}

	// endregion

	// region 外から来たトレース

	@Test
	@DisplayName("traceparent が来たら、そのトレースに繋ぐ")
	void remoteParent () {

		String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

		RequestSource source = new Fakes.FakeRequestSource("GET", "/posts")
			.header("traceparent", traceparent);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			context.run(() -> { });
		}

		// RecordingTracer は渡された traceparent をそのままトレース ID として覚える
		assertEquals(traceparent, tracer.spans().get(0).traceId());

	}

	@Test
	@DisplayName("traceparent が無ければ新しいトレースを始める")
	void withoutTraceparent () {

		try (WebContext context = Fakes.context("GET", "/posts")) {
			context.run(() -> { });
		}

		assertEquals("trace-1", tracer.spans().get(0).traceId());

	}

	// endregion

	// region そのほか

	@Test
	@DisplayName("内部呼び出しは server ではなく internal")
	void internalCall () {

		/*
		 * <b>server が2つあると、トレースを見る道具の側で「入口が2つある」ように見える。</b>
		 * 内部呼び出し（要件 F-W-27）は外から来たリクエストではない
		 */
		Router router = new Router();
		router.get("/internal", NOOP);

		try (WebContext outer = Fakes.context("GET", "/posts")) {

			outer.run(() -> {

				RequestSource source = new Fakes.FakeRequestSource("GET", "/internal");

				try (WebContext inner = WebContext.internal(source, new Fakes.FakeResponseSink(), outer)) {
					inner.route(router.match("GET", "/internal"));
					inner.run(() -> { });
				}

			});

		}

		assertEquals(2, tracer.spans().size());

		RecordingTracer.Recorded inner = tracer.find("GET /internal");

		assertNotNull(inner);
		assertEquals(SpanKind.internal, inner.kind());
		assertEquals(SpanKind.server, inner.parent().kind());

	}

	@Test
	@DisplayName("5xx だけを失敗として印を付ける")
	void onlyServerErrorIsError () {

		for (int code : new int[]{200, 302, 400, 404, 500, 503}) {

			try (WebContext context = Fakes.context("GET", "/posts")) {
				context.run(() -> context.response().code(code));
			}

		}

		/*
		 * <b>404 はアプリが正しく返した答えである。</b>
		 * トレースの上で赤くすると、404 の多いサイトが常に赤く見える
		 */
		assertNull(tracer.spans().get(0).attributes().get("error.type"));
		assertNull(tracer.spans().get(2).attributes().get("error.type"), "400 を失敗にしている");
		assertNull(tracer.spans().get(3).attributes().get("error.type"), "404 を失敗にしている");
		assertEquals("500", tracer.spans().get(4).attributes().get("error.type"));
		assertEquals("503", tracer.spans().get(5).attributes().get("error.type"));

	}

	@Test
	@DisplayName("SQL の実行回数もスパンに残す")
	void sqlCount () {

		try (WebContext context = Fakes.context("GET", "/posts")) {

			context.run(() -> {
				io.jimble.core.context.Context.recordSqlExecution(1_000_000L, "SELECT * FROM post");
				io.jimble.core.context.Context.recordSqlExecution(1_000_000L, "SELECT * FROM comment");
			});

		}

		RecordingTracer.Recorded request = tracer.find("GET (unmatched)");

		assertEquals(2L, request.attributes().get("db.sql.execute_count"));

	}

	@Test
	@DisplayName("トレースが無効なら、スパンは1つも作らない")
	void disabled () {

		Tracing.off();

		try (WebContext context = Fakes.context("GET", "/posts")) {
			context.run(() -> { });
		}

		assertTrue(tracer.spans().isEmpty());
		assertFalse(Tracing.enabled());

	}

	// endregion

}
