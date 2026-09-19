package io.jimble.web.response;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.RedirectLoopException;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リダイレクトループ判定
 *
 * <p>
 * <b>自分自身へ戻る {@code redirect()} は {@code error(...)} に 500 で渡る。</b>
 * 見つけるのは1段だけで、PRG（POST → GET）は止めない。
 * </p>
 */
class RedirectLoopTest {

	/* error() に渡った原因 */
	private final AtomicReference<Throwable> caught = new AtomicReference<>();
	private final AtomicReference<Integer> caughtStatus = new AtomicReference<>();

	/** 飛び先を1つ持つアプリ */
	final class TestApp extends JimbleApp {

		{
			error((context, cause, statusCode) -> {
				caught.set(cause);
				caughtStatus.set(statusCode);
				context.response().code(statusCode).text("error page").send();
			});

			get("/self", context -> context.response().redirect("/self"));
			get("/self-abs", context -> context.response().redirect("http://localhost/self-abs"));
			get("/self-slash", context -> context.response().redirect("/self-slash/"));
			get("/self-query", context -> context.response().redirect("/self-query?page=2"));
			get("/other", context -> context.response().redirect("/elsewhere"));
			get("/other-host", context -> context.response().redirect("https://example.com/other-host"));
			post("/login", context -> context.response().redirect("/login"));
		}

	}

	private Fakes.FakeResponseSink run (String method, String rawPath) {

		Dispatcher dispatcher = new Dispatcher(new TestApp());
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource(method, rawPath);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
			return sink;
		}

	}

	@Test
	@DisplayName("同じパスへの redirect は error() に 500 で渡る")
	void selfRedirectGoesToErrorHandler () {

		Fakes.FakeResponseSink sink = run("GET", "/self");

		assertInstanceOf(RedirectLoopException.class, caught.get());
		assertEquals(500, caughtStatus.get());
		assertEquals(500, sink.status());
		assertEquals("error page", sink.body());
		assertNull(sink.headers().get("Location"));

	}

	@Test
	@DisplayName("絶対 URL でも同じなら止める")
	void absoluteSelfRedirect () {

		run("GET", "/self-abs");
		assertInstanceOf(RedirectLoopException.class, caught.get());

	}

	@Test
	@DisplayName("末尾スラッシュの違いだけなら同じとみなす")
	void trailingSlashIsSame () {

		run("GET", "/self-slash");
		assertInstanceOf(RedirectLoopException.class, caught.get());

	}

	@Test
	@DisplayName("クエリが違えばループではない")
	void differentQueryIsNotLoop () {

		Fakes.FakeResponseSink sink = run("GET", "/self-query");

		assertNull(caught.get());
		assertEquals(302, sink.status());
		assertEquals("/self-query?page=2", sink.headers().get("Location"));

	}

	@Test
	@DisplayName("別のパスへの redirect は普通に 302")
	void otherPathIsNotLoop () {

		Fakes.FakeResponseSink sink = run("GET", "/other");

		assertNull(caught.get());
		assertEquals(302, sink.status());
		assertEquals("/elsewhere", sink.headers().get("Location"));

	}

	@Test
	@DisplayName("別ホストは同じパスでもループではない")
	void otherHostIsNotLoop () {

		Fakes.FakeResponseSink sink = run("GET", "/other-host");

		assertNull(caught.get());
		assertEquals(302, sink.status());

	}

	@Test
	@DisplayName("POST から同じパスへ戻す（PRG）は止めない")
	void postRedirectGetIsNotLoop () {

		Fakes.FakeResponseSink sink = run("POST", "/login");

		assertNull(caught.get());
		assertEquals(302, sink.status());
		assertEquals("/login", sink.headers().get("Location"));

	}

	@Test
	@DisplayName("判定だけの単体：解決できない飛び先はループではない")
	void unparsableLocationIsNotLoop () {

		try (WebContext context = Fakes.context("GET", "/x")) {
			assertFalse(RedirectLoop.isLoop(context.request(), "http://[bad"));
			assertTrue(RedirectLoop.isLoop(context.request(), "/x"));
			assertTrue(RedirectLoop.isLoop(context.request(), "/x/"));
			assertTrue(RedirectLoop.isLoop(context.request(), "/./x"));
			// {@code //x} はスキーム相対（ホスト x）なので別の場所
			assertFalse(RedirectLoop.isLoop(context.request(), "//x"));
			assertFalse(RedirectLoop.isLoop(context.request(), "/x?a=1"));
		}

	}

}
