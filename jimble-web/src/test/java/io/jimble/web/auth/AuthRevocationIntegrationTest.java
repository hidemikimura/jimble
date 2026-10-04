package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Router;
import io.jimble.web.session.CookieSessionStore;
import io.jimble.web.session.SessionConf;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 利用者を外からログアウトさせる（要件 F-W-33 / D-199）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * ログインの判定はセッションの中だけで決まっていて、<b>本人のリクエストの外から終わらせる手が無かった</b>。
 * {@code Remember.forgetAll} は記憶を消すだけで、<b>生きているセッションはそのまま入れた</b>。
 * </p>
 *
 * <p>
 * 固定したいのは4つ。<b>締め出した人は次のリクエストで入れない</b>こと（Cookie セッションでも）、
 * <b>締め出していない人・上げる前のセッションは何も変わらない</b>こと、
 * <b>種別をまたがない</b>こと、<b>remember-me とぶつかっても生き残らない</b>こと。
 * </p>
 *
 * <p>
 * セッションは <b>Cookie に置く</b>。サーバーに行が無いので「探して消す」ができない保存先で、
 * それでも効くことを見る。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-web:dbTest}</p>
 */
@Tag("db")
class AuthRevocationIntegrationTest {

	/** 種別 */
	private static final String OPERATOR = "operator";

	/** 締め出す人 */
	private static final long ID = 8401;

	/** 締め出さない人 */
	private static final long OTHER_ID = 8402;

	/** 締め出す人 */
	private static final Principal ALICE = Principal.of(ID, "有栖", "member");

	/** 締め出さない人 */
	private static final Principal BOB = Principal.of(OTHER_ID, "坊", "member");

	/** 運用者の表の 8401 番（別人） */
	private static final Principal STAFF = Principal.of(ID, "運用 有栖", "ops");

	/** id から引き直す */
	private static final LongFunction<Principal> LOOKUP = id -> id == ID ? ALICE : id == OTHER_ID ? BOB : null;

	/* ルーター */
	private static Router router;

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		originalConf = Conf.conf().config();

		DBUtil.load(Conf.conf().config(), AuthRevocationIntegrationTest.class);

