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

/**
 * 管理画面と顧客で、API のトークンの利用者を種別ごとに引き分ける（D-303）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class ApiTokenRealmIntegrationTest {

	/* 管理者と顧客で、同じ ID を使う */
	private static final long SAME_ID = 1;

	private static JimbleServer server;

	@BeforeAll
	static void start () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), ApiTokenRealmIntegrationTest.class);
		SessionStores.reset();

		server = JimbleServer.start(new JimbleApp() {
			{
				// 種別ごとに、別の表から引く形
				before(ApiToken.authenticate((realm, id) -> switch (realm) {
					case "operator" -> Principal.of(id, "staff", "admin");
					case "member" -> Principal.of(id, "customer", "member");
					default -> null;
				}));
				before(Auth::guard);

				path("/admin", () -> {
					attribute(Auth.REALM, "operator");
					attribute(ApiToken.ACCEPT, true);
					get("/api/me", context -> context.response().send(whoami(context)));
				});

				path("/shop", () -> {
					attribute(Auth.REALM, "member");
					attribute(ApiToken.ACCEPT, true);
					get("/api/me", context -> context.response().send(whoami(context)));
				});

				error((context, cause, statusCode) -> context.response().code(statusCode).send(
					statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

	}

	private static String whoami (io.jimble.web.context.WebContext context) {

		Principal principal = Auth.principal(context);

		return principal.name() + " " + principal.id() + " " + principal.role();

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

		ApiToken.revokeAll("operator", SAME_ID);
		ApiToken.revokeAll("member", SAME_ID);

	}

	@Test
	@DisplayName("D-303 同じ ID 1 でも、管理画面のトークンは管理者、顧客のトークンは顧客として引く。種別の違うルートには入れない")
	void lookupByRealm () throws Exception {

		ApiToken.Issued staff = ApiToken.issue("operator", SAME_ID, "管理", Set.of(), Duration.ofDays(1));
		ApiToken.Issued customer = ApiToken.issue("member", SAME_ID, "顧客", Set.of(), Duration.ofDays(1));

		HttpResponse<String> admin = get("/admin/api/me", staff.token());
		assertEquals(200, admin.statusCode(), admin.body());
		assertEquals("staff 1 admin", admin.body());

		HttpResponse<String> shop = get("/shop/api/me", customer.token());
		assertEquals(200, shop.statusCode(), shop.body());
		assertEquals("customer 1 member", shop.body());

		// 顧客のトークンで管理画面には入れない（逆も）
		assertEquals(401, get("/admin/api/me", customer.token()).statusCode());
		assertEquals(401, get("/shop/api/me", staff.token()).statusCode());

		// 一覧も種別ごと
		assertEquals(List.of("管理"), ApiToken.list("operator", SAME_ID).stream().map(row -> row.getString("name")).toList());
		assertEquals(List.of("顧客"), ApiToken.list("member", SAME_ID).stream().map(row -> row.getString("name")).toList());

	}

	private static HttpResponse<String> get (String path, String token) throws Exception {

		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + token)
			.GET()
			.build();

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}

	}

}
