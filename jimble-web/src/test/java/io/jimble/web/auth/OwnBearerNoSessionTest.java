package io.jimble.web.auth;

import io.jimble.util.conf.Conf;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.http.HttpException;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiToken} を使わず、アプリが自分で {@code Authorization: Bearer} を確かめるルート（2.4.0 の不具合）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>2.4.0 で、セッションを使わないルート（{@link Auth#NO_SESSION}）に Bearer が来ると 500 になっていた。</b>
 * {@link Auth#guard} が保存先を「なし」に変える前に {@link ApiToken#isTokenRequest} を呼び、
 * これが Bearer を見てセッションを読み始めていたため（「セッションを使い始めたあとで保存先は変えられません」）。
 * 独自のトークン（MCP の接続など）を Bearer で受けるアプリは、{@code ApiToken.authenticate} を置かないので、
 * {@code ApiToken} のテストでは見つからなかった。
 * </p>
 *
 * <p>DB は使わない（保存先「なし」のルートと、セッションを読まない道だけを通す）。</p>
 */
class OwnBearerNoSessionTest {

	private static JimbleServer server;

	/* Csrf.verify を Auth.guard より先に置く、よくある順番のアプリ */
	private static JimbleServer csrfFirst;

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	@BeforeAll
	static void start () {

		Conf.reload();

		server = JimbleServer.start(new JimbleApp() {
			{
				before(Auth::guard);

				// アプリが自分で Bearer を確かめる、セッションを使わないルート（ApiToken.authenticate は置かない）
				path("/own", () -> {
					attribute(Auth.PUBLIC, true);
					attribute(Auth.NO_SESSION, true);
					before(context -> {
						if (!"Bearer own-secret".equals(context.request().header().getStringOptional("authorization"))) {
							throw new HttpException(401, "トークンが違います");
						}
					});
					post("/mcp", context -> context.response().send("ok"));
				});

				error((context, cause, statusCode) -> context.response().code(statusCode).send(
					statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

		csrfFirst = JimbleServer.start(new JimbleApp() {
			{
				before(Csrf::verify);
				before(Auth::guard);

				path("/own", () -> {
					attribute(Auth.PUBLIC, true);
					attribute(Auth.NO_SESSION, true);
					get("/read", context -> context.response().send("read"));
				});

				error((context, cause, statusCode) -> context.response().code(statusCode).send(
					statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

	}

	@AfterAll
	static void stop () {

		if (server != null) {
			server.stop();
		}
		if (csrfFirst != null) {
			csrfFirst.stop();
		}

	}

	@Test
	@DisplayName("ApiToken を使わないアプリの Bearer を、NO_SESSION のルートで受けても 500 にならない。Cookie も出さない")
	void ownBearer () throws Exception {

		HttpResponse<String> ok = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/own/mcp"))
			.header("Authorization", "Bearer own-secret")
			.POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, ok.statusCode(), ok.body());
		assertEquals("ok", ok.body());
		assertTrue(ok.headers().allValues("set-cookie").isEmpty(), "セッションを使わないルートで Cookie を出した: " + ok.headers().allValues("set-cookie"));

		HttpResponse<String> wrong = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/own/mcp"))
			.header("Authorization", "Bearer wrong")
			.POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(401, wrong.statusCode(), wrong.body());

	}

	@Test
	@DisplayName("Csrf.verify を先に置いても、Bearer の付いた要求でセッションを始めない（GET は CSRF を見ずに Auth.guard まで進む。500 にならない）")
	void csrfFirst () throws Exception {

		HttpResponse<String> read = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + csrfFirst.port() + "/own/read"))
			.header("Authorization", "Bearer something")
			.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, read.statusCode(), read.body());
		assertEquals("read", read.body());
		assertTrue(read.headers().allValues("set-cookie").isEmpty(), read.headers().allValues("set-cookie").toString());

	}
}
