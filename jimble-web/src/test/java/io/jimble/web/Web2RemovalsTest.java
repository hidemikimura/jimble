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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 で消すもの・変わるものを、1.5 のうちに知らせる（Web。要件 D-192）
 */
class Web2RemovalsTest {

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

	// region 消したもの

	private static boolean hasMethod (Class<?> type, String name, Class<?>... params) {

		try {
			type.getMethod(name, params);
			return true;
		} catch (NoSuchMethodException ex) {
			return false;
		}

	}

	@Test
	@DisplayName("D-196 1.5 で非推奨にしたものは消えている。Request は Data を継承しない")
	void removed () {

		assertFalse(hasMethod(Router.class, "path", String.class), "Router.path(String) が残っています");
		assertTrue(hasMethod(Router.class, "path", String.class, java.util.function.Consumer.class));
		assertFalse(hasMethod(Cookies.class, "put", Cookie.class), "Cookies.put(Cookie) が残っています");
		assertFalse(io.jimble.util.data.Data.class.isAssignableFrom(Request.class), "Request が Data を継承しています");

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
	@DisplayName("D-196 destroy() のあとの変更は例外（1.5 は警告して入れなかった）")
	void sessionChangeAfterDestroyThrows () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");
		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.session().put("a", "1");
			context.session().destroy();
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.session().put("b", "2"));
			assertTrue(e.getMessage().contains("destroy()"), e.getMessage());
			assertThrows(IllegalStateException.class, () -> context.session().remove("a"));
			assertThrows(IllegalStateException.class, () -> context.session().clear());
			assertEquals("", context.session().get("b"));
		}

	}

	@Test
	@DisplayName("D-196 session().data() は読み取り専用の写し（書き換えると例外。1.x は保存されずに消えた）")
	void sessionDataIsReadOnly () {

		conf("session.store = \"cookie\"\nsession.secret = \"test-secret\"");
		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.session().put("a", "1");
			io.jimble.util.data.Data data = context.session().data();
			assertEquals("1", data.getString("a"));
			assertThrows(UnsupportedOperationException.class, () -> data.put("b", "2"));
			assertThrows(UnsupportedOperationException.class, () -> data.remove("a"));
			assertThrows(UnsupportedOperationException.class, () -> data.keySet().remove("a"));
			assertThrows(UnsupportedOperationException.class, () -> context.request().session().put("c", "3"));
			assertFalse(context.session().has("b"));
		}

	}

	// endregion

	// region 応答

	@Test
	@DisplayName("D-196 2種類目の返し方を積んだら例外（1.x は最初の1つだけ返し、残りを黙って捨てた）")
	void conflictingBodiesThrow () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/a"), new Fakes.FakeResponseSink())) {
			context.response().json("x", 1).json("y", 2);          // 同じ種類を重ねるのはよい
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.response().redirect("/b"));
			assertTrue(e.getMessage().contains("json") && e.getMessage().contains("redirect"), e.getMessage());
			assertThrows(IllegalStateException.class, () -> context.response().text("t"));
		}

	}

	@Test
	@DisplayName("D-196 送ったあとのヘッダと Cookie は例外（1.x は黙って捨てた）")
	void afterSendThrows () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			context.response().json("x", 1).send();
			IllegalStateException h = assertThrows(IllegalStateException.class, () -> context.response().setResponseHeader("X-Late", "1"));
			assertTrue(h.getMessage().contains("X-Late"), h.getMessage());
			IllegalStateException c = assertThrows(IllegalStateException.class, () -> context.cookies().put("late", "1"));
			assertTrue(c.getMessage().contains("late"), c.getMessage());
		}
		assertEquals(List.of(), warns);

	}

	// endregion

	// region リクエスト

	@Test
	@DisplayName("D-196 壊れた JSON の本文は 400（1.x は空の Data として扱い、送った値が全部無かったことになった）")
	void malformedJsonIs400 () {

		for (String broken : new String[] {"{\"a\":", "{\"a\":1", "not json", "{\"a\":1} x"}) {
			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/x").body("application/json", broken);
			try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
				io.jimble.web.http.HttpException e = assertThrows(io.jimble.web.http.HttpException.class, () -> context.request().bodyJson(), broken);
				assertEquals(400, e.statusCode());
				// 何度読んでも同じ（途中まで組んだ本文を返さない）
				assertThrows(io.jimble.web.http.HttpException.class, () -> context.request().bodyAll(), broken);
			}
		}

		// 空の本文と {} は読める
		for (String ok : new String[] {"", "{}", " { } "}) {
			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/x").body("application/json", ok);
			try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
				assertTrue(context.request().bodyJson().isEmpty(), ok);
			}
		}

	}

	@Test
	@DisplayName("D-196 paging() のあとに違う件数の paging(50) は例外（1.x は 50 を黙って無視した）")
	void pagingPerThrows () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x"), new Fakes.FakeResponseSink())) {
			context.request().paging();
			context.request().paging();
			context.request().paging(0);
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.request().paging(50));
			assertTrue(e.getMessage().contains("paging(50)"), e.getMessage());
		}

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x"), new Fakes.FakeResponseSink())) {
			context.request().paging(50);
			context.request().paging(50);                       // 同じ件数はよい
			context.request().paging();                         // 件数を言わないのもよい
		}

	}

	@Test
	@DisplayName("D-196 ?page=abc は 1 ページ目（利用者の入力なので止めない）")
	void pagingIgnoresUnreadablePage () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x").query("page", "abc"), new Fakes.FakeResponseSink())) {
			assertEquals(1, context.request().paging().page());
		}

	}

	// endregion

}
