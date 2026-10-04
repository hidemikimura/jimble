package io.jimble.web.auth.passkey;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.http.HttpException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスキーの登録とログインを、サーバーを立てて通しで確かめる（D-261）
 */
@Tag("db")
class PasskeyIntegrationTest {

	private static final long USER_ID = 7;

	private static final String RP_ID = "localhost";

	private static final String ORIGIN = "http://localhost";

	/* UP + UV */
	private static final int OK = 0x01 | 0x04;

	/* クライアントごとのサイト（SaaS。D-262） */
	private static final PasskeyRp CLIENT_A = PasskeyRp.of("client-a.localhost", "クライアント A", List.of("http://client-a.localhost"));

	private static final PasskeyRp CLIENT_B = PasskeyRp.of("client-b.localhost", "クライアント B", List.of("http://client-b.localhost"));

	private static Config originalConf;

	private static JimbleServer server;

	@BeforeAll
	static void start () {

		Conf.reload();
		originalConf = Conf.conf().config();

		Conf.replace(ConfigFactory.parseString("""
			auth.passkey {
				rp_id   = "%s"
				origins = ["%s"]
			}
			""".formatted(RP_ID, ORIGIN)).withFallback(originalConf));

		DBUtil.load(Conf.conf().config(), PasskeyIntegrationTest.class);
		SessionStores.reset();

		server = JimbleServer.start(new JimbleApp() {
			{
				before(Auth::guard);

				get("/test/login", context -> {
					Auth.login(context, Principal.of(USER_ID, "kimura"));
					context.response().send("ok");
				}).attribute(Auth.PUBLIC, true);

				get("/me", context -> context.response().send(String.valueOf(Auth.principal(context).id())))
					.attribute(Auth.PUBLIC, true);

				post("/passkey/register/options", context -> context.response().json(Passkey.registrationOptions(context, "kimura")))
					.attribute(Auth.FULL_AUTH, true);
				post("/passkey/register", context -> {
					Passkey.register(context, context.request().bodyJson(), "ノート PC");
					context.response().json("ok", true);
				}).attribute(Auth.FULL_AUTH, true);

				post("/passkey/login/options", context -> context.response().json(Passkey.loginOptions(context)))
					.attribute(Auth.PUBLIC, true);
				post("/passkey/login", context -> {
					if (!Passkey.login(context, context.request().bodyJson(), id -> Principal.of(id, "kimura"))) {
						throw new HttpException(401, "パスキーでログインできませんでした");
					}
					context.response().json("ok", true);
				}).attribute(Auth.PUBLIC, true);

				get("/passkey.js", Passkey.script()).attribute(Auth.PUBLIC, true);

				// クライアントごとにサイトを渡す（本物のアプリはクライアントの表から引く）
				for (String client : List.of("a", "b")) {
					PasskeyRp rp = client.equals("a") ? CLIENT_A : CLIENT_B;
					post("/" + client + "/passkey/register/options", context -> context.response().json(Passkey.registrationOptions(context, rp, "kimura")))
						.attribute(Auth.FULL_AUTH, true);
					post("/" + client + "/passkey/register", context -> {
						Passkey.register(context, rp, context.request().bodyJson(), client);
						context.response().json("ok", true);
					}).attribute(Auth.FULL_AUTH, true);
					post("/" + client + "/passkey/login/options", context -> context.response().json(Passkey.loginOptions(context, rp)))
						.attribute(Auth.PUBLIC, true);
					post("/" + client + "/passkey/login", context -> {
						if (!Passkey.login(context, rp, context.request().bodyJson(), id -> Principal.of(id, "kimura"))) {
							throw new HttpException(401, "パスキーでログインできませんでした");
						}
						context.response().json("ok", true);
					}).attribute(Auth.PUBLIC, true);
				}

				// options はクライアント A、確かめるのはクライアント B（つなぎ間違い）
				post("/mixed/passkey/login", context -> {
					if (!Passkey.login(context, CLIENT_B, context.request().bodyJson(), id -> Principal.of(id, "kimura"))) {
						throw new HttpException(401, "パスキーでログインできませんでした");
					}
					context.response().json("ok", true);
				}).attribute(Auth.PUBLIC, true);

				error((context, cause, statusCode) -> context.response().code(statusCode).json("error"
					, statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));
			}
		}, 0);

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

		Passkey.deleteAll("", USER_ID);

	}

