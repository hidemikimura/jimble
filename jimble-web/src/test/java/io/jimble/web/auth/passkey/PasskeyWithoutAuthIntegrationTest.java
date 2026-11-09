package io.jimble.web.auth.passkey;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.web.auth.Auth;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.session.SessionStores;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth を使わずにログインを自分で持つアプリの、パスキーの口
 *
 * <p>
 * アプリは種別・利用者 ID・サイトを自分で決めて渡す。{@code authenticate} は確かめて利用者 ID を返すだけで、
 * {@code Auth.login} を呼ばない。
 * </p>
 */
@Tag("db")
class PasskeyWithoutAuthIntegrationTest {

	private static final long USER_ID = 11;

	private static final String REALM = "system";

	/* UP + UV */
	private static final int OK = 0x01 | 0x04;

	/* UP だけ（本人確認なし） */
	private static final int NO_UV = 0x01;

	private static final PasskeyRp CLIENT_A = PasskeyRp.of("client-a.localhost", "クライアント A", List.of("http://client-a.localhost"));

	private static final PasskeyRp CLIENT_B = PasskeyRp.of("client-b.localhost", "クライアント B", List.of("http://client-b.localhost"));

	private static final String ORIGIN_A = "http://client-a.localhost";

	private static Config originalConf;

	private static JimbleServer server;

