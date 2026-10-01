package io.jimble.web.csrf;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CSRF トークンは本文とヘッダからだけ読む（D-245）
 *
 * <p>かつてはクエリ文字列からも読んだので、トークンが URL に載ってログや Referer に残りえた。</p>
 */
class CsrfTokenSourceTest {

	private static final String TOKEN = "tokentokentokentoken";

	@Test
	@DisplayName("クエリ文字列のトークンは受けない。フォームとヘッダは受ける")
	void sources () {

		try (WebContext query = new WebContext(new Fakes.FakeRequestSource("POST", "/x")
			.cookie(Csrf.COOKIE_NAME, TOKEN).query(Csrf.FORM_NAME, TOKEN), new Fakes.FakeResponseSink())) {
			assertThrows(HttpException.class, () -> Csrf.verify(query));
		}

		try (WebContext form = new WebContext(new Fakes.FakeRequestSource("POST", "/x")
			.cookie(Csrf.COOKIE_NAME, TOKEN).form(Csrf.FORM_NAME, TOKEN), new Fakes.FakeResponseSink())) {
			assertDoesNotThrow(() -> Csrf.verify(form));
		}

		try (WebContext header = new WebContext(new Fakes.FakeRequestSource("POST", "/x")
			.cookie(Csrf.COOKIE_NAME, TOKEN).header(Csrf.HEADER_NAME, TOKEN), new Fakes.FakeResponseSink())) {
			assertDoesNotThrow(() -> Csrf.verify(header));
		}

	}

}
