package io.jimble.web.session;

import com.typesafe.config.ConfigFactory;
import io.jimble.db.redis.RedisClient;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.ratelimit.RateLimitResult;
import io.jimble.web.ratelimit.RedisRateLimitStore;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis クラスターで、セッションと流量制限（どちらも Lua を使う）が動くか
 *
 * <p>
 * <b>Redis クラスターが要る</b>（{@code .github/scripts/redis-cluster.sh}）。
 * {@code JIMBLE_TEST_REDIS_CLUSTER} が無ければ飛ばす。
 * </p>
 */
@Tag("db")
class RedisClusterSessionIntegrationTest {

	/* クラスターのノード（, で区切る） */
	private static final String NODES = System.getenv("JIMBLE_TEST_REDIS_CLUSTER");

	/* セッションの数（ノードをまたがせる） */
	private static final int SESSIONS = 10;

	@BeforeAll
	static void setUp () {

		assumeTrue(NODES != null && !NODES.isBlank(), "JIMBLE_TEST_REDIS_CLUSTER が無いので飛ばします");

		String nodes = Arrays.stream(NODES.split(",")).map(n -> "\"" + n.trim() + "\"").collect(Collectors.joining(", "));

		RedisClient.close();
		Conf.replace(ConfigFactory.parseString("""
			redis.host = ""
			redis.cluster.nodes = [%s]
			redis.settings.connection_minimum_idle = 2
			""".formatted(nodes)).withFallback(ConfigFactory.load()));

		assertEquals(RedisClient.Mode.CLUSTER, RedisClient.mode());

	}

	@AfterAll
	static void tearDown () {

		RedisClient.close();
		Conf.reload();

	}

	@Test
	@DisplayName("セッションを保存して読め、消せる")
	void session () {

		RedisSessionStore store = new RedisSessionStore(30);

		for (int i = 0; i < SESSIONS; i++) {

			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
				context.sessionStore(store);
				context.session().put("user_id", i + 1L);
				context.session().save();
				context.response().send("ok");
			}

			String sessionId = sessionIdFrom(sink);
			assertNotNull(sessionId, "セッション Cookie が出ていない");

			try (WebContext context = withSession(sessionId)) {
				context.sessionStore(store);
				assertEquals(i + 1L, context.session().getLong("user_id"));
				context.session().destroy();
			}

			try (WebContext context = withSession(sessionId)) {
				context.sessionStore(store);
				// 無ければ 0（getLong の既定）
				assertEquals(0L, context.session().getLong("user_id"), "消したのに読めた");
			}

		}

	}

	@Test
	@DisplayName("流量制限：上限まで通し、超えたら止める")
	void rateLimit () {

		RedisRateLimitStore store = new RedisRateLimitStore();
		String key = "cluster:" + UUID.randomUUID();

		for (int i = 0; i < 3; i++) {
			assertTrue(store.consume(key, 3, Duration.ofMinutes(1)).allowed(), "上限の内なのに止められた: " + i);
		}

		RateLimitResult over = store.consume(key, 3, Duration.ofMinutes(1));

		assertFalse(over.allowed(), "上限を超えたのに通った");
		assertTrue(over.retryAfterMillis() > 0);

	}

	private static String sessionIdFrom (Fakes.FakeResponseSink sink) {

		String prefix = SessionConf.cookieName() + "=";

		for (String setCookie : sink.setCookies()) {
			if (setCookie.startsWith(prefix)) {
				return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
			}
		}

		return null;

	}

	private static WebContext withSession (String sessionId) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		return new WebContext(source, new Fakes.FakeResponseSink());

	}

}
