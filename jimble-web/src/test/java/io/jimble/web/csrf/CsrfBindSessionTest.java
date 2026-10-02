package io.jimble.web.csrf;

import io.jimble.util.conf.Conf;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.session.SessionStores;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSRF トークンをセッションに結びつける（csrf.bind_session。D-255）
 */
class CsrfBindSessionTest {

	@AfterEach
	void reset () {

		Conf.reload();
		SessionStores.reset();

	}

	static final class App extends JimbleApp {

		{
			get("/token", context -> context.response().send(Csrf.token(context)));

			before(Csrf::verify);

			post("/login", context -> {
				Auth.login(context, Principal.of(1, "kimura"));
				context.response().send("ok");
			});

			post("/do", context -> context.response().send("done"));
		}

	}

	private static void conf (String store, boolean bind) {

		Conf.replace(ConfigFactory.parseString("""
			csrf.bind_session = %s
			session {
				store  = "%s"
				secret = "session-secret-session-secret-00"
			}
			""".formatted(bind, store)));

		SessionStores.reset();

	}

	@Test
	@DisplayName("結びつけると、攻撃者が植え付けたトークン（Cookie と同じ値を送る）は通らない。既定では通る")
	void plantedTokenIsRejected () throws Exception {

		// 既定（double submit cookie）：Cookie と送った値が同じなら通る
		conf("cookie", false);
		JimbleServer plain = JimbleServer.start(new App(), 0);
		try {
			assertEquals(200, post(plain, "/do", List.of(Csrf.COOKIE_NAME + "=planted"), "planted").statusCode());
		} finally {
			plain.stop();
		}

		conf("cookie", true);
		JimbleServer bound = JimbleServer.start(new App(), 0);
		try {
			assertEquals(403, post(bound, "/do", List.of(Csrf.COOKIE_NAME + "=planted"), "planted").statusCode());
		} finally {
			bound.stop();
		}

	}

	@Test
	@DisplayName("セッションに置いたトークンで通る。トークンの Cookie は出さない")
	void tokenInSession () throws Exception {

		conf("cookie", true);
		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> page = get(server, "/token", List.of());
			List<String> held = cookiesOf(page);
			String token = page.body();

			assertFalse(held.stream().anyMatch(cookie -> cookie.startsWith(Csrf.COOKIE_NAME + "=")), held.toString());
			assertEquals(200, post(server, "/do", held, token).statusCode());
			assertEquals(403, post(server, "/do", held, "other").statusCode());

			// 読み直しても同じトークン（開いているフォームを壊さない）
			assertEquals(token, get(server, "/token", held).body());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("ログインでトークンを作り直し、新しいものを X-CSRF-Token で返す。古いものは通らない")
	void rotatesOnLogin () throws Exception {

		conf("cookie", true);
		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> page = get(server, "/token", List.of());
			List<String> held = cookiesOf(page);
			String before = page.body();

			HttpResponse<String> login = post(server, "/login", held, before);
			assertEquals(200, login.statusCode());

			String after = login.headers().firstValue(Csrf.HEADER_NAME).orElse(null);
			assertTrue(after != null && !after.isEmpty(), "新しいトークンが返っていない");
			assertNotEquals(before, after);

			held = merge(held, cookiesOf(login));

			assertEquals(403, post(server, "/do", held, before).statusCode(), "ログインの前のトークンが通った");
			assertEquals(200, post(server, "/do", held, after).statusCode());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("セッションの置き場が無いのに結びつけようとしたら、黙って Cookie に戻さず失敗する")
	void requiresSessionStore () throws Exception {

		conf("none", true);
		JimbleServer server = JimbleServer.start(new App(), 0);

		try {
			assertEquals(500, get(server, "/token", List.of()).statusCode());
		} finally {
			server.stop();
		}

	}

	// region 小物

	private static HttpResponse<String> get (JimbleServer server, String path, List<String> cookies) throws Exception {

		return send(server, HttpRequest.newBuilder(uri(server, path)).GET(), cookies);

	}

	private static HttpResponse<String> post (JimbleServer server, String path, List<String> cookies, String token) throws Exception {

		return send(server, HttpRequest.newBuilder(uri(server, path))
			.header(Csrf.HEADER_NAME, token)
			.POST(HttpRequest.BodyPublishers.noBody()), cookies);

	}

	private static URI uri (JimbleServer server, String path) {

		return URI.create("http://127.0.0.1:" + server.port() + path);

	}

	private static HttpResponse<String> send (JimbleServer server, HttpRequest.Builder builder, List<String> cookies) throws Exception {

		builder.timeout(Duration.ofSeconds(10));

		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ", cookies));
		}

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		}

	}

	private static List<String> cookiesOf (HttpResponse<String> response) {

		List<String> result = new ArrayList<>();

		for (String header : response.headers().allValues("set-cookie")) {
			result.add(header.split(";", 2)[0]);
		}

		return result;

	}

	/** 同じ名前は新しいほうで置き換える（値が空なら消す） */
	private static List<String> merge (List<String> held, List<String> fresh) {

		List<String> result = new ArrayList<>();

		for (String cookie : held) {
			String name = cookie.split("=", 2)[0];
			if (fresh.stream().noneMatch(f -> f.startsWith(name + "="))) {
				result.add(cookie);
			}
		}

		for (String cookie : fresh) {
			if (!cookie.endsWith("=")) {
				result.add(cookie);
			}
		}

		return result;

	}

	// endregion

}
