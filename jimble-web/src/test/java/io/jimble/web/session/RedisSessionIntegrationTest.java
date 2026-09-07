package io.jimble.web.session;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis セッションの結合テスト（要件 F-S-01 / 10.3）
 *
 * <p>
 * <b>サンプルアプリでは埋まらない分</b>である（要件 10.3）。
 * セッションの保存先は設定で選ぶので、サンプルは1つしか通らない。
 * </p>
 *
 * <p>実 Redis が要る（要件 D-16）。</p>
 */
@Tag("db")
class RedisSessionIntegrationTest {

	/* 保存先 */
	private static RedisSessionStore store;

	@BeforeAll
	static void setUp () {

		Conf.reload();

		store = new RedisSessionStore(30);

	}

	@AfterAll
	static void tearDown () {

		RedisClient.close();

	}

	@Test
	@DisplayName("保存すると次のリクエストで読める")
	void saveAndLoad () {

		String sessionId;

		// 1回目のリクエスト
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {

			context.sessionStore(store);
			context.session().put("user_id", 42L);
			context.session().save();
			context.response().send("ok");

		}

		sessionId = sessionIdFrom(sink);
		assertNotNull(sessionId, "セッション Cookie が出ていない");

		// 2回目のリクエスト（同じ Cookie を持っていく）
		try (WebContext context = withSession(sessionId)) {

			context.sessionStore(store);

			assertEquals(42L, context.session().getLong("user_id"));

		}

	}

	@Test
	@DisplayName("destroy すると読めなくなる")
	void destroy () {

		String sessionId;

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.sessionStore(store);
			context.session().put("user_id", 7L);
			context.session().save();
			context.response().send("ok");
		}

		sessionId = sessionIdFrom(sink);

		try (WebContext context = withSession(sessionId)) {
			context.sessionStore(store);
			context.session().destroy();
		}

		try (WebContext context = withSession(sessionId)) {
			context.sessionStore(store);
			assertEquals(0L, context.session().getLong("user_id"), "消えていない");
		}

	}

	@Test
	@DisplayName("期限は Redis の TTL に任せている")
	void ttl () {

		String sessionId;

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.sessionStore(store);
			context.session().put("user_id", 1L);
			context.session().save();
			context.response().send("ok");
		}

		sessionId = sessionIdFrom(sink);

		/*
		 * 移送元は __accessed_at というキーを値に混ぜて自前で期限を見ていた。
		 * そのキーが無いと Instant.parse(null) で落ち、
		 * 期限切れの行が Redis に残り続けた。TTL ならどちらも起きない。
		 */
		long ttl = RedisClient.client()
			.getMap(RedisSessionStore.KEY_PREFIX + sessionId)
			.remainTimeToLive();

		assertTrue(ttl > 0, "TTL が設定されていない: " + ttl);
		assertTrue(ttl <= 30 * 60 * 1000L, "TTL が長すぎる: " + ttl);

	}

	@Test
	@DisplayName("アプリから見えるのは入れたものだけ")
	void noInternalKeys () {

		String sessionId;

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.sessionStore(store);
			context.session().put("user_id", 3L);
			context.session().save();
			context.response().send("ok");
		}

		sessionId = sessionIdFrom(sink);

		try (WebContext context = withSession(sessionId)) {

			context.sessionStore(store);

			// 移送元は __accessed_at がここに混ざっていた
			assertEquals(List.of("user_id"), List.copyOf(context.session().data().keySet())
				, context.session().data().keySet().toString());

		}

	}

	@Test
	@DisplayName("触らなければ Redis に何も作らない（F-S-12）")
	void untouched () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.sessionStore(store);
			// session() を呼ばない
			context.response().send("ok");
		}

		assertNull(sessionIdFrom(sink), "触っていないのに Cookie が出ている");

	}

	// region 小物

	/**
	 * 返された Cookie からセッションIDを取る
	 *
	 * @param context	コンテキスト
	 * @return	セッションID。無ければ null
	 */
	private static String sessionIdFrom (Fakes.FakeResponseSink sink) {

		String prefix = SessionConf.cookieName() + "=";

		for (String setCookie : sink.setCookies()) {
			if (setCookie.startsWith(prefix)) {
				return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
			}
		}

		return null;

	}

	/**
	 * セッション Cookie を持ったリクエスト
	 *
	 * @param sessionId	セッションID
	 * @return	コンテキスト
	 */
	private static WebContext withSession (String sessionId) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		return new WebContext(source, new Fakes.FakeResponseSink());

	}

	// endregion

}
