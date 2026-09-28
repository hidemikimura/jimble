package io.jimble.web;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.internal.WarnOnce;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.Cookie;
import io.jimble.web.cookie.Cookies;
import io.jimble.web.request.Request;
import io.jimble.web.router.Router;
import io.jimble.web.session.CookieSessionStore;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 で消すもの・変わるものを、1.5 のうちに知らせる（Web。要件 D-192）
 */
@SuppressWarnings("removal")  // 1.x の書き方も確かめている（2.0 で消す。要件 D-192）
class WebDeprecationAndWarningTest {

	private final List<String> warns = new ArrayList<>();

	@BeforeEach
	void capture () {

		WarnOnce.reset();
		SessionStores.reset();
		Log.sink((logger, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				warns.add(message);
			}
		});

	}

	@AfterEach
	void reset () {

		Log.resetSink();
		WarnOnce.reset();
		Conf.reload();
		SessionStores.reset();

	}

	private void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));
		SessionStores.reset();

	}

	private long count (String text) {

		return warns.stream().filter(w -> w.contains(text)).count();

	}

	// region 非推奨

	private static void assertForRemoval (AnnotatedElement element) {

		Deprecated d = element.getAnnotation(Deprecated.class);
		assertTrue(d != null && d.forRemoval() && "1.5.0".equals(d.since()), element + " が非推奨（forRemoval, since 1.5.0）になっていません");

	}

	@Test
	@DisplayName("D-192 2.0 で消すものに @Deprecated(since = \"1.5.0\", forRemoval = true)")
	void deprecated () throws Exception {

		assertForRemoval(Router.class.getMethod("path", String.class));
		assertForRemoval(Cookies.class.getMethod("put", Cookie.class));
		for (String m : new String[] {"getString", "getInt", "getLong", "getBoolean"}) {
			assertForRemoval(Request.class.getMethod(m, String.class));
		}

	}

	// endregion

	// region セッション

	@Test
	@DisplayName("D-192 save() のあとに変えたら、もう一度 save() で保存される（1.4 までは黙って捨てていた）")
	void sessionChangeAfterSave () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("a", "1");
			context.session().save();
			context.session().put("b", "2");        // Auth.login(...) のあとに入れるのと同じ形
			assertTrue(context.session().isUnsavedChange(), "save() のあとの変更が「保存済み」のまま");
			context.session().save();
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getLast();
		String value = setCookie.substring((CookieSessionStore.COOKIE_NAME + "=").length(), setCookie.indexOf(';'));
		Fakes.FakeRequestSource next = new Fakes.FakeRequestSource("GET", "/");
		next.cookie(CookieSessionStore.COOKIE_NAME, value);
		try (WebContext context = new WebContext(next, new Fakes.FakeResponseSink())) {
			assertEquals("1", context.session().get("a"));
			assertEquals("2", context.session().get("b"), "save() のあとに入れた値が保存されていない");
		}

	}

	@Test
	@DisplayName("D-192 save() のあとに変えて保存しなければ、終わりに「save() が呼ばれていません」")
	void sessionUnsavedWarns () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");
		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.session().put("a", "1");
			context.session().save();
			context.session().put("b", "2");
			context.response().send("ok");
		}
		assertEquals(1, count("save() が呼ばれていません"), warns.toString());

	}

	@Test
	@DisplayName("D-192 destroy() のあとの変更は入れず、警告する")
	void sessionChangeAfterDestroy () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");
		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.session().put("a", "1");
			context.session().destroy();
			context.session().put("b", "2");
			assertFalse(context.session().isUnsavedChange());
			assertEquals("", context.session().get("b"));
		}
		assertEquals(1, count("destroy() のあと"), warns.toString());

	}

	// endregion

	// region 応答

	@Test
	@DisplayName("D-192 json と redirect を両方積んだら、どれが返りどれが捨てられるかを警告する")
	void conflictingBodies () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/a"), new Fakes.FakeResponseSink())) {
			context.response().json("x", 1).redirect("/b").send();
		}
		assertEquals(1, count("[json, redirect]"), warns.toString());
		assertTrue(warns.getFirst().contains("返るのは json だけ"), warns.getFirst());

	}

	@Test
	@DisplayName("D-192 送ったあとのヘッダと Cookie は警告する（1つだけなら言わない）")
	void afterSend () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.response().json("x", 1).send();
			assertEquals(List.of(), warns, "1つだけなのに警告している");
			context.response().setResponseHeader("X-Late", "1");
			context.cookies().put("late", "1");
		}
		assertEquals(1, count("X-Late"), warns.toString());
		assertEquals(1, count("Cookie late"), warns.toString());

	}

	// endregion

	// region リクエスト

	@Test
	@DisplayName("D-192 壊れた JSON の本文は警告する（空の Data を返すのは変えない）")
	void malformedJson () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/x").body("application/json", "{\"a\":");
		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertTrue(context.request().bodyJson().isEmpty());
		}
		assertEquals(1, count("JSON を読めませんでした"), warns.toString());

	}

	@Test
	@DisplayName("D-192 paging() のあとの paging(50) は効かないことを警告する")
	void pagingPer () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x"), new Fakes.FakeResponseSink())) {
			context.request().paging();
			context.request().paging();
			assertEquals(List.of(), warns);
			context.request().paging(50);
		}
		assertEquals(1, count("paging(50)"), warns.toString());

	}

	@Test
	@DisplayName("D-192 request().getString(\"x\") は値を読まないことを警告する（区分の名前は言わない）")
	void requestReadAsData () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x").query("id", "7"), new Fakes.FakeResponseSink())) {
			context.request().request();
			assertEquals(null, context.request().getString("id"));
			assertEquals("7", context.request().bodyAll().getString("id"));
		}
		assertEquals(1, count("request().getString(\"id\")"), warns.toString());

	}

	// endregion

}
