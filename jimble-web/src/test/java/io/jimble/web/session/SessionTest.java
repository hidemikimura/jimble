package io.jimble.web.session;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * セッションのテスト（M4 ステップ2）
 *
 * <p>
 * DB / Redis の保存先は結合テスト側で見る。ここは
 * <b>セッションの振る舞い</b>（明示保存・保存し忘れの検知・保存先の切替）と
 * Cookie セッションを見る。
 * </p>
 */
class SessionTest {

	/* 警告ログ */
	private final List<String> warnings = new ArrayList<>();

	@BeforeEach
	void captureLog () {

		SessionStores.reset();

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level == Level.WARN) {
				warnings.add(message);
			}
		});

	}

	@AfterEach
	void restore () {

		Log.resetSink();
		Conf.reload();
		SessionStores.reset();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));
		SessionStores.reset();

	}

	/**
	 * コンテキストを作る
	 *
	 * @return	コンテキスト
	 */
	private WebContext context () {

		return new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink());

	}

	// region 既定はセッションなし

	@Test
	@DisplayName("既定はセッションなし。次のリクエストには残らない")
	void defaultIsNone () {

		try (WebContext context = context()) {
			context.session().put("user_id", 42);
			// 同じリクエストの中では読み返せる
			assertEquals("42", context.session().get("user_id"));
			context.session().save();
		}

		// 別のリクエストには残らない
		try (WebContext context = context()) {
			assertEquals("", context.session().get("user_id"));
		}

	}

	@Test
	@DisplayName("セッションを触らなければ Cookie も出ない（F-S-12）")
	void untouchedSessionIssuesNoCookie () {

		conf("session.store = \"cookie\"\nsession.secret = \"k\"");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.response().send("ok");
		}

		assertTrue(sink.setCookies().isEmpty(), sink.setCookies().toString());

	}

	// endregion

	// region 明示保存（F-S-02 / F-S-03）

	@Test
	@DisplayName("save() を呼ばないと警告が出る（F-S-03）")
	void warnsOnUnsavedChange () {

		conf("session.store = \"cookie\"\nsession.secret = \"k\"");

		try (WebContext context = context()) {
			context.session().put("user_id", 42);
			// save() を呼ばない
		}

		assertTrue(warnings.stream().anyMatch(w -> w.contains("save()")), warnings.toString());

	}

	@Test
	@DisplayName("save() を呼べば警告は出ない")
	void noWarnAfterSave () {

		conf("session.store = \"cookie\"\nsession.secret = \"k\"");

		try (WebContext context = context()) {
			context.session().put("user_id", 42);
			context.session().save();
		}

		assertFalse(warnings.stream().anyMatch(w -> w.contains("save()")), warnings.toString());

	}

	@Test
	@DisplayName("読むだけなら警告は出ない")
	void noWarnOnReadOnly () {

		conf("session.store = \"cookie\"\nsession.secret = \"k\"");

		try (WebContext context = context()) {
			context.session().get("user_id");
		}

		assertFalse(warnings.stream().anyMatch(w -> w.contains("save()")), warnings.toString());

	}

	// endregion

	// region Cookie セッション（F-S-08）

	@Test
	@DisplayName("Cookie セッションは往復する")
	void cookieSessionRoundTrip () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 42);
			context.session().save();
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		String value = setCookie.substring(
			(CookieSessionStore.COOKIE_NAME + "=").length(), setCookie.indexOf(';'));

		assertFalse(value.contains("user_id"), "中身が平文で載っている: " + value);

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(CookieSessionStore.COOKIE_NAME, value);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals(42, context.session().getInt("user_id"));
		}

	}

	@Test
	@DisplayName("Cookie セッションが改ざんされていたら空になる")
	void cookieSessionRejectsTampered () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(CookieSessionStore.COOKIE_NAME, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertTrue(context.session().data().isEmpty());
		}

	}

	@Test
	@DisplayName("大きすぎる Cookie セッションは黙って捨てず落とす（F-S-08）")
	void cookieSessionSizeLimit () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");

		try (WebContext context = context()) {
			context.session().put("big", "x".repeat(CookieSessionStore.MAX_VALUE_BYTES));
			IllegalStateException ex = assertThrows(IllegalStateException.class, () -> context.session().save());
			assertTrue(ex.getMessage().contains("大きすぎます"), ex.getMessage());
		}

	}

	@Test
	@DisplayName("暗号鍵が無ければ Cookie セッションは作れない")
	void cookieSessionRequiresSecret () {

		conf("session.store = \"cookie\"");

		assertThrows(IllegalStateException.class, CookieSessionStore::new);

	}

	// endregion

	// region 保存先の切替（F-S-11）

	@Test
	@DisplayName("リクエスト単位で保存先を変えられる")
	void perRequestStore () {

		conf("session.store = \"none\"\nsession.secret = \"test-secret\"");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			// 既定は none だが、このリクエストだけ cookie にする
			context.sessionStore(SessionStores.cookie());
			context.session().put("user_id", 42);
			context.session().save();
			context.response().send("ok");
		}

		assertEquals(1, sink.setCookies().size(), sink.setCookies().toString());

	}

	@Test
	@DisplayName("使い始めたあとに保存先は変えられない")
	void storeCannotChangeAfterUse () {

		try (WebContext context = context()) {
			context.session().get("user_id");
			assertThrows(IllegalStateException.class, () -> context.sessionStore(SessionStores.none()));
		}

	}

	@Test
	@DisplayName("知らない保存先名は警告を出してセッションなしになる")
	void unknownStoreFallsBack () {

		conf("session.store = \"memcached\"");

		assertSame(SessionStores.none(), SessionStores.defaultStore());
		assertTrue(warnings.stream().anyMatch(w -> w.contains("memcached")), warnings.toString());

	}

	// endregion

	// region アプリのコードは保存先を知らない（F-S-10）

	@Test
	@DisplayName("保存先を変えてもアプリのコードは同じ")
	void sameCodeForAnyStore () {

		conf("session.secret = \"test-secret\"");

		for (SessionStore store : List.of(SessionStores.none(), SessionStores.cookie())) {

			try (WebContext context = context()) {
				context.sessionStore(store);
				context.session().put("key", "value");
				context.session().save();
				assertTrue(context.session().isSaved());
			}

		}

	}

	// endregion

}