		router = new Router();
		router.get("/me", context -> { });
		router.get("/public", context -> { }).attribute(Auth.PUBLIC, true);
		router.get("/ops/me", context -> { }).attribute(Auth.REALM, OPERATOR);
		router.get("/ops/login", context -> { }).attribute(Auth.REALM, OPERATOR).attribute(Auth.PUBLIC, true);
		router.seal();

	}

	@AfterAll
	static void stopDataSource () {

		cleanAll();

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		conf("");
		cleanAll();

	}

	@AfterEach
	void restoreConf () {

		conf("");

	}

	/**
	 * Cookie セッションにして、設定を足す
	 *
	 * @param extra	足す設定
	 */
	private static void conf (String extra) {

		Conf.replace(ConfigFactory.parseString("""
			session.store = "cookie"
			session.secret = "revocation-test-secret"
			""" + extra).withFallback(originalConf));

		SessionStores.reset();
		Revocations.clearCache();

	}

	/**
	 * 世代と記憶を消す
	 */
	private static void cleanAll () {

		try (DB db = DBUtil.getMainDB()) {
			// 表がまだ無ければ作る（初回）
			Revocations.forLogin("", ID);
			db.execute("DELETE FROM %s WHERE user_id IN (?, ?)".formatted(table(db)), ID, OTHER_ID);
		}

		Remember.forgetAll(ID);
		Remember.forgetAll(OTHER_ID);
		Remember.forgetAll(OPERATOR, ID);

		Revocations.clearCache();

	}

	// region 締め出す

	@Test
	@DisplayName("F-W-33 revoke すると、ほかの端末のセッションは次のリクエストで 401 になる")
	void revokeEndsLiveSessions () {

		Browser pc = Browser.loggedIn(ALICE);
		Browser phone = Browser.loggedIn(ALICE);

		assertEquals(200, pc.status("/me"));

		Auth.revoke(ID);

		assertEquals(401, pc.status("/me"), "締め出したのに、PC のセッションで入れます");
		assertEquals(401, phone.status("/me"), "締め出したのに、スマホのセッションで入れます");

		// 一度弾かれたら、そのセッションはもうログインしていない（鍵を消している）
		assertFalse(pc.principal("/public").isAuthenticated());

	}

	@Test
	@DisplayName("F-W-33 締め出していない人は、何も変わらない")
	void othersAreUntouched () {

		Browser alice = Browser.loggedIn(ALICE);
		Browser bob = Browser.loggedIn(BOB);

		Auth.revoke(ID);

		assertEquals(401, alice.status("/me"));
		assertEquals(200, bob.status("/me"), "締め出していない人まで弾いています");

	}

	@Test
	@DisplayName("F-W-33 PUBLIC のルートでも、締め出した人をログイン中に見せない")
	void publicRouteDoesNotShowRevokedLogin () {

		Browser pc = Browser.loggedIn(ALICE);

		Auth.revoke(ID);

		Principal seen = pc.principal("/public");

		assertEquals(200, pc.status("/public"), "公開のルートは 401 にしない");
		assertFalse(seen.isAuthenticated(), "公開のルートで、締め出した人がログイン中に見えています");

	}

	@Test
	@DisplayName("F-W-33 締め出したあとでも、入り直せば入れる（これからのログインは止めない）")
	void loginAfterRevokeWorks () {

		Browser pc = Browser.loggedIn(ALICE);

		Auth.revoke(ID);

		assertEquals(401, pc.status("/me"));

		pc.visit("/public", context -> Auth.login(context, ALICE));

		assertEquals(200, pc.status("/me"), "締め出したあとに入り直したのに、弾かれます");

	}

	@Test
	@DisplayName("F-W-33 revokeOthers は、いまの端末だけを残す")
	void revokeOthersKeepsTheCurrentDevice () {

		Browser pc = Browser.loggedIn(ALICE);
		Browser phone = Browser.loggedIn(ALICE);

		// パスワードを変えた画面（PC）
		pc.visit("/me", Auth::revokeOthers);

		assertEquals(200, pc.status("/me"), "パスワードを変えた本人の端末まで締め出しています");
		assertEquals(401, phone.status("/me"), "ほかの端末が残っています");

		// もう一度締め出されても、PC は入り直せばよい
		assertEquals(200, pc.status("/me"));

	}

	@Test
	@DisplayName("F-W-33 revokeOthers は、ログインしていなければ投げる")
	void revokeOthersNeedsALogin () {

		Browser anonymous = new Browser();

		IllegalStateException ex = anonymous.error("/public", Auth::revokeOthers);

		assertTrue(ex.getMessage().contains("ログインしていない"), ex.getMessage());

	}

	@Test
	@DisplayName("F-W-33 revoke は remember-me の記憶も消す（残すと次のリクエストでまた入る）")
	void revokeAlsoForgetsRememberMe () {

		Browser pc = Browser.loggedIn(ALICE);
		pc.visit("/me", context -> Remember.issue(context, ALICE));

		assertEquals(1, rememberRows(""));

		Auth.revoke(ID);

		assertEquals(0, rememberRows(""), "締め出したのに、記憶が残っています");

		// セッションは弾かれ、記憶からも思い出せない
		assertEquals(401, pc.status("/me"));
		assertFalse(pc.restored("/me").isAuthenticated(), "締め出したのに、remember-me でまた入れます");

	}

	// endregion

	// region 種別

	@Test
	@DisplayName("F-W-33 / D-185 種別つきの revoke は、その種別のログインだけを終える（同じセッションの種別なしは残る）")
	void revokeOnlyTouchesItsRealm () {

		Browser browser = Browser.loggedIn(ALICE);
		browser.visit("/ops/login", context -> Auth.login(context, STAFF));

		assertEquals(200, browser.status("/ops/me"));

		Auth.revoke(OPERATOR, ID);

		assertEquals(401, browser.status("/ops/me"), "運用者のログインが残っています");
		assertEquals(200, browser.status("/me"), "同じ ID の種別なしのログイン（別人）まで終わっています");

	}

	@Test
	@DisplayName("F-W-33 種別なしの revoke は、種別つきのログインに触らない")
	void plainRevokeDoesNotTouchRealms () {

		Browser browser = Browser.loggedIn(ALICE);
		browser.visit("/ops/login", context -> Auth.login(context, STAFF));

		Auth.revoke(ID);

		assertEquals(401, browser.status("/me"));
		assertEquals(200, browser.status("/ops/me"), "種別なしの締め出しで、運用者のログインまで終わっています");

	}

	// endregion

	// region 上げたとき

	@Test
	@DisplayName("F-W-33 上げる前のセッション（世代の鍵が無い）は、締め出さなければそのまま入れる")
	void sessionsFromBeforeTheUpgradeSurvive () {

		Browser browser = Browser.loggedIn(ALICE);

		// 2.0 のログインと同じ形にする（__auth_gen が無い）
		browser.visit("/me", context -> {
			context.session().remove("__auth_gen");
			context.session().save();
		});

		assertEquals(200, browser.status("/me"), "上げる前のセッションが弾かれています（上げた日に全員ログアウトされる）");

		Auth.revoke(ID);

		assertEquals(401, browser.status("/me"), "上げる前のセッションは締め出せていません");

	}

	@Test
	@DisplayName("F-W-33 締め出したことの無い人のために、行を作らない")
	void noRowsForPeopleNeverRevoked () {

		Browser browser = Browser.loggedIn(BOB);

		assertEquals(200, browser.status("/me"));
		assertEquals(0, revocationRows(OTHER_ID), "締め出していない人の行ができています");

	}

	// endregion

	// region 控え

	@Test
	@DisplayName("F-W-33 ほかの台で締め出すと、控えが切れるまでは遅れる（cache_ttl）")
	void otherNodesSeeItAfterTheTtl () {

		conf("auth.revocation.cache_ttl = 1h");

		Browser pc = Browser.loggedIn(ALICE);

		// この台の控えに世代 0 が載る
		assertEquals(200, pc.status("/me"));

		// ほかの台が締め出した（この台の控えは知らない）
		bumpLikeOldNode(ID);

		assertEquals(200, pc.status("/me"), "控えが効いていません（毎回 DB を引いています）");

		Revocations.clearCache();

		assertEquals(401, pc.status("/me"), "控えが切れたのに、締め出しが効きません");

	}

	@Test
	@DisplayName("D-274 全体の世代が変わらなければ、cache_ttl を過ぎても控えを使う。変われば次のリクエストで効く")
	void watermarkKeepsTheCache () throws Exception {

		conf("auth.revocation.cache_ttl = 100ms");

		Browser pc = Browser.loggedIn(ALICE);

		assertEquals(200, pc.status("/me"));

		/*
		 * 利用者の行だけが変わり、全体の世代は変わらない。
		 * 2.5.1 までは 100ms で控えが切れて引き直していた（ここで 401 になった）。いまは引き直さない
		 */
		bumpLikeOldNode(ID);
		Thread.sleep(250);

		assertEquals(200, pc.status("/me"), "全体の世代が変わっていないのに、利用者ごとに引き直しています");

		// 2.5.2 以降の台が締め出すと、全体の世代も上がるので、cache_ttl のうちに効く
		bumpLikeAnotherNode(ID);
		Thread.sleep(250);

		assertEquals(401, pc.status("/me"), "全体の世代が変わったのに、控えを使い続けています");

	}

	@Test
	@DisplayName("D-274 この台の revoke は全体の世代も上げる")
	void revokeBumpsTheGlobalGeneration () {

		long before = globalGeneration();

		Auth.revoke(ID);

		assertEquals(before + 1, globalGeneration());

	}

	@Test
	@DisplayName("F-W-33 cache_ttl = 0s なら、ほかの台の締め出しも即座に効く")
	void zeroTtlReadsEveryTime () {

		conf("auth.revocation.cache_ttl = 0s");

		Browser pc = Browser.loggedIn(ALICE);

		assertEquals(200, pc.status("/me"));

		bumpLikeOldNode(ID);

		assertEquals(401, pc.status("/me"), "cache_ttl = 0s なのに控えを使っています");

	}

	@Test
	@DisplayName("F-W-33 / D-199 ほかの台で締め出した直後にこの台でログインしても、弾かれない（ログインは控えを使わない）")
	void loginDoesNotUseTheStaleCache () {

		conf("auth.revocation.cache_ttl = 1h");

		Browser first = Browser.loggedIn(ALICE);

		// この台の控えに世代 0 が載る
		assertEquals(200, first.status("/me"));

		bumpLikeOldNode(ID);

		// 締め出された直後に、この台でログインし直す
		Browser second = Browser.loggedIn(ALICE);

		// 控えが切れた（あるいは別の台に振られた）あとも入れること
		Revocations.clearCache();

		assertEquals(200, second.status("/me"), "ログインで古い世代を入れたので、入ったばかりの人が弾かれました");

	}

	// endregion

	// region remember-me とぶつかる

	@Test
	@DisplayName("D-199 記憶を確かめた直後に締め出されても、remember-me で生き残らない")
	void revokeDuringRestoreDoesNotSurvive () {

		Browser pc = Browser.loggedIn(ALICE);
		pc.visit("/me", context -> Remember.issue(context, ALICE));
		pc.forgetSession();

		/*
		 * restore は、記憶を確かめたあとで lookup を呼ぶ。<b>その中で締め出す</b>——
		 * 「確かめた」と「世代を引く」の間に Auth.revoke が挟まった形である。
		 * 見直しが無いと、上がったあとの世代をもらってそのまま入る。
		 */
		Principal restored = pc.restored("/me", id -> {
			Auth.revoke(id);
			return LOOKUP.apply(id);
		});

		assertFalse(restored.isAuthenticated(), "締め出されたのに、remember-me で入りました");
		assertEquals(401, pc.status("/me"));

	}

	@Test
	@DisplayName("D-199 revoke は記憶を消してから世代を上げる（世代を書けなくても、記憶は消えている）")
	void revokeForgetsBeforeBumping () throws Exception {

		Browser pc = Browser.loggedIn(ALICE);
		pc.visit("/me", context -> Remember.issue(context, ALICE));

		/*
		 * <b>逆の順番だと、上げたあと・消す前に restore した人が新しい世代をもらって生き残る。</b>
		 * その隙間は外から差し込めないので、世代を書けなくして順番を見る——
		 * 先に消していれば、書けずに投げたときにはもう記憶が無い。
		 */
		try (DB db = DBUtil.getMainDB()) {

			db.execute("DROP TABLE " + table(db));

			try {
				IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Auth.revoke(ID));
				assertEquals(0, rememberRows(""), "世代を上げる前に記憶を消していません: " + ex);
			} finally {
				forgetTableVersions(db);
				DBVersion.load(db);
				resetInstalled();
			}

		}

	}

	@Test
	@DisplayName("F-W-33 締め出していなければ、remember-me はこれまでどおり思い出す")
	void restoreStillWorks () {

		Browser pc = Browser.loggedIn(ALICE);
		pc.visit("/me", context -> Remember.issue(context, ALICE));
		pc.forgetSession();

		assertEquals("有栖", pc.restored("/me").name());
		assertEquals(200, pc.status("/me"));

	}

	// endregion

	// region 失敗

	@Test
	@DisplayName("F-W-33 enabled = false なら比べない。revoke は例外（黙って効かない形にしない）")
	void disabledThrowsOnRevoke () {

		conf("auth.revocation.enabled = false");

		Browser pc = Browser.loggedIn(ALICE);

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Auth.revoke(ID));

		assertTrue(ex.getMessage().contains("auth.revocation.enabled"), ex.getMessage());
		assertEquals(200, pc.status("/me"));

	}

	@Test
	@DisplayName("F-W-33 id が 0 なら投げる")
	void revokeNeedsAnId () {

		assertThrows(IllegalArgumentException.class, () -> Auth.revoke(0));
		assertThrows(IllegalArgumentException.class, () -> Auth.revoke("ops admin", ID));

	}

	@Test
	@DisplayName("F-W-33 世代を引けなければ通さない（500）。表が戻れば、また作って入れる")
	void unreadableTableFailsClosed () throws Exception {

		Browser pc = Browser.loggedIn(ALICE);

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DROP TABLE " + table(db));
			Revocations.clearCache();

			assertThrows(IllegalStateException.class, () -> pc.status("/me"), "世代を引けないのに通しています");

			// 起動し直したのと同じ状態にして、もう一度作らせる
			forgetTableVersions(db);
			DBVersion.load(db);
			resetInstalled();

		}

		assertEquals(200, pc.status("/me"));

	}

	// endregion

	// region 補助

	/**
	 * 2.5.1 以前の台が締め出したことにする（利用者の行だけを上げる。全体の世代は上げない）
	 */
	private static void bumpLikeOldNode (long userId) {

		try (DB db = DBUtil.getMainDB()) {

			int updated = db.update("UPDATE %s SET generation = generation + 1 WHERE realm = '' AND user_id = ?"
				.formatted(table(db)), userId);

			if (updated == 0) {
				db.execute("INSERT INTO %s (realm, user_id, generation, revoked_at) VALUES ('', ?, 1, 0)"
					.formatted(table(db)), userId);
			}

		}

	}

	/**
	 * ほかの台（2.5.2 以降）が締め出したことにする（利用者の行と全体の世代を上げる。D-274）
	 */
	private static void bumpLikeAnotherNode (long userId) {

		bumpLikeOldNode(userId);

		try (DB db = DBUtil.getMainDB()) {

			int updated = db.update("UPDATE %s SET generation = generation + 1 WHERE realm = ? AND user_id = 0"
				.formatted(table(db)), Revocations.GLOBAL_REALM);

			if (updated == 0) {
				db.execute("INSERT INTO %s (realm, user_id, generation, revoked_at) VALUES (?, 0, 1, 0)"
					.formatted(table(db)), Revocations.GLOBAL_REALM);
			}

		}

	}

	/**
	 * 全体の世代（行が無ければ 0）
	 */
	private static long globalGeneration () {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT generation FROM %s WHERE realm = ? AND user_id = 0".formatted(table(db)), Revocations.GLOBAL_REALM)
				.map(row -> row.getLong("generation")).orElse(0L);
		}

	}

	/**
	 * 世代の行数
	 */
	private static int revocationRows (long userId) {

		try (DB db = DBUtil.getMainDB()) {
			Data row = db.select("SELECT count(*) as cnt FROM %s WHERE user_id = ?".formatted(table(db)), userId).orElse(null);
			return row == null ? -1 : row.getInt("cnt");
		}

	}

	/**
	 * 記憶の行数
	 */
	private static int rememberRows (String realm) {

		try (DB db = DBUtil.getMainDB()) {
			Data row = db.select("SELECT count(*) as cnt FROM %s WHERE realm = ? AND user_id = ?"
				.formatted(db.dialect().identifier(FrameworkTables.AUTH_REMEMBER)), realm, ID).orElse(null);
			return row == null ? -1 : row.getInt("cnt");
		}

	}

	/**
	 * テーブル名
	 */
	private static String table (DB db) {

		return db.dialect().identifier(FrameworkTables.AUTH_REVOCATION);

	}

	/**
	 * 表の版の控えを捨てる（起動し直した JVM と同じ状態にする）
	 */
	@SuppressWarnings("unchecked")
	private static void forgetTableVersions (DB db) throws Exception {

		Field field = DBVersion.class.getDeclaredField("dbTableInfoMap");
		field.setAccessible(true);
		((Map<String, ?>) field.get(null)).remove(db.getDBName());

	}

	/**
	 * 表を作ったかどうかの印を戻す
	 */
	private static void resetInstalled () throws Exception {

		Field field = Revocations.class.getDeclaredField("initialized");
		field.setAccessible(true);
		field.setBoolean(null, false);

	}

	/**
	 * Cookie を持ち回る「ブラウザ」（セッションも Cookie の中）
	 */
	private static final class Browser {

		/* 受信済みの Cookie */
		private final Map<String, String> cookies = new LinkedHashMap<>();

		/**
		 * ログインした端末
		 *
		 * @param principal	ログインする人
		 * @return	端末
		 */
		static Browser loggedIn (Principal principal) {

			Browser browser = new Browser();
			browser.visit("/public", context -> Auth.login(context, principal));

			return browser;

		}

		/**
		 * 1リクエスト（guard を通してから action）
		 *
		 * @param path		パス
		 * @param action	中でやること
		 * @return	状態コード
		 */
		int request (String path, Consumer<WebContext> action) {

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", path);
			cookies.forEach(source::cookie);

			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			int status = 200;

			try (WebContext context = new WebContext(source, sink)) {

				context.route(router.match("GET", path));

				try {
					Auth.guard(context);
					action.accept(context);
				} catch (HttpException ex) {
					status = ex.statusCode();
				}

				context.response().send("ok");

			}

			for (String setCookie : sink.setCookies()) {

				int equals = setCookie.indexOf('=');
				int semicolon = setCookie.indexOf(';');

				String name = setCookie.substring(0, equals);
				String value = setCookie.substring(equals + 1, semicolon < 0 ? setCookie.length() : semicolon);

				if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
					cookies.remove(name);
				} else {
					cookies.put(name, value);
				}

			}

			return status;

		}

		/** 1リクエスト（戻り値なし） */
		void visit (String path, Consumer<WebContext> action) {

			assertEquals(200, request(path, action), path + " が通りません");

		}

		/** 状態コードだけ */
		int status (String path) {

			return request(path, context -> { });

		}

		/** guard を通ったあとの Auth.principal */
		Principal principal (String path) {

			Principal[] seen = { Principal.ANONYMOUS };
			request(path, context -> seen[0] = Auth.principal(context));

			return seen[0];

		}

		/** guard の前に restore を置いたときの Auth.principal */
		Principal restored (String path) {

			return restored(path, LOOKUP);

		}

		/** guard の前に restore を置いたときの Auth.principal（引き直しを決める） */
		Principal restored (String path, LongFunction<Principal> lookup) {

			Principal[] seen = { Principal.ANONYMOUS };

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", path);
			cookies.forEach(source::cookie);

			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			try (WebContext context = new WebContext(source, sink)) {
				context.route(router.match("GET", path));
				Remember.restore(context, lookup);
				seen[0] = Auth.principal(context);
				context.response().send("ok");
			}

			for (String setCookie : sink.setCookies()) {
				int equals = setCookie.indexOf('=');
				int semicolon = setCookie.indexOf(';');
				String name = setCookie.substring(0, equals);
				String value = setCookie.substring(equals + 1, semicolon < 0 ? setCookie.length() : semicolon);
				if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
					cookies.remove(name);
				} else {
					cookies.put(name, value);
				}
			}

			return seen[0];

		}

		/** action が投げる IllegalStateException */
		IllegalStateException error (String path, Consumer<WebContext> action) {

			return assertThrows(IllegalStateException.class, () -> request(path, action));

		}

		/** セッションだけ捨てる（ブラウザを閉じたのと同じ） */
		void forgetSession () {

			cookies.remove(SessionConf.cookieName());
			cookies.remove(CookieSessionStore.COOKIE_NAME);

		}

	}

	// endregion

}
