package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Router;
import io.jimble.web.session.CookieSessionStore;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DB が無いときの締め出し（要件 F-W-33）
 *
 * <p>
 * <b>{@code revoke} は投げ、{@code guard} は比べない。</b>
 * 締め出せない構成で「締め出したつもり」を作らないことと、
 * そのうえで<b>ログインそのものは今までどおり通す</b>ことを固定する。
 * </p>
 */
class AuthRevocationNoDbTest {

	/* 元の設定 */
	private Config originalConf;

	@BeforeEach
	void cookieSession () {

		assumeFalse(DBUtil.isUseDB(), "この JVM では DB が読み込まれている");

		Conf.reload();
		originalConf = Conf.conf().config();

		Conf.replace(ConfigFactory.parseString("""
			session.store = "cookie"
			session.secret = "no-db-secret"
			""").withFallback(originalConf));

		SessionStores.reset();

	}

	@AfterEach
	void restore () {

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

		SessionStores.reset();

	}

	@Test
	@DisplayName("F-W-33 DB が無ければ revoke は投げる（締め出したつもりにさせない）")
	void revokeThrowsWithoutDb () {

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Auth.revoke(1));

		assertTrue(ex.getMessage().contains("DB"), ex.getMessage());

	}

	@Test
	@DisplayName("F-W-33 DB が無くても、ログインした人は guard を通る（比べない）")
	void guardPassesWithoutDb () {

		Router router = new Router();
		router.get("/login", context -> { }).attribute(Auth.PUBLIC, true);
		router.get("/me", context -> { });
		router.seal();

		String session = request(router, "/login", null, context -> Auth.login(context, Principal.of(1, "有栖", "")));

		assertDoesNotThrow(() -> request(router, "/me", session, Auth::guard));

	}

	/**
	 * 1リクエスト
	 *
	 * @return	返ってきたセッションの Cookie（無ければ送ったもの）
	 */
	private static String request (Router router, String path, String session, java.util.function.Consumer<WebContext> action) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", path);

		if (session != null) {
			source.cookie(CookieSessionStore.COOKIE_NAME, session);
		}

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			context.route(router.match("GET", path));
			action.accept(context);
			context.response().send("ok");
		}

		for (String setCookie : sink.setCookies()) {
			if (setCookie.startsWith(CookieSessionStore.COOKIE_NAME + "=")) {
				int semicolon = setCookie.indexOf(';');
				return setCookie.substring(CookieSessionStore.COOKIE_NAME.length() + 1, semicolon < 0 ? setCookie.length() : semicolon);
			}
		}

		return session;

	}

}
