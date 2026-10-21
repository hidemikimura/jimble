package io.jimble.web.auth;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * マルチテナントで、トークンの利用者とリクエストのテナント（Host）を lookup の中で照合する（D-305）
 *
 * <p>
 * テナント A は {@code localhost}、テナント B は {@code 127.0.0.1} で同じサーバーに繋ぐ
 * （HttpClient は Host ヘッダーを書き換えさせないので、名前の違う2つのホストで代える）。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class ApiTokenTenantIntegrationTest {

	/* テナント A の顧客（ID が 100 未満） */
	private static final long CUSTOMER_A = 1;

	/* テナント B の顧客（ID が 100 以上） */
	private static final long CUSTOMER_B = 101;

	/* 止めた顧客（lookup がいつも null を返す） */
	private static final long BANNED = 999;

	private static JimbleServer server;

	@BeforeAll
	static void start () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), ApiTokenTenantIntegrationTest.class);
		SessionStores.reset();

		server = JimbleServer.start(new JimbleApp() {
			{
				before(ApiToken.authenticate((context, realm, id) -> {

					// lookup の中でセッションを触ったときの確かめ用
					if (context.request().path().endsWith("/touch-session")) {
						context.session().put("x", "1");
					}

					if (id == BANNED) {
						return null;
					}

					String hostTenant = tenantOf(context.request().host());
					String userTenant = id < 100 ? "a" : "b";

					// ほかのテナントの顧客なら null（止めた人と同じ 401）
					return userTenant.equals(hostTenant) ? Principal.of(id, "customer-" + userTenant, "member") : null;

				}));
				before(Auth::guard);

				path("/shop", () -> {
					attribute(Auth.REALM, "member");
					attribute(ApiToken.ACCEPT, true);
					get("/api/me", context -> context.response().send(
						Auth.principal(context).name() + " " + Auth.principal(context).id()));
					get("/api/touch-session", context -> context.response().send("touched"));
				});

				error((context, cause, statusCode) -> context.response().code(statusCode).send(
					statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

	}

	/**
	 * Host からテナントを決める（Tenants.byHost の代わり）
	 *
	 * @param host	ホスト
	 * @return	テナント
	 */
	private static String tenantOf (String host) {

		return host != null && host.startsWith("localhost") ? "a" : "b";

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

		for (long id : List.of(CUSTOMER_A, CUSTOMER_B, BANNED)) {
			ApiToken.revokeAll("member", id);
		}

	}

	@Test
	@DisplayName("D-305 同じ種別の顧客のトークンは、自分のテナントのホストでは 200、ほかのテナントのホストでは 401（止めた人と同じ応答）")
	void tokenIsBoundToTenantHost () throws Exception {

		ApiToken.Issued a = ApiToken.issue("member", CUSTOMER_A, "A", Set.of(), Duration.ofDays(1));
		ApiToken.Issued b = ApiToken.issue("member", CUSTOMER_B, "B", Set.of(), Duration.ofDays(1));
		ApiToken.Issued banned = ApiToken.issue("member", BANNED, "止めた", Set.of(), Duration.ofDays(1));

		HttpResponse<String> aOnA = get("localhost", "/shop/api/me", a.token());
		assertEquals(200, aOnA.statusCode(), aOnA.body());
		assertEquals("customer-a 1", aOnA.body());

		HttpResponse<String> bOnB = get("127.0.0.1", "/shop/api/me", b.token());
		assertEquals(200, bOnB.statusCode(), bOnB.body());
		assertEquals("customer-b 101", bOnB.body());

		HttpResponse<String> aOnB = get("127.0.0.1", "/shop/api/me", a.token());
		HttpResponse<String> bOnA = get("localhost", "/shop/api/me", b.token());
		HttpResponse<String> bannedOnA = get("localhost", "/shop/api/me", banned.token());

		assertEquals(401, aOnB.statusCode(), "テナント A のトークンがテナント B で通った");
		assertEquals(401, bOnA.statusCode(), "テナント B のトークンがテナント A で通った");

		// テナントが違うことを応答で区別させない（止めた人と同じ本文・同じ WWW-Authenticate）
		assertEquals(bannedOnA.statusCode(), aOnB.statusCode());
		assertEquals(bannedOnA.body(), aOnB.body());
		assertEquals(bannedOnA.headers().firstValue("www-authenticate"), aOnB.headers().firstValue("www-authenticate"));

	}

	@Test
	@DisplayName("D-305 lookup の中で session() を触ると 500 になり、トークンのリクエストにセッションの Cookie を出さない")
	void sessionInsideLookupIsRejected () throws Exception {

		ApiToken.Issued a = ApiToken.issue("member", CUSTOMER_A, "A", Set.of(), Duration.ofDays(1));

		List<Throwable> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
		io.jimble.util.log.Log.sink((logger, level, message, data, throwable) -> {
			if (throwable != null) {
				errors.add(throwable);
			}
		});

		HttpResponse<String> response;

		try {
			response = get("localhost", "/shop/api/touch-session", a.token());
			Thread.sleep(200);
		} finally {
			io.jimble.util.log.Log.resetSink();
		}

		assertEquals(500, response.statusCode(), response.body());
		assertEquals("サーバーで問題が起きました", response.body(), "中身を返した");
		assertTrue(response.headers().allValues("set-cookie").isEmpty(), "セッションの Cookie を出した: " + response.headers().allValues("set-cookie"));

		/*
		 * 例外は「lookup の中では session() を使えない」と分かる文であること。
		 * 確かめが無いと、あとで保存先を切り替えるところで「before のいちばん先に置いてください」という見当違いの例外になる
		 */
		assertTrue(errors.stream().anyMatch(ApiTokenTenantIntegrationTest::mentionsLookupSession)
			, "lookup の中の session() だと分かる例外になっていません: " + errors);

	}

	private static boolean mentionsLookupSession (Throwable throwable) {

		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			if (String.valueOf(cause.getMessage()).contains("lookup の中では session() を使えません")) {
				return true;
			}
		}

		return false;

	}

	private static HttpResponse<String> get (String host, String path, String token) throws Exception {

		HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + server.port() + path))
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + token)
			.GET()
			.build();

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}

	}

}
