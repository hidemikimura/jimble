package io.jimble.web.auth;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.session.SessionStores;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API のトークン（D-264）を、サーバーを立てて確かめる
 */
@Tag("db")
class ApiTokenIntegrationTest {

	private static final long MEMBER = 7;

	private static final long ADMIN = 8;

	/* lookup が null を返す（止めた利用者） */
	private static final long BANNED = 9;

	private static JimbleServer server;

	@BeforeAll
	static void start () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), ApiTokenIntegrationTest.class);
		SessionStores.reset();

		server = JimbleServer.start(new JimbleApp() {
			{
				before(ApiToken.authenticate(id -> id == BANNED ? null : Principal.of(id, "kimura", id == ADMIN ? "admin" : "member")));
				before(Csrf::verify);
				before(Auth::guard);

				get("/test/login", context -> {
					Auth.login(context, Principal.of(MEMBER, "kimura", "member"));
					context.response().send(Csrf.token(context));
				}).attribute(Auth.PUBLIC, true);

				get("/page", context -> context.response().send("page"));

				path("/api", () -> {
					attribute(ApiToken.ACCEPT, true);

					get("/requests", context -> context.response().send(
						Auth.principal(context).id() + " " + String.join(",", ApiToken.scopes(context))))
						.attribute(ApiToken.SCOPE, "requests:read");
					post("/requests", context -> context.response().send("created"))
						.attribute(ApiToken.SCOPE, "requests:write");
					get("/any", context -> context.response().send(String.valueOf(Auth.principal(context).id())));
					get("/admin", context -> context.response().send("admin")).attribute(Auth.ROLE, "admin");
					post("/password", context -> context.response().send("changed")).attribute(Auth.FULL_AUTH, true);
				});

				// セッションをまったく使わない API（トークンの確かめは、ここでも同じに効く）
				path("/stateless", () -> {
					attribute(ApiToken.ACCEPT, true);
					attribute(Auth.NO_SESSION, true);
					get("/any", context -> context.response().send(String.valueOf(Auth.principal(context).id())));
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

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		for (long id : List.of(MEMBER, ADMIN, BANNED)) {
			ApiToken.revokeAll("", id);
		}

	}

	@Test
	@DisplayName("トークンで入れて、スコープが見える。Cookie は出さない。スコープが足りなければ 403（insufficient_scope）")
	void scopes () throws Exception {

		ApiToken.Issued read = ApiToken.issue(MEMBER, "読むだけ", Set.of("requests:read"), Duration.ofDays(30));

		assertTrue(read.token().startsWith("jbt_"));
		assertFalse(read.toString().contains(read.token()), "toString にトークンが出ている");

		HttpResponse<String> list = get("/api/requests", read.token(), new ArrayList<>());
		assertEquals(200, list.statusCode(), list.body());
		assertEquals(MEMBER + " requests:read", list.body());
		assertTrue(list.headers().allValues("set-cookie").isEmpty(), "トークンのリクエストに Cookie を出した: " + list.headers().allValues("set-cookie"));

		HttpResponse<String> create = post("/api/requests", read.token(), new ArrayList<>(), null);
		assertEquals(403, create.statusCode());
		assertTrue(create.headers().firstValue("www-authenticate").orElse("").contains("insufficient_scope"));

		// 書けるトークンなら、CSRF トークンが無くても通る（ブラウザは Authorization を勝手に付けない）
		ApiToken.Issued write = ApiToken.issue(MEMBER, "書く", Set.of("requests:read", "requests:write"), Duration.ofDays(30));
		assertEquals(200, post("/api/requests", write.token(), new ArrayList<>(), null).statusCode());

	}

	@Test
	@DisplayName("ACCEPT の無いルートに Bearer が来たら 401。違う・形の崩れたトークンも 401（invalid_token）")
	void rejects () throws Exception {

		ApiToken.Issued token = ApiToken.issue(MEMBER, "x", Set.of(), Duration.ofDays(1));

		assertEquals(401, get("/page", token.token(), new ArrayList<>()).statusCode(), "トークンを受けないルートに入れた");

		HttpResponse<String> wrong = get("/api/any", "jbt_" + "A".repeat(43), new ArrayList<>());
		assertEquals(401, wrong.statusCode());
		assertTrue(wrong.headers().firstValue("www-authenticate").orElse("").contains("invalid_token"));

		assertEquals(401, get("/api/any", "abc", new ArrayList<>()).statusCode());
		assertEquals(200, get("/api/any", token.token(), new ArrayList<>()).statusCode());

	}

	@Test
	@DisplayName("期限切れ・消したトークンは 401。Auth.revoke のあとは、それより前のトークンが止まり、新しく発行したものは使える")
	void expiryAndRevocation () throws Exception {

		ApiToken.Issued expired = ApiToken.issue(MEMBER, "期限切れ", Set.of(), Duration.ofDays(1));
		DBUtil.getMainDB().update("UPDATE auth_api_token SET expires_at = ? WHERE id = ?", System.currentTimeMillis() / 1000 - 1, expired.id());
		assertEquals(401, get("/api/any", expired.token(), new ArrayList<>()).statusCode());

		ApiToken.Issued deleted = ApiToken.issue(MEMBER, "消す", Set.of(), Duration.ZERO);
		assertTrue(ApiToken.revoke("", MEMBER, deleted.id()));
		assertFalse(ApiToken.revoke("", ADMIN, deleted.id()), "ほかの人のものを消せた");
		assertEquals(401, get("/api/any", deleted.token(), new ArrayList<>()).statusCode());

		ApiToken.Issued before = ApiToken.issue(MEMBER, "締め出す前", Set.of(), Duration.ZERO);
		assertEquals(200, get("/api/any", before.token(), new ArrayList<>()).statusCode());
		assertEquals(String.valueOf(MEMBER), get("/stateless/any", before.token(), new ArrayList<>()).body(), "NO_SESSION のルートでトークンが使えない");

		Auth.revoke(MEMBER);
		Revocations.clearCache();

		assertEquals(401, get("/api/any", before.token(), new ArrayList<>()).statusCode(), "締め出したのにトークンで入れた");
		// NO_SESSION のルートでは Auth.guard が世代を比べないので、トークンの側で止まっていること
		assertEquals(401, get("/stateless/any", before.token(), new ArrayList<>()).statusCode(), "NO_SESSION のルートで、締め出したのにトークンで入れた");

		ApiToken.Issued after = ApiToken.issue(MEMBER, "締め出したあと", Set.of(), Duration.ZERO);
		assertEquals(200, get("/api/any", after.token(), new ArrayList<>()).statusCode());

	}

	@Test
	@DisplayName("止めた利用者（lookup が null）は 401。役割が足りなければ 403。FULL_AUTH のルートには入れない（401）")
	void principalRules () throws Exception {

		assertEquals(401, get("/api/any", ApiToken.issue(BANNED, "x", Set.of(), Duration.ZERO).token(), new ArrayList<>()).statusCode());

		ApiToken.Issued member = ApiToken.issue(MEMBER, "x", Set.of(), Duration.ZERO);
		assertEquals(403, get("/api/admin", member.token(), new ArrayList<>()).statusCode());
		assertEquals(200, get("/api/admin", ApiToken.issue(ADMIN, "x", Set.of(), Duration.ZERO).token(), new ArrayList<>()).statusCode());

		assertEquals(401, post("/api/password", member.token(), new ArrayList<>(), null).statusCode(), "トークンでパスワードの変更に入れた");

	}

	@Test
	@DisplayName("セッションで入った人はそのまま（スコープは関係ない・CSRF は要る）。一覧にトークンは出ず、最後に使った時刻が残る")
	void sessionAndList () throws Exception {

		List<String> cookies = new ArrayList<>();
		String csrf = get("/test/login", null, cookies).body();

		assertEquals(String.valueOf(MEMBER), get("/api/any", null, cookies).body());
		assertEquals(200, get("/api/requests", null, cookies).statusCode(), "セッションの人にスコープを求めた");
		assertEquals(403, post("/api/requests", null, cookies, null).statusCode(), "セッションの POST で CSRF を見ていない");
		assertEquals(200, post("/api/requests", null, cookies, csrf).statusCode());

		ApiToken.Issued token = ApiToken.issue(MEMBER, "一覧", List.of("Requests:Read"), Duration.ofDays(7));
		get("/api/any", token.token(), new ArrayList<>());

		List<Data> list = ApiToken.list("", MEMBER);
		assertEquals(1, list.size());
		assertEquals("一覧", list.get(0).getString("name"));
		assertEquals(List.of("requests:read"), list.get(0).getStringList("scopes"));
		assertTrue(list.get(0).getLong("last_used_at") > 0);
		assertTrue(list.get(0).getLong("expires_at") > list.get(0).getLong("created_at"));
		assertFalse(list.toString().contains(token.token()));

		assertThrows(IllegalArgumentException.class, () -> ApiToken.issue(MEMBER, "x", Set.of("requests read"), Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> ApiToken.issue(MEMBER, "x", Set.of(), Duration.ofDays(-1)));

	}

	// region 小物

	private static HttpResponse<String> get (String path, String token, List<String> cookies) throws Exception {

		return send(HttpRequest.newBuilder(uri(path)).GET(), token, cookies, null);

	}

	private static HttpResponse<String> post (String path, String token, List<String> cookies, String csrf) throws Exception {

		return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()), token, cookies, csrf);

	}

	private static HttpResponse<String> send (HttpRequest.Builder builder, String token, List<String> cookies, String csrf) throws Exception {

		builder.timeout(Duration.ofSeconds(10));

		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}

		if (csrf != null) {
			builder.header(Csrf.HEADER_NAME, csrf);
		}

		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ", cookies));
		}

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {

			HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

			for (String header : response.headers().allValues("set-cookie")) {
				String pair = header.split(";", 2)[0];
				String name = pair.split("=", 2)[0];
				cookies.removeIf(cookie -> cookie.startsWith(name + "="));
				if (!pair.endsWith("=")) {
					cookies.add(pair);
				}
			}

			return response;

		}

	}

	private static URI uri (String path) {

		return URI.create("http://127.0.0.1:" + server.port() + path);

	}

	// endregion

}
