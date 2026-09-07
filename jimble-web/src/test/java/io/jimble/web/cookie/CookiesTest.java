package io.jimble.web.cookie;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.flash.Flash;
import io.jimble.web.http.HttpException;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cookie / Flash / CSRF のテスト（M4 ステップ1）
 */
class CookiesTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	// region Cookie

	@Test
	@DisplayName("書き込みは Set-Cookie として1つだけ出る")
	void writeOnce () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().put("name", "one");
			context.cookies().put("name", "two");
			context.response().send("ok");
		}

		List<String> setCookies = sink.setCookies();
		assertEquals(1, setCookies.size(), setCookies.toString());
		assertTrue(setCookies.getFirst().startsWith("name=two"), setCookies.getFirst());

	}

	@Test
	@DisplayName("既定は HttpOnly / Secure / SameSite=Lax")
	void secureByDefault () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().put("name", "value");
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		assertTrue(setCookie.contains("HttpOnly"), setCookie);
		assertTrue(setCookie.contains("Secure"), setCookie);
		assertTrue(setCookie.contains("SameSite=Lax"), setCookie);

	}

	@Test
	@DisplayName("消すと Max-Age=0 で出る")
	void remove () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().remove("name");
			context.response().send("ok");
		}

		assertTrue(sink.setCookies().getFirst().contains("Max-Age=0"), sink.setCookies().toString());

	}

	@Test
	@DisplayName("Cookie を触らなければ Set-Cookie は出ない")
	void noCookieNoHeader () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.response().send("ok");
		}

		assertTrue(sink.setCookies().isEmpty());

	}

	@Test
	@DisplayName("鍵があれば署名して書き、検証して読む")
	void signedRoundTrip () {

		conf("cookie.secret = \"test-secret\"");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		String signed;

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().put("user", "42");
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		signed = setCookie.substring("user=".length(), setCookie.indexOf(';'));
		assertNotEquals("42", signed, "署名されていない");

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie("user", signed);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals("42", context.cookies().get("user"));
		}

	}

	@Test
	@DisplayName("署名が合わない Cookie は読めない（生の値は raw() で取れる）")
	void signedRejectsTampered () {

		conf("cookie.secret = \"test-secret\"");

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie("user", "forged|999");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals("", context.cookies().get("user"), "改ざんされた値がアプリに渡っている");
			assertEquals("forged|999", context.cookies().raw("user"));
		}

	}

	// endregion

	// region Flash

	@Test
	@DisplayName("Flash は書くと次のリクエストで読める")
	void flashWrite () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.flash().put("message", "保存しました");
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		assertTrue(setCookie.startsWith(Flash.PREFIX + "message="), setCookie);

	}

	@Test
	@DisplayName("String 以外の put も Cookie に出る")
	void flashWriteNonString () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			// 移送元は data に入れるだけで Cookie を書いておらず、黙って消えていた
			context.flash().put("count", 3);
			context.response().send("ok");
		}

		assertEquals(1, sink.setCookies().size());
		assertTrue(sink.setCookies().getFirst().startsWith(Flash.PREFIX + "count=3"), sink.setCookies().toString());

	}

	@Test
	@DisplayName("Flash は読んだら失効する（次リクエストにだけ残る）")
	void flashExpiresAfterRead () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(Flash.PREFIX + "message", "保存しました");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			assertEquals("保存しました", context.flash().get("message"));
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		assertTrue(setCookie.startsWith(Flash.PREFIX + "message="), setCookie);
		assertTrue(setCookie.contains("Max-Age=0"), "読んだのに失効していない: " + setCookie);

	}

	@Test
	@DisplayName("読んだうえで書き直せば、失効ではなく新しい値が出る")
	void flashRewrite () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(Flash.PREFIX + "message", "古い");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			assertEquals("古い", context.flash().get("message"));
			context.flash().put("message", "新しい");
			context.response().send("ok");
		}

		// 同じ名前の Set-Cookie が2つ出ると、どちらが効くかブラウザ任せになる
		assertEquals(1, sink.setCookies().size(), sink.setCookies().toString());
		assertTrue(sink.setCookies().getFirst().startsWith(Flash.PREFIX + "message=新しい"), sink.setCookies().toString());

	}

	// endregion

	// region CSRF

	@Test
	@DisplayName("トークンは初回に発行され、Cookie に載る")
	void csrfIssuesToken () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		String token;

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			token = Csrf.token(context);
			assertFalse(token.isEmpty());
			// 2回目は同じものを返す
			assertEquals(token, Csrf.token(context));
			context.response().send("ok");
		}

		assertEquals(1, sink.setCookies().size());
		assertTrue(sink.setCookies().getFirst().startsWith(Csrf.COOKIE_NAME + "="), sink.setCookies().toString());

	}

	@Test
	@DisplayName("GET は検証しない")
	void csrfSkipsSafeMethod () {

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {
			Csrf.verify(context);
		}

	}

	@Test
	@DisplayName("ヘッダのトークンが一致すれば通る")
	void csrfAcceptsMatchingHeader () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/items");
		source.cookie(Csrf.COOKIE_NAME, "token-1");
		source.header(Csrf.HEADER_NAME.toLowerCase(), "token-1");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			Csrf.verify(context);
			assertTrue(Csrf.isValid(context));
		}

	}

	@Test
	@DisplayName("トークンが無ければ 403")
	void csrfRejectsMissingToken () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/items");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			HttpException ex = assertThrows(HttpException.class, () -> Csrf.verify(context));
			assertEquals(403, ex.statusCode());
		}

	}

	@Test
	@DisplayName("トークンが違えば 403")
	void csrfRejectsMismatch () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/items");
		source.cookie(Csrf.COOKIE_NAME, "token-1");
		source.header(Csrf.HEADER_NAME.toLowerCase(), "token-2");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals(403, assertThrows(HttpException.class, () -> Csrf.verify(context)).statusCode());
			assertFalse(Csrf.isValid(context));
		}

	}

	// endregion

}
