package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Router;
import io.jimble.web.session.SessionConf;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ログインの記憶の種別（realm。D-183）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * 記憶は<b>利用者 ID だけ</b>で持っていて、Cookie の名前も1つだった。
 * 運用者の画面と利用者の管理画面のように<b>別々の表から ID を引く</b>アプリでは、
 * <b>運用者として覚えた Cookie が、利用者の画面で「同じ ID の利用者」として思い出される</b>——
 * パスワードを1度も入れずに、別の人としてログインできる。
 * </p>
 *
 * <p>
 * 固定したいのは3つ。<b>別の種別としては入れない</b>こと、
 * <b>消す・盗用とみなすのは、その種別のその人だけ</b>であること、
 * <b>種別を渡さないアプリは何も変わらない</b>こと（上げる前の Cookie で、上げたあとも入れる）。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-web:pgTest}</p>
 */
@Tag("db")
class RememberRealmIntegrationTest {

	/** 種別 */
	private static final String OPERATOR = "operator";

	/** 種別 */
	private static final String MEMBER = "member";

	/** 2つの種別で同じ数字の ID */
	private static final long ID = 8301;

	/** 運用者の表の 8301 番 */
	private static final Principal STAFF = Principal.of(ID, "運用 有栖", "ops");

	/** 利用者の表の 8301 番（別人） */
	private static final Principal MEMBER_USER = Principal.of(ID, "会員 有栖", "member");

	/** 運用者の表から引く */
	private static final LongFunction<Principal> STAFF_LOOKUP = id -> id == ID ? STAFF : null;

	/** 利用者の表から引く */
	private static final LongFunction<Principal> MEMBER_LOOKUP = id -> id == ID ? MEMBER_USER : null;