	@Test
	@DisplayName("登録して、ログイン ID なしでパスキーだけでログインできる。同じ応答はもう使えない")
	void registerAndLogin () throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);
		register(authenticator);

		List<Data> list = Passkey.list(USER_ID);
		assertEquals(1, list.size());
		assertEquals("ノート PC", list.get(0).getString("label"));

		// 別のブラウザ（何も持っていない）
		Browser other = new Browser();
		Data options = other.postJson("/passkey/login/options", new Data());

		Data credential = authenticator.get(options.getString("challenge"), ORIGIN, RP_ID, OK);
		HttpResponse<String> login = other.post("/passkey/login", credential);

		assertEquals(200, login.statusCode(), login.body());
		assertEquals(String.valueOf(USER_ID), other.get("/me").body(), "パスキーでログインできていない");

		// 同じ応答をもう一度（チャレンジは1度しか使えない）
		assertEquals(400, other.post("/passkey/login", credential).statusCode());

		assertTrue(Passkey.list(USER_ID).get(0).getLong("last_used_at") > 0);

	}

	@Test
	@DisplayName("D-290 cookie のセッションでも、ログイン前の Cookie と同じ応答を送り直して入れない（チャレンジは DB で1度きり）")
	void replayWithCookieSession () throws Exception {

		Conf.replace(ConfigFactory.parseString("""
			session.store = "cookie"
			session.secret = "passkey-replay-test-secret"
			""").withFallback(Conf.conf().config()));
		SessionStores.reset();

		try {

			FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);
			register(authenticator);

			Browser victim = new Browser();
			Data options = victim.postJson("/passkey/login/options", new Data());

			// ログインの前の Cookie（チャレンジが入っている）を取られた
			Browser attacker = victim.copy();

			Data credential = authenticator.get(options.getString("challenge"), ORIGIN, RP_ID, OK);
			assertEquals(200, victim.post("/passkey/login", credential).statusCode());

			// 同じ要求（Cookie と本文）を送り直す
			HttpResponse<String> replay = attacker.post("/passkey/login", credential);

			assertEquals(400, replay.statusCode(), "取られた要求の送り直しでログインできた: " + replay.body());
			assertEquals("0", attacker.get("/me").body());

		} finally {
			Conf.replace(Conf.conf().config().withoutPath("session.store").withoutPath("session.secret").withFallback(originalConf));
			SessionStores.reset();
		}

	}

	@Test
	@DisplayName("署名が違えば、ログインさせない（401）")
	void wrongSignature () throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);
		register(authenticator);

		FakeAuthenticator impostor = new FakeAuthenticator(CoseKey.ES256, 0);

		Browser other = new Browser();
		Data options = other.postJson("/passkey/login/options", new Data());
		Data credential = authenticator.get(options.getString("challenge"), ORIGIN, RP_ID, OK, impostor.keys.getPrivate());

		assertEquals(401, other.post("/passkey/login", credential).statusCode());
		assertEquals("0", other.get("/me").body());

	}

	@Test
	@DisplayName("同じ認証器は2度登録できない（409）。消したパスキーではログインできない")
	void duplicateAndDelete () throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.EDDSA, 0);
		Browser browser = register(authenticator);

		Data options = browser.postJson("/passkey/register/options", new Data());
		assertEquals(1, options.getDataList("excludeCredentials").size(), "登録済みのものを excludeCredentials に入れていない");
		assertEquals(409, browser.post("/passkey/register"
			, authenticator.create(options.getString("challenge"), ORIGIN, RP_ID, OK)).statusCode());

		String id = Passkey.list(USER_ID).get(0).getString("id");
		assertTrue(!Passkey.delete(USER_ID + 1, id), "ほかの人のパスキーを消せた");
		assertTrue(Passkey.delete(USER_ID, id));

		Browser other = new Browser();
		Data login = other.postJson("/passkey/login/options", new Data());
		assertEquals(401, other.post("/passkey/login"
			, authenticator.get(login.getString("challenge"), ORIGIN, RP_ID, OK)).statusCode());

	}

	@Test
	@DisplayName("2つ目のパスキーも同じ利用者のハンドルで登録する。ログインしていなければ登録の options は出さない")
	void secondPasskeyAndAnonymous () throws Exception {

		FakeAuthenticator first = new FakeAuthenticator(CoseKey.ES256, 0);
		Browser browser = register(first);

		Data options = browser.postJson("/passkey/register/options", new Data());
		assertEquals(first.userHandle, options.getDataOptional("user").getString("id"));

		assertEquals(401, new Browser().post("/passkey/register/options", new Data()).statusCode());

	}

	@Test
	@DisplayName("D-262 クライアントごとのサイト：A で登録したパスキーは A では使え、B では使えない")
	void perClientRp () throws Exception {

		FakeAuthenticator authenticator = new FakeAuthenticator(CoseKey.ES256, 0);

		Browser browser = new Browser();
		browser.get("/test/login");

		Data options = browser.postJson("/a/passkey/register/options", new Data());
		assertEquals("client-a.localhost", options.getDataOptional("rp").getString("id"));
		authenticator.userHandle = options.getDataOptional("user").getString("id");

		HttpResponse<String> registered = browser.post("/a/passkey/register"
			, authenticator.create(options.getString("challenge"), "http://client-a.localhost", "client-a.localhost", OK));
		assertEquals(200, registered.statusCode(), registered.body());

		assertEquals(1, Passkey.list("", CLIENT_A, USER_ID).size());
		assertEquals(0, Passkey.list("", CLIENT_B, USER_ID).size());
		assertEquals("client-a.localhost", Passkey.list(USER_ID).get(0).getString("rp_id"));

		// A でログインできる
		Browser a = new Browser();
		Data loginA = a.postJson("/a/passkey/login/options", new Data());
		assertEquals("client-a.localhost", loginA.getString("rpId"));
		assertEquals(200, a.post("/a/passkey/login"
			, authenticator.get(loginA.getString("challenge"), "http://client-a.localhost", "client-a.localhost", OK)).statusCode());

		// B では、同じ資格情報で（B の rp_id に署名させても）入れない
		Browser b = new Browser();
		Data loginB = b.postJson("/b/passkey/login/options", new Data());
		assertEquals(401, b.post("/b/passkey/login"
			, authenticator.get(loginB.getString("challenge"), "http://client-b.localhost", "client-b.localhost", OK)).statusCode());
		assertEquals("0", b.get("/me").body());

		// A の options のあとに B で確かめると、手続きのやり直し（400）
		Browser mixed = new Browser();
		Data loginMixed = mixed.postJson("/a/passkey/login/options", new Data());
		assertEquals(400, mixed.post("/mixed/passkey/login"
			, authenticator.get(loginMixed.getString("challenge"), "http://client-a.localhost", "client-a.localhost", OK)).statusCode());

	}

	@Test
	@DisplayName("ブラウザ側の JS を返す")
	void script () throws Exception {

		HttpResponse<String> response = new Browser().get("/passkey.js");

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/javascript"));
		assertTrue(response.body().contains("JimblePasskey"));

	}

	// region 小物

	/**
	 * ログインして、パスキーを登録する
	 */
	private static Browser register (FakeAuthenticator authenticator) throws Exception {

		Browser browser = new Browser();
		browser.get("/test/login");

		Data options = browser.postJson("/passkey/register/options", new Data());
		authenticator.userHandle = options.getDataOptional("user").getString("id");

		HttpResponse<String> response = browser.post("/passkey/register"
			, authenticator.create(options.getString("challenge"), ORIGIN, RP_ID, OK));

		assertEquals(200, response.statusCode(), response.body());

		return browser;

	}

	/**
	 * Cookie を持ち回るだけのブラウザ
	 */
	private static final class Browser {

		private final List<String> cookies = new ArrayList<>();

		/** いまの Cookie を写した、別のブラウザ（取られた Cookie のつもり） */
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