	@BeforeAll
	static void start () {

		Conf.reload();
		originalConf = Conf.conf().config();

		DBUtil.load(Conf.conf().config(), PasskeyWithoutAuthIntegrationTest.class);
		SessionStores.reset();

		// Auth::guard を置かない（ログインはアプリが自分で持つ）
		server = JimbleServer.start(new JimbleApp() {
			{
				post("/own/passkey/register/options", c -> {
					Data body = c.request().bodyJson();
					c.response().json(Passkey.registrationOptions(c, rp(body), body.getString("realm"), body.getLong("userId"), "kimura"));
				});
				post("/own/passkey/register", c -> {
					Data body = c.request().bodyJson();
					Passkey.register(c, rp(body), body.getString("realm"), body.getLong("userId"), body.getDataOptional("credential"), "ノート PC");
					c.response().json("ok", true);
				});

				post("/own/passkey/login/options", c -> {
					Data body = c.request().bodyJson();
					c.response().json(Passkey.loginOptions(c, rp(body), body.getString("realm")));
				});
				post("/own/passkey/login", c -> {
					Data body = c.request().bodyJson();
					OptionalLong userId = Passkey.authenticate(c, rp(body), body.getString("realm"), body.getDataOptional("credential"));
					c.response().json(new Data()
						.putData("userId", userId.orElse(0))
						.putData("principal", Auth.principal(c).id()));
				});

				// チャレンジを出してから時間が経ったことにする
				post("/own/age", c -> {
					c.session().put("__passkey_issued_at", System.currentTimeMillis() / 1000 - 3600);
					c.session().save();
					c.response().json("ok", true);
				});

				get("/me", c -> c.response().send(String.valueOf(Auth.principal(c).id())));

				error((context, cause, statusCode) -> context.response().code(statusCode).json("error"
					, statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

	}

	private static PasskeyRp rp (Data body) {

		return "b".equals(body.getStringOptional("rp")) ? CLIENT_B : CLIENT_A;

	}

	@AfterAll
	static void stop () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		Passkey.deleteAll(REALM, USER_ID);
		Passkey.deleteAll("", USER_ID);
		Passkey.deleteAll("admin", USER_ID);

	}

	@Test
	@DisplayName("登録して確かめると、登録した利用者の ID が返る。Auth.login は呼ばない（未ログインのまま）")
	void registerAndAuthenticate () throws Exception {

		FakeAuthenticator authenticator = register(new Browser(), REALM, USER_ID, "a");

		List<Data> list = Passkey.list(REALM, CLIENT_A, USER_ID);
		assertEquals(1, list.size());
		assertEquals("ノート PC", list.get(0).getString("label"));

		Browser browser = new Browser();
		Data result = login(browser, authenticator, REALM, "a", OK, ORIGIN_A);

		assertEquals(USER_ID, result.getLong("userId"));
		assertEquals(0, result.getLong("principal"), "Auth.login を呼んでいる");
		assertEquals("0", browser.get("/me").body(), "Auth のログインが次の要求に残っている");
		assertTrue(Passkey.list(REALM, CLIENT_A, USER_ID).get(0).getLong("last_used_at") > 0, "last_used_at を進めていない");

	}

	@Test
	@DisplayName("登録済みのものは excludeCredentials に入る。2つ目も同じ利用者のハンドル")
	void excludeCredentials () throws Exception {

		FakeAuthenticator first = register(new Browser(), REALM, USER_ID, "a");

		Data options = new Browser().postJson("/own/passkey/register/options", subject(REALM, USER_ID, "a"));

		assertEquals(1, options.getDataList("excludeCredentials").size());
		assertEquals(first.userHandle, options.getDataOptional("user").getString("id"));

	}

	@Test
	@DisplayName("options を別の種別・別の利用者・別のサイトで出した手続きでは、register が断る（400）")
	void registerRejectsOtherSubject () throws Exception {

		List<Data> others = List.of(subject("admin", USER_ID, "a"), subject(REALM, USER_ID + 1, "a"), subject(REALM, USER_ID, "b"));

		for (Data other : others) {

			Browser browser = new Browser();
			FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);

			Data options = browser.postJson("/own/passkey/register/options", other);
			authenticator.userHandle = options.getDataOptional("user").getString("id");

			Data credential = authenticator.create(options.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK);
			HttpResponse<String> response = browser.post("/own/passkey/register", subject(REALM, USER_ID, "a").putData("credential", credential));

			assertEquals(400, response.statusCode(), other + " の手続きで登録できた: " + response.body());

		}

		assertEquals(0, Passkey.list(REALM, null, USER_ID).size());
		assertEquals(0, Passkey.list(REALM, null, USER_ID + 1).size());

	}

	@Test
	@DisplayName("同じチャレンジの2回目は空。Auth の口と違い 400 にしない")
	void replay () throws Exception {

		FakeAuthenticator authenticator = register(new Browser(), REALM, USER_ID, "a");

		Browser browser = new Browser();
		Data options = browser.postJson("/own/passkey/login/options", subject(REALM, 0, "a"));
		Data credential = authenticator.get(options.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK);

		assertEquals(USER_ID, authenticate(browser, REALM, "a", credential).getLong("userId"));
		assertEquals(0, authenticate(browser, REALM, "a", credential).getLong("userId"), "同じチャレンジで2度通った");

		// セッションを写して送り直しても（cookie のセッションなら、チャレンジが戻ってくる）通らない
		Browser other = new Browser();
		Data again = other.postJson("/own/passkey/login/options", subject(REALM, 0, "a"));
		Data second = authenticator.get(again.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK);
		Browser copied = other.copy();
		assertEquals(USER_ID, authenticate(other, REALM, "a", second).getLong("userId"));
		assertEquals(0, authenticate(copied, REALM, "a", second).getLong("userId"));

	}

	@Test
	@DisplayName("時間切れ・オリジン違い・本人確認（UV）なし・手続きの無い要求は空")
	void rejected () throws Exception {

		FakeAuthenticator authenticator = register(new Browser(), REALM, USER_ID, "a");

		// 時間切れ
		Browser expired = new Browser();
		Data options = expired.postJson("/own/passkey/login/options", subject(REALM, 0, "a"));
		expired.postJson("/own/age", new Data());
		assertEquals(0, authenticate(expired, REALM, "a"
			, authenticator.get(options.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK)).getLong("userId"));

		assertEquals(0, login(new Browser(), authenticator, REALM, "a", OK, "http://evil.localhost").getLong("userId"), "オリジン違いで通った");
		assertEquals(0, login(new Browser(), authenticator, REALM, "a", NO_UV, ORIGIN_A).getLong("userId"), "UV なしで通った");

		// options を出していない
		assertEquals(0, authenticate(new Browser(), REALM, "a"
			, authenticator.get(options.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK)).getLong("userId"));

		// 落ちたものは last_used_at を進めない
		assertEquals(0, Passkey.list(REALM, CLIENT_A, USER_ID).get(0).getLong("last_used_at"));

	}

	@Test
	@DisplayName("別のサイト（rp_id）・別の種別で登録した資格情報は空")
	void otherRpAndRealm () throws Exception {

		FakeAuthenticator authenticator = register(new Browser(), REALM, USER_ID, "a");

		// B のサイトの手続きで、A の資格情報を B の rp_id で署名させても通らない
		assertEquals(0, login(new Browser(), authenticator, REALM, "b", OK, "http://client-b.localhost").getLong("userId"));

		// 別の種別の手続き
		assertEquals(0, login(new Browser(), authenticator, "admin", "a", OK, ORIGIN_A).getLong("userId"));

		// options は A、確かめるのは B（つなぎ間違い）
		Browser mixed = new Browser();
		Data options = mixed.postJson("/own/passkey/login/options", subject(REALM, 0, "a"));
		assertEquals(0, authenticate(mixed, REALM, "b"
			, authenticator.get(options.getString("challenge"), ORIGIN_A, CLIENT_A.id(), OK)).getLong("userId"));

	}

	@Test
	@DisplayName("種別の書式・利用者 ID・名前・サイトが正しくなければ例外（Auth.checkRealm と同じ決まり）")
	void validation () {

		try (WebContext context = new WebContext(new io.jimble.web.support.Fakes.FakeRequestSource("POST", "/"), new io.jimble.web.support.Fakes.FakeResponseSink())) {

			assertThrows(IllegalArgumentException.class, () -> Passkey.registrationOptions(context, CLIENT_A, "bad realm", USER_ID, "kimura"));
			assertThrows(IllegalArgumentException.class, () -> Passkey.registrationOptions(context, CLIENT_A, null, USER_ID, "kimura"));
			assertThrows(IllegalArgumentException.class, () -> Passkey.registrationOptions(context, CLIENT_A, REALM, 0, "kimura"));
			assertThrows(IllegalArgumentException.class, () -> Passkey.registrationOptions(context, CLIENT_A, REALM, USER_ID, " "));
			assertThrows(IllegalArgumentException.class, () -> Passkey.register(context, null, REALM, USER_ID, new Data(), ""));
			assertThrows(IllegalArgumentException.class, () -> Passkey.loginOptions(context, CLIENT_A, "a:b"));
			assertThrows(IllegalArgumentException.class, () -> Passkey.authenticate(context, CLIENT_A, "a/b", new Data()));

		}

	}

	// region 小物

	private static Data subject (String realm, long userId, String rp) {

		return new Data().putData("realm", realm).putData("userId", userId).putData("rp", rp);

	}

	/**
	 * パスキーを登録する（アプリが自分のログインで本人を確かめた、という前提）
	 */
	private static FakeAuthenticator register (Browser browser, String realm, long userId, String rp) throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);
		PasskeyRp site = "b".equals(rp) ? CLIENT_B : CLIENT_A;

		Data options = browser.postJson("/own/passkey/register/options", subject(realm, userId, rp));
		assertEquals(site.id(), options.getDataOptional("rp").getString("id"));
		authenticator.userHandle = options.getDataOptional("user").getString("id");

		Data credential = authenticator.create(options.getString("challenge"), site.origins().get(0), site.id(), OK);
		HttpResponse<String> response = browser.post("/own/passkey/register", subject(realm, userId, rp).putData("credential", credential));

		assertEquals(200, response.statusCode(), response.body());

		return authenticator;

	}

	/**
	 * ログインの options を取り、認証器に答えさせて確かめる
	 */
	private static Data login (Browser browser, FakeAuthenticator authenticator, String realm, String rp, int flags, String origin) throws Exception {

		PasskeyRp site = "b".equals(rp) ? CLIENT_B : CLIENT_A;

		Data options = browser.postJson("/own/passkey/login/options", subject(realm, 0, rp));
		assertEquals(site.id(), options.getString("rpId"));

		return authenticate(browser, realm, rp, authenticator.get(options.getString("challenge"), origin, site.id(), flags));

	}

	private static Data authenticate (Browser browser, String realm, String rp, Data credential) throws Exception {

		return browser.postJson("/own/passkey/login", subject(realm, 0, rp).putData("credential", credential));

	}

	/**
	 * Cookie を持ち回るだけのブラウザ
	 */
	private static final class Browser {

		private final List<String> cookies = new ArrayList<>();

		Browser copy () {

			Browser copied = new Browser();
			copied.cookies.addAll(cookies);
			return copied;

		}

		HttpResponse<String> get (String path) throws Exception {

			return send(HttpRequest.newBuilder(uri(path)).GET());

		}

		HttpResponse<String> post (String path, Data body) throws Exception {

			return send(HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(Dson.encodes(body))));

		}

		Data postJson (String path, Data body) throws Exception {

			HttpResponse<String> response = post(path, body);
			assertEquals(200, response.statusCode(), response.body());
			return Dson.decodes(response.body(), Data.class);

		}

		private HttpResponse<String> send (HttpRequest.Builder builder) throws Exception {

			builder.timeout(Duration.ofSeconds(10));

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

	}

	// endregion

}