	/* ルーター */
	private static Router router;

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		originalConf = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("session.store = \"none\"").withFallback(originalConf));

		assertTrue(DBUtil.load(Conf.conf().config(), RememberRealmIntegrationTest.class), "DB に接続できませんでした");

		router = new Router();
		router.get("/me", context -> { });
		// ログインの種別（Auth.REALM）を付けたブロック（D-185）
		router.get("/ops/me", context -> { }).attribute(Auth.REALM, OPERATOR);
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

		SessionStores.reset();
		cleanAll();

	}

	/**
	 * 3つの種別の記憶を消す
	 */
	private static void cleanAll () {

		Remember.forgetAll(ID);
		Remember.forgetAll(OPERATOR, ID);
		Remember.forgetAll(MEMBER, ID);

	}

	// region 別の種別としては入れない

	@Test
	@DisplayName("D-183 運用者として覚えた Cookie では、利用者の画面に入れない")
	void staffCookieDoesNotOpenTheMemberArea () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));
		browser.forgetSession();

		/*
		 * <b>ここが報告の形である。</b>種別が無いと、利用者の画面の restore が同じ Cookie を読み、
		 * MEMBER_LOOKUP(8301) で<b>別人（会員 有栖）としてログインさせる</b>。
		 */
		Principal asMember = browser.get(context -> {
			Remember.restore(context, MEMBER, MEMBER_LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(asMember.isAuthenticated(), "運用者の記憶で、利用者の画面に別人として入っています");

		Principal asStaff = browser.get(context -> {
			Remember.restore(context, OPERATOR, STAFF_LOOKUP);
			return Auth.principal(context);
		});

		assertEquals("運用 有栖", asStaff.name(), "運用者の画面で思い出せていません");

	}

	@Test
	@DisplayName("D-183 種別ごとに Cookie の名前が分かれ、同じ端末で両方覚えていられる")
	void twoRealmsKeepTwoCookies () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));
		browser.visit(context -> Remember.issue(context, MEMBER_USER, MEMBER));

		assertNotNull(browser.cookie(RememberConf.cookieName() + "_operator"), "運用者の Cookie が上書きされています");
		assertNotNull(browser.cookie(RememberConf.cookieName() + "_member"));
		assertNull(browser.cookie(RememberConf.cookieName()), "種別なしの Cookie まで出ています");

		browser.forgetSession();

		assertEquals("運用 有栖", browser.get(context -> {
			Remember.restore(context, OPERATOR, STAFF_LOOKUP);
			return Auth.principal(context);
		}).name());

		browser.forgetSession();

		assertEquals("会員 有栖", browser.get(context -> {
			Remember.restore(context, MEMBER, MEMBER_LOOKUP);
			return Auth.principal(context);
		}).name());

	}

	@Test
	@DisplayName("D-183 別の種別の記憶を、この種別の名前で送ってきても入れない（相手の記憶は消さない）")
	void rowOfAnotherRealmIsRejected () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));

		String staffCookie = browser.cookie(RememberConf.cookieName() + "_operator");

		// 運用者の Cookie の中身を、利用者の Cookie の名前で送る
		Browser other = new Browser();
		other.setCookie(RememberConf.cookieName() + "_member", staffCookie);

		Principal restored = other.get(context -> {
			Remember.restore(context, MEMBER, MEMBER_LOOKUP);
			return Auth.principal(context);
		});

		/*
		 * <b>Cookie の名前だけで分けていると、ここで入れてしまう。</b>
		 * 記憶の行の種別まで見るので、名前を取り違えても別の種別としては入れない。
		 */
		assertFalse(restored.isAuthenticated(), "別の種別の記憶で入れています");
		assertEquals(1, rows(OPERATOR), "持ち主の（運用者の）記憶まで消しています");

	}

	// endregion

	// region 消すのはその種別のその人だけ

	@Test
	@DisplayName("D-183 forgetAll は、その種別の記憶だけを消す")
	void forgetAllOnlyTouchesItsRealm () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));
		browser.visit(context -> Remember.issue(context, MEMBER_USER, MEMBER));

		assertEquals(1, Remember.forgetAll(MEMBER, ID));

		assertEquals(0, rows(MEMBER));
		assertEquals(1, rows(OPERATOR), "同じ ID の別の人（運用者）の記憶まで消えています");

	}

	@Test
	@DisplayName("D-183 盗用とみなして消すのも、その種別のその人だけ")
	void theftInOneRealmDoesNotWipeTheOther () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));
		browser.visit(context -> Remember.issue(context, MEMBER_USER, MEMBER));

		// 運用者の Cookie の validator を書き換える（selector は合っている＝盗用の合図）
		String staffCookie = browser.cookie(RememberConf.cookieName() + "_operator");
		Browser thief = new Browser();
		thief.setCookie(RememberConf.cookieName() + "_operator"
			, staffCookie.substring(0, staffCookie.indexOf(':') + 1) + "forged");

		thief.visit(context -> Remember.restore(context, OPERATOR, STAFF_LOOKUP));

		assertEquals(0, rows(OPERATOR), "盗用の合図で運用者の記憶が消えていません");
		assertEquals(1, rows(MEMBER), "運用者の盗用で、同じ ID の利用者の記憶まで消えています");

	}

	@Test
	@DisplayName("D-183 ログアウトすると、使っているすべての種別の記憶を消す")
	void logoutForgetsEveryRealm () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));
		browser.visit(context -> Remember.issue(context, MEMBER_USER, MEMBER));
		browser.visit(context -> Remember.issue(context, MEMBER_USER));

		browser.visit(Auth::logout);

		/*
		 * <b>ログアウトはセッションごと捨てる</b>ので、どの種別のログインも終わる。
		 * 記憶だけ残すと、<b>その種別の画面を開いた次のリクエストでまた入る</b>。
		 */
		assertNull(browser.cookie(RememberConf.cookieName() + "_operator"), "運用者の記憶の Cookie が残っています");
		assertNull(browser.cookie(RememberConf.cookieName() + "_member"));
		assertNull(browser.cookie(RememberConf.cookieName()));

		assertEquals(0, rows(OPERATOR));
		assertEquals(0, rows(MEMBER));
		assertEquals(0, rows(""));

	}

	// endregion

	// region 種別を渡さなければ、これまでどおり

	@Test
	@DisplayName("D-183 種別を渡さなければ、Cookie の名前も消す範囲もこれまでどおり")
	void noRealmIsUnchanged () {

		Browser browser = new Browser();

		browser.visit(context -> Remember.issue(context, MEMBER_USER));
		browser.visit(context -> Remember.issue(context, STAFF, OPERATOR));

		assertNotNull(browser.cookie(RememberConf.cookieName()), "種別なしの Cookie の名前が変わっています");

		browser.forgetSession();

		assertEquals("会員 有栖", browser.get(context -> {
			Remember.restore(context, MEMBER_LOOKUP);
			return Auth.principal(context);
		}).name());

		assertEquals(1, Remember.forgetAll(ID));
		assertEquals(1, rows(OPERATOR), "種別なしの forgetAll が、種別つきの記憶まで消しています");

	}

	@Test
	@DisplayName("D-183 版1の表から上げても、上げる前の Cookie で思い出せる（種別なしになる）")
	void existingRememberSurvivesTheUpgrade () throws Exception {

		DB db = DBUtil.getMainDB();

		String selector = "sel-" + System.nanoTime();
		String validator = "validator-before-upgrade";
		long now = Instant.now().toEpochMilli();

		recreateVersion1(db);

		db.execute("""
			INSERT INTO %s (selector, validator, previous_validator, rotated_at, user_id, created_at, last_used_at)
			VALUES (?, ?, '', ?, ?, ?, ?)
			""".formatted(db.dialect().identifier(FrameworkTables.AUTH_REMEMBER))
			, selector, Hash.sha256(validator), now, ID, now, now);

		// 上げたあとの起動を再現する
		forgetTableVersions(db);
		DBVersion.load(db);
		resetInstalled();

		Browser browser = new Browser();
		browser.setCookie(RememberConf.cookieName(), selector + ":" + validator);

		Principal restored = browser.get(context -> {
			Remember.restore(context, MEMBER_LOOKUP);
			return Auth.principal(context);
		});

		assertEquals("会員 有栖", restored.name(), "上げる前の記憶で、上げたあとに思い出せません");

		Data row = db.select("SELECT realm FROM %s WHERE selector = ?"
			.formatted(db.dialect().identifier(FrameworkTables.AUTH_REMEMBER)), selector);
		assertEquals("", row.getString("realm"), "上げる前の記憶が種別なしになっていません");

	}

	@Test
	@DisplayName("D-185 種別を付けたブロックでは、種別の違う記憶で思い出さない（アプリ全体の restore でも 500 にしない）")
	void restoreWithOtherRealmDoesNothingInRealmBlock () {

		Browser browser = new Browser();

		// 種別なしで覚えた（アプリ全体の remember-me）
		browser.visit(context -> Remember.issue(context, MEMBER_USER));
		browser.forgetSession();

		/*
		 * アプリ全体に置いた restore（種別なし）は、Auth.REALM = operator のブロックにも掛かる。
		 * <b>投げない。しかも思い出さない</b>——思い出すと operator の置き場所にログインし、
		 * operator のログアウトでは消えない記憶で、次のリクエストでまた入ってしまう
		 */
		Principal inOps = browser.get("/ops/me", context -> {
			Remember.restore(context, MEMBER_LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(inOps.isAuthenticated(), "種別なしの記憶で、運用者のブロックに入っています");

		// 種別なしのルートでは、これまでどおり思い出す
		Principal plain = browser.get(context -> {
			Remember.restore(context, MEMBER_LOOKUP);
			return Auth.principal(context);
		});

		assertEquals("会員 有栖", plain.name(), "種別なしのルートで思い出せていません");

	}

	@Test
	@DisplayName("D-183 使えない種別は落とす")
	void invalidRealmIsRejected () {

		assertThrows(IllegalArgumentException.class, () -> Remember.restore((String) null, STAFF_LOOKUP));
		assertThrows(IllegalArgumentException.class, () -> Remember.restore("ops:admin", STAFF_LOOKUP));
		assertThrows(IllegalArgumentException.class, () -> Remember.forgetAll("ops admin", ID));
		assertThrows(IllegalArgumentException.class, () -> Remember.forgetAll("a".repeat(65), ID));

		assertDoesNotThrow(() -> Remember.forgetAll("ops_admin-2", ID));

	}

	// endregion

	// region 補助

	/**
	 * 種別ごとの行数
	 *
	 * @param realm	種別
	 * @return	行数
	 */
	private static int rows (String realm) {

		Data row = DBUtil.getMainDB().select("SELECT count(*) as cnt FROM %s WHERE realm = ? AND user_id = ?"
			.formatted(DBUtil.getMainDB().dialect().identifier(FrameworkTables.AUTH_REMEMBER)), realm, ID);

		return row == null ? -1 : row.getInt("cnt");

	}

	/**
	 * 表を版1の形で作り直す
	 *
	 * @param db	DB
	 */
	private static void recreateVersion1 (DB db) {

		boolean mysql = !"postgresql".equals(db.dialect().name());
		String name = FrameworkTables.AUTH_REMEMBER;
		String table = db.dialect().identifier(name);

		db.execute("DROP TABLE IF EXISTS " + table);
		db.execute("""
			create table %s (
				selector varchar(64) not null primary key
				, validator varchar(64) not null
				, previous_validator varchar(64) not null
				, rotated_at bigint not null
				, user_id bigint not null
				, created_at bigint not null
				, last_used_at bigint not null
			)%s""".formatted(table, mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : ""));
		db.execute(db.dialect().setTableCommentSql(name, "ログインの記憶:1"));

		assertFalse(db.isError(), String.valueOf(db.getError()));

	}

	/**
	 * 表の版の控えを捨てる（起動し直した JVM と同じ状態にする）
	 *
	 * @param db	DB
	 * @throws Exception	例外
	 */
	@SuppressWarnings("unchecked")
	private static void forgetTableVersions (DB db) throws Exception {

		Field field = DBVersion.class.getDeclaredField("dbTableInfoMap");
		field.setAccessible(true);
		((Map<String, ?>) field.get(null)).remove(db.getDBName());

	}

	/**
	 * 表を作ったかどうかの印を戻す
	 *
	 * @throws Exception	例外
	 */
	private static void resetInstalled () throws Exception {

		Field field = Remember.class.getDeclaredField("initialized");
		field.setAccessible(true);
		field.setBoolean(null, false);

	}

	/**
	 * Cookie を持ち回る「ブラウザ」（{@code RememberIntegrationTest} と同じ作り）
	 */
	private static final class Browser {

		/* 受信済みの Cookie */
		private final Map<String, String> cookies = new LinkedHashMap<>();

		/**
		 * 1リクエスト
		 *
		 * @param action	中でやること
		 * @param <T>		戻り値
		 * @return	action の戻り値
		 */
		<T> T get (Function<WebContext, T> action) {

			return get("/me", action);

		}

		/**
		 * 1リクエスト（パスを決める）
		 *
		 * @param path		パス
		 * @param action	中でやること
		 * @param <T>		戻り値
		 * @return	action の戻り値
		 */
		<T> T get (String path, Function<WebContext, T> action) {

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", path);
			cookies.forEach(source::cookie);

			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			T result;

			try (WebContext context = new WebContext(source, sink)) {
				context.route(router.match("GET", path));
				result = action.apply(context);
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

			return result;

		}

		/**
		 * 1リクエスト（戻り値なし）
		 *
		 * @param action	中でやること
		 */
		void visit (java.util.function.Consumer<WebContext> action) {

			get(context -> {
				action.accept(context);
				return null;
			});

		}

		/** Cookie を読む */
		String cookie (String name) {
			return cookies.get(name);
		}

		/** Cookie を置く */
		void setCookie (String name, String value) {
			cookies.put(name, value);
		}

		/** セッションだけ捨てる（ブラウザを閉じたのと同じ） */
		void forgetSession () {
			cookies.remove(SessionConf.cookieName());
		}

	}

	// endregion

}
