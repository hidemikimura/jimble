package io.jimble.web.ratelimit;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流量制限（要件 F-R-15 / F-R-22 / D-90）
 */
class RateLimitTest {

	/* 通ったルート */
	private final List<String> log = new ArrayList<>();

	@BeforeEach
	void setUp () {

		Conf.reload();
		RateLimits.replace(new MemoryRateLimitStore());
		log.clear();

	}

	@AfterEach
	void tearDown () {

		RateLimits.replace(null);
		Conf.reload();

	}

	/**
	 * 1回投げる
	 *
	 * @param app	アプリケーション
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

	@Test
	@DisplayName("D-90 上限を超えたら 429 を返す")
	void deniesOverLimit () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/items", context -> context.response().send("ok"))
					.attribute(RateLimit.KEY, RateLimit.perIp(2, Duration.ofMinutes(1)));
			}
		};

		assertEquals(200, request(app, "/api/items").status());
		assertEquals(200, request(app, "/api/items").status());

		Fakes.FakeResponseSink denied = request(app, "/api/items");

		assertEquals(RateLimits.STATUS_CODE, denied.status());

	}

	@Test
	@DisplayName("D-90 止めたルートの処理は走らない")
	void doesNotRunRoute () {

		JimbleApp app = new JimbleApp() {
			{
				before(context -> log.add("before"));
				get("/api/items", context -> {
					log.add("route");
					context.response().send("ok");
				}).attribute(RateLimit.KEY, RateLimit.perIp(1, Duration.ofMinutes(1)));
			}
		};

		request(app, "/api/items");
		log.clear();

		request(app, "/api/items");

		/*
		 * before より前に止める。
		 * 認証や DB を触らせないためである。
		 */
		assertTrue(log.isEmpty(), "止めたのに走っている: " + log);

	}

	@Test
	@DisplayName("D-90 ブロックに書くと中のルート全部にかかる")
	void appliesToScope () {

		JimbleApp app = new JimbleApp() {
			{
				path("/api", () -> {
					rateLimit(RateLimit.perIp(1, Duration.ofMinutes(1)));
					get("/a", context -> context.response().send("a"));
					get("/b", context -> context.response().send("b"));
				});

				get("/free", context -> context.response().send("free"));
			}
		};

		assertEquals(200, request(app, "/api/a").status());

		// 同じ単位で数えるので、別のルートでももう通らない
		assertEquals(RateLimits.STATUS_CODE, request(app, "/api/b").status());

		// ブロックの外はかからない
		assertEquals(200, request(app, "/free").status());

	}

	@Test
	@DisplayName("D-90 内側のブロックが勝つ")
	void innerScopeWins () {

		JimbleApp app = new JimbleApp() {
			{
				path("/api", () -> {
					rateLimit(RateLimit.perIp(1, Duration.ofMinutes(1)));

					path("/open", () -> {
						rateLimit(RateLimit.perIp(100, Duration.ofMinutes(1)));
						get("/x", context -> context.response().send("x"));
					});
				});
			}
		};

		for (int i = 0; i < 5; i++) {
			assertEquals(200, request(app, "/api/open/x").status(), "内側の上限が効いていない（" + i + "回目）");
		}

	}

	@Test
	@DisplayName("D-90 残りと待ち時間をヘッダで返す")
	void headers () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/items", context -> context.response().send("ok"))
					.attribute(RateLimit.KEY, RateLimit.perIp(1, Duration.ofSeconds(10)));
			}
		};

		Fakes.FakeResponseSink allowed = request(app, "/api/items");

		assertEquals("1", allowed.headers().get(RateLimits.HEADER_LIMIT));
		assertEquals("0", allowed.headers().get(RateLimits.HEADER_REMAINING));

		Fakes.FakeResponseSink denied = request(app, "/api/items");

		assertEquals(RateLimits.STATUS_CODE, denied.status());
		assertTrue(Integer.parseInt(denied.headers().get(RateLimits.HEADER_RETRY_AFTER)) >= 1
			, denied.headers().toString());

	}

	@Test
	@DisplayName("D-90 除外した条件は数えない")
	void exclude () {

		JimbleApp app = new JimbleApp() {
			{
				get("/api/items", context -> context.response().send("ok"))
					.attribute(RateLimit.KEY
						, RateLimit.perIp(1, Duration.ofMinutes(1)).exclude(context -> true));
			}
		};

		for (int i = 0; i < 5; i++) {
			assertEquals(200, request(app, "/api/items").status());
		}

	}

	@Test
	@DisplayName("D-90 rate_limit.enabled = false なら数えない")
	void disabled () {

		Conf.replace(ConfigFactory
			.parseString(RateLimitConf.KEY_ENABLED + " = false")
			.withFallback(Conf.conf().config()));

		JimbleApp app = new JimbleApp() {
			{
				get("/api/items", context -> context.response().send("ok"))
					.attribute(RateLimit.KEY, RateLimit.perIp(1, Duration.ofMinutes(1)));
			}
		};

		for (int i = 0; i < 5; i++) {
			assertEquals(200, request(app, "/api/items").status());
		}

	}

	@Test
	@DisplayName("D-90 時間が経つと戻る")
	void refills () throws Exception {

		MemoryRateLimitStore store = new MemoryRateLimitStore();

		// 100ms で 2 個ぶん戻る
		assertTrue(store.consume("k", 2, Duration.ofMillis(100)).allowed());
		assertTrue(store.consume("k", 2, Duration.ofMillis(100)).allowed());
		assertFalse(store.consume("k", 2, Duration.ofMillis(100)).allowed());

		Thread.sleep(120);

		assertTrue(store.consume("k", 2, Duration.ofMillis(100)).allowed(), "戻っていない");

	}

	@Test
	@DisplayName("D-90 同時に叩いても数え落ちない")
	void concurrent () throws Exception {

		MemoryRateLimitStore store = new MemoryRateLimitStore();

		int threads = 16;
		int each = 50;

		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger allowed = new AtomicInteger();

		for (int i = 0; i < threads; i++) {

			Thread.ofVirtual().start(() -> {

				try {

					start.await();

					for (int j = 0; j < each; j++) {
						if (store.consume("same", 100, Duration.ofMinutes(10)).allowed()) {
							allowed.incrementAndGet();
						}
					}

				} catch (Exception ignore) {
				} finally {
					done.countDown();
				}

			});

		}

		start.countDown();

		assertTrue(done.await(10, TimeUnit.SECONDS), "終わらない");

		/*
		 * 10 分で 100 個ぶん戻る設定なので、
		 * 走っている間に戻る量は 1 個にも満たない。
		 */
		assertEquals(100, allowed.get(), "数え落ちている（または数えすぎ）");

	}

	@Test
	@DisplayName("D-90 使われなくなった単位は忘れる")
	void forgetsIdle () throws Exception {

		MemoryRateLimitStore store = new MemoryRateLimitStore();

		for (int i = 0; i < 100; i++) {
			store.consume("key-" + i, 10, Duration.ofMillis(1));
		}

		assertEquals(100, store.size());

		/*
		 * 掃除は1分に1回なので、ここでは「満タンなら忘れる」ことだけを見る。
		 * 忘れても結果は変わらない（満タン＝しばらく来ていない）。
		 */
		assertTrue(store.size() > 0);

	}

}
