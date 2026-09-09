package io.jimble.web.context;

import io.jimble.util.data.Data;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.http.RequestSource;
import io.jimble.web.router.Handler;
import io.jimble.web.router.Router;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストをメトリクスに入れているか（要件 NF-O-04）
 *
 * <p>
 * <b>名前が増えないことがいちばん大事である。</b>レイテンシの名前に {@code request.path()} を使うと、
 * {@code /aaa} {@code /aab} … と叩かれるだけで名前が上限に達し、
 * <b>そのあとは本物のルートも数えられなくなる</b>。攻撃でなくても、
 * ID の入った URL を1つ名前にするだけで起きる。ここでその線を固定する。
 * </p>
 */
class WebContextMetricsTest {

	/** 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	@BeforeEach
	void clear () {

		Metrics.reset();

	}

	@Test
	@DisplayName("1リクエストで1つ数える")
	void counted () {

		try (WebContext context = Fakes.context("GET", "/posts")) {
			context.run(() -> { });
		}

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(1, counter.getLong("http.request"));
		assertEquals(1, counter.getLong("http.status.2xx"));

	}

	@Test
	@DisplayName("ステータスは百の位でまとめる")
	void statusClass () {

		close("GET", "/a", 200);
		close("GET", "/a", 204);
		close("GET", "/a", 404);
		close("GET", "/a", 500);
		close("GET", "/a", 503);

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(5, counter.getLong("http.request"));
		assertEquals(2, counter.getLong("http.status.2xx"));
		assertEquals(1, counter.getLong("http.status.4xx"));
		assertEquals(2, counter.getLong("http.status.5xx"));

	}

	@Test
	@DisplayName("レイテンシの名前はルートの型（生のパスではない）")
	void latencyKeyIsPattern () {

		Router router = new Router();
		router.get("/posts/{id}", NOOP);

		try (WebContext context = Fakes.context("GET", "/posts/12345")) {
			context.route(router.match("GET", "/posts/12345"));
			context.run(() -> { });
		}

		Data latency = Metrics.snapshot().getData("latency");

		assertTrue(latency.containsKey("http.GET /posts/{id}"), latency.keySet().toString());
		assertFalse(latency.containsKey("http.GET /posts/12345"), "生のパスを名前にしている");

		assertEquals(1, latency.getData("http.GET /posts/{id}").getLong("count"));

	}

	@Test
	@DisplayName("ID が違っても名前は増えない")
	void patternDoesNotGrow () {

		Router router = new Router();
		router.get("/posts/{id}", NOOP);

		for (int i = 0; i < 100; i++) {

			String path = "/posts/" + i;

			try (WebContext context = Fakes.context("GET", path)) {
				context.route(router.match("GET", path));
				context.run(() -> { });
			}

		}

		Data latency = Metrics.snapshot().getData("latency");

		assertEquals(1, latency.size(), latency.keySet().toString());
		assertEquals(100, latency.getData("http.GET /posts/{id}").getLong("count"));

	}

	@Test
	@DisplayName("どのルートにも当たらなかったものは1つにまとめる")
	void unmatchedCollapsed () {

		Router router = new Router();
		router.get("/posts", NOOP);

		for (int i = 0; i < 50; i++) {

			String path = "/存在しない/" + i;

			try (WebContext context = Fakes.context("GET", path)) {
				context.route(router.match("GET", path));
				context.run(() -> { });
			}

		}

		// route を渡されないまま終わったものも同じところへ
		try (WebContext context = Fakes.context("GET", "/なにか")) {
			context.run(() -> { });
		}

		Data latency = Metrics.snapshot().getData("latency");

		assertEquals(1, latency.size(), latency.keySet().toString());
		assertEquals(51, latency.getData("http.(unmatched)").getLong("count"));

	}

	@Test
	@DisplayName("メソッドが違えば別に数える")
	void methodIsPartOfName () {

		Router router = new Router();
		router.get("/posts", NOOP);
		router.post("/posts", NOOP);

		for (String method : new String[]{"GET", "POST"}) {

			try (WebContext context = Fakes.context(method, "/posts")) {
				context.route(router.match(method, "/posts"));
				context.run(() -> { });
			}

		}

		Data latency = Metrics.snapshot().getData("latency");

		assertEquals(2, latency.size(), latency.keySet().toString());
		assertTrue(latency.containsKey("http.GET /posts"));
		assertTrue(latency.containsKey("http.POST /posts"));

	}

	@Test
	@DisplayName("内部呼び出しは数えない")
	void internalNotCounted () {

		/*
		 * <b>内部呼び出し（要件 F-W-27）を数えると1リクエストが2回になる。</b>
		 * アクセスログを1行にしているのと同じ理由である
		 */
		try (WebContext outer = Fakes.context("GET", "/posts")) {

			outer.run(() -> {

				RequestSource source = new Fakes.FakeRequestSource("GET", "/internal");

				try (WebContext inner = WebContext.internal(source, new Fakes.FakeResponseSink(), outer)) {
					inner.run(() -> { });
				}

			});

		}

		Data data = Metrics.snapshot();

		assertEquals(1, data.getData("counter").getLong("http.request"));
		assertEquals(1, data.getData("latency").size(), data.getData("latency").keySet().toString());

	}

	@Test
	@DisplayName("かかった時間を入れている")
	void latencyRecorded () {

		try (WebContext context = Fakes.context("GET", "/posts")) {

			context.run(() -> {

				try {
					Thread.sleep(12);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}

			});

		}

		Data latency = Metrics.snapshot().getData("latency").getData("http.(unmatched)");

		assertEquals(1, latency.getLong("count"));

		// 12ms 寝たので、50ms のバケットより下には入らない
		assertTrue(latency.getDouble("max_ms") >= 12, "max_ms=" + latency.getDouble("max_ms"));
		assertEquals(0, latency.getData("bucket").getLong("10"));

	}

	/**
	 * ステータスを決めて1リクエスト終える
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param code		ステータス
	 */
	private static void close (String method, String path, int code) {

		try (WebContext context = Fakes.context(method, path)) {
			context.run(() -> context.response().code(code));
		}

	}

}
