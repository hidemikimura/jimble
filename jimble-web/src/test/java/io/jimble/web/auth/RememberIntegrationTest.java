package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ログインを覚えて思い出すところ（要件 F-W-30）
 *
 * <p>
 * <b>ブラウザのふりをする。</b>1リクエストごとに {@link WebContext} を作り、
 * 返ってきた Cookie を次のリクエストへ持ち回る——
 * <b>Cookie を回すところが本体</b>なので、1リクエストの中だけ見ても何も確かめられない。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-web:pgTest
 * </pre>
 */
@Tag("db")
class RememberIntegrationTest {

	/** 覚える相手 */
	private static final Principal ALICE = Principal.of(7, "有栖", "member");

	/** id から引き直す（ふつうは DB を引く） */
	private static final LongFunction<Principal> LOOKUP = id -> id == ALICE.id() ? ALICE : null;

	/* ルーター（ふつうのルート1本だけ） */
	private static Router router;

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		/*
		 * <b>セッションの保存先は none にする。</b>
		 * ここで見たいのは <b>Cookie を回すところ</b>で、セッションが DB に載るかどうかではない。
		 * DB セッションのテーブルは<b>別のテストが最後に落とす</b>ので、
		 * 当てにすると<b>実行順で落ちるテスト</b>になる。
		 */
		originalConf = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("session.store = \"none\"").withFallback(originalConf));

		assertTrue(
			DBUtil.load(Conf.conf().config(), RememberIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		router = new Router();
		router.get("/me", context -> { });
		router.get("/password", context -> { }).attribute(Auth.FULL_AUTH, true);
		router.get("/guide", context -> { }).attribute(Auth.PUBLIC, true).attribute(Auth.NO_SESSION, true);
		router.seal();

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();
		Remember.forgetAll(ALICE.id());

	}

	// region 覚えて、思い出す

	@Test
	@DisplayName("F-W-30 覚えさせると、次のリクエストで思い出す")
	void remembersAcrossRequests () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		assertNotNull(browser.cookie(RememberConf.cookieName()), "Cookie が出ていない");

		// セッションは持ち越さない＝ブラウザを閉じたのと同じ
		browser.forgetSession();

		Principal restored = browser.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertEquals(ALICE.id(), restored.id(), "思い出せていない");
		assertEquals("member", restored.role());

	}

	@Test
	@DisplayName("F-W-30 思い出した人は「パスワードを入れた人」ではない")
	void restoredIsNotFullyAuthenticated () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));
		browser.forgetSession();

		browser.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			assertTrue(Auth.principal(context).isAuthenticated());
			assertFalse(Auth.fullyAuthenticated(context)
				, "remember で戻っただけの人が full auth になっている");
			return null;
		});

		/*
		 * <b>ここが remember-me を出せる理由である。</b>
		 * Cookie を盗まれても、パスワードの変更まではできない。
		 */
		browser.get("/password", context -> {

			Remember.restore(context, LOOKUP);

			HttpException thrown = assertThrows(HttpException.class, () -> Auth.guard(context)
				, "FULL_AUTH のルートに remember だけで入れてしまっている");

			assertEquals(401, thrown.statusCode(), "403 だと、入り直せば見られることが伝わらない");

			return null;

		});

	}

	@Test
	@DisplayName("F-W-30 パスワードを入れて入った人は FULL_AUTH のルートに入れる")
	void passwordLoginIsFullyAuthenticated () {

		new Browser().get("/password", context -> {

			Auth.login(context, ALICE);

			assertTrue(Auth.fullyAuthenticated(context));
			Auth.guard(context);

			return null;

		});

	}

	@Test
	@DisplayName("F-W-30 セッションを使わないルートでは、思い出しに行かない")
	void doesNothingOnNoSessionRoutes () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));
		browser.forgetSession();

		browser.visit("/guide", context -> {

			/*
			 * <b>ここで思い出すと、guard が保存先を none に差し替える前に
			 * セッションが決まってしまう</b>——公開ページにセッションの行が増える。
			 */
			Remember.restore(context, LOOKUP);
			Auth.guard(context);

			assertFalse(Auth.principal(context).isAuthenticated()
				, "セッションを使わないページで思い出している");

		});

	}

	@Test
	@DisplayName("F-W-30 validator は、そのままでは保存しない")
	void storesTheValidatorHashed () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		String cookie = browser.cookie(RememberConf.cookieName());
		String validator = cookie.substring(cookie.indexOf(':') + 1);

		Data row = DBUtil.getMainDB().select("SELECT selector, validator FROM %s".formatted(table()));

		/*
		 * <b>そのまま保存すると、DB を読めた人が全員になりすませる。</b>
		 * パスワードを平文で持つのと同じことになる。
		 */
		assertNotEquals(validator, row.getString("validator")
			, "validator がそのまま入っている");
		assertEquals(64, row.getString("validator").length(), "SHA-256 になっていない");

		/*
		 * <b>selector はそのままでよい。</b>行を引くための鍵で、
		 * これだけでは照合できない（だから2つに分けている）。
		 */
		assertEquals(selectorOf(cookie), row.getString("selector"));

	}

	// endregion

	// region 回す

	@Test
	@DisplayName("F-W-30 使うたびに validator が変わる")
	void rotatesOnEveryUse () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		String first = browser.cookie(RememberConf.cookieName());

		browser.forgetSession();
		browser.visit("/me", context -> Remember.restore(context, LOOKUP));

		String second = browser.cookie(RememberConf.cookieName());

		assertNotEquals(first, second, "使っても Cookie が変わらない（盗まれても気づけない）");
		assertEquals(selectorOf(first), selectorOf(second), "selector まで変わっている（行を引けなくなる）");

	}

	@Test
	@DisplayName("F-W-30 回した直後の古い Cookie も、猶予の中なら通る")
	void oldCookieStillWorksInsideTheGrace () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		String old = browser.cookie(RememberConf.cookieName());

		browser.forgetSession();
		browser.visit("/me", context -> Remember.restore(context, LOOKUP));

		/*
		 * <b>1枚のページが画像や API を同時に取りに行くと、これが起きる。</b>
		 * 回した直後の古い Cookie を持った通信が、数本あとから届く——
		 * それを盗用とみなすと、ふつうに使っている人が締め出される。
		 */
		Browser straggler = new Browser();
		straggler.setCookie(RememberConf.cookieName(), old);

		Principal restored = straggler.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertEquals(ALICE.id(), restored.id(), "猶予の中なのに締め出している");
		assertEquals(1, rowCount(), "記憶が消えている（盗用と判定された）");

	}

	@Test
	@DisplayName("F-W-30 同時に使っても、渡される Cookie は2つまで")
	void concurrentUseHandsOutAtMostTwoCookies () {

		Browser first = new Browser();
		first.visit("/me", context -> Remember.issue(context, ALICE));

		String issued = first.cookie(RememberConf.cookieName());

		/*
		 * <b>1枚のページから同時に飛ぶ通信を再現する。</b>
		 * どれも同じ Cookie を持っていて、どれも「回そう」とする。
		 *
		 * <b>回すときに「今の validator のままなら」を付けていないと、
		 * 何本も回ってしまう</b>——先に回したほうが渡した Cookie は
		 * <b>今のものでも回転前のものでもなくなり、次に使ったときに盗用と判定される</b>。
		 * 本人が並列に使っただけで、全端末から締め出される。
		 */
		java.util.Set<String> handedOut = java.util.concurrent.ConcurrentHashMap.newKeySet();
		Thread[] threads = new Thread[8];

		for (int i = 0; i < threads.length; i++) {

			threads[i] = new Thread(() -> {

				Browser browser = new Browser();
				browser.setCookie(RememberConf.cookieName(), issued);
				browser.visit("/me", context -> Remember.restore(context, LOOKUP));

				handedOut.add(browser.cookie(RememberConf.cookieName()));

			});

			threads[i].start();

		}

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		assertTrue(handedOut.size() <= 2
			, "同時に使っただけで %d 種類の Cookie が出ている（1つ回すたびに、前のものが盗用になる）"
				.formatted(handedOut.size()));

		assertEquals(1, rowCount(), "記憶が消えている");

	}

	// endregion

	// region 盗まれたとき

	@Test
	@DisplayName("F-W-30 猶予を過ぎた古い Cookie は盗用とみなし、その人のぶんを全部消す")
	void detectsTheft () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		String stolen = browser.cookie(RememberConf.cookieName());

		// 別の端末でも覚えている（＝全部消えることを見るため）
		Browser other = new Browser();
		other.visit("/me", context -> Remember.issue(context, ALICE));

		browser.forgetSession();
		browser.visit("/me", context -> Remember.restore(context, LOOKUP));

		assertEquals(2, rowCount());

		Config original = Conf.conf().config();

		try {

			// 猶予を無くす（時間を進める代わり）
			Conf.replace(ConfigFactory.parseString("auth.remember.grace = 0s").withFallback(original));

			Browser thief = new Browser();
			thief.setCookie(RememberConf.cookieName(), stolen);

			Principal restored = thief.get("/me", context -> {
				Remember.restore(context, LOOKUP);
				return Auth.principal(context);
			});

			assertFalse(restored.isAuthenticated(), "盗まれた Cookie で入れてしまっている");

		} finally {
			Conf.replace(original);
		}

		/*
		 * <b>1本だけ消しても足りない。</b>
		 * 盗んだ側と本人のどちらが先に使ったかは区別できないので、
		 * 片方だけ消すと<b>本人だけが締め出されて盗んだ側が残る</b>ことがある。
		 */
		assertEquals(0, rowCount(), "その人のぶんが全部消えていない");

	}

	@Test
	@DisplayName("F-W-30 selector が合っていて validator が違えば、盗用とみなす")
	void detectsAWrongValidator () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		String cookie = browser.cookie(RememberConf.cookieName());

		Browser thief = new Browser();
		thief.setCookie(RememberConf.cookieName(), selectorOf(cookie) + ":" + "AAAAAAAAAAAAAAAAAAAAAA");

		Principal restored = thief.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(restored.isAuthenticated());
		assertEquals(0, rowCount(), "引くための鍵が漏れているのに、記憶を残している");

	}

	@Test
	@DisplayName("F-W-30 知らない selector は、盗用ではなく「もう無い」として扱う")
	void unknownSelectorIsNotTheft () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));

		/*
		 * <b>掃除で消えたあとや、別の端末で「全部忘れる」をしたあとは、ここへ来る。</b>
		 * これを盗用にすると、<b>まだ生きている他の端末まで巻き添えで消える</b>。
		 */
		Browser stale = new Browser();
		stale.setCookie(RememberConf.cookieName(), "no-such-selector:no-such-validator");

		stale.visit("/me", context -> Remember.restore(context, LOOKUP));

		assertEquals(1, rowCount(), "無関係な記憶まで消している");

		/*
		 * <b>死んだ Cookie は消しておく。</b>残すと、来るたびに DB を1回引いて
		 * <b>何も起きない</b>ということを、切れるまで繰り返す。
		 */
		assertNull(stale.cookie(RememberConf.cookieName()), "もう使えない Cookie を消していない");

	}

	// endregion

	// region 忘れる

	@Test
	@DisplayName("F-W-30 ログアウトすると記憶も消える")
	void logoutForgets () {

		Browser browser = new Browser();

		browser.get("/me", context -> {
			Auth.login(context, ALICE);
			Remember.issue(context, ALICE);
			return null;
		});

		browser.get("/me", context -> {
			Auth.logout(context);
			return null;
		});

		assertEquals(0, rowCount(), "ログアウトしたのに記憶が残っている");

		/*
		 * <b>ここを消し忘れると、ログアウトの次のリクエストでまた入る。</b>
		 * 画面上はログアウトできたように見えるので、共用の端末で踏むまで気づけない。
		 */
		Principal restored = browser.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(restored.isAuthenticated(), "ログアウトしたのにまた入っている");

	}

	@Test
	@DisplayName("F-W-30 全部忘れさせられる（パスワードを変えたとき）")
	void forgetAllRemovesEveryDevice () {

		new Browser().visit("/me", context -> Remember.issue(context, ALICE));
		new Browser().visit("/me", context -> Remember.issue(context, ALICE));

		assertEquals(2, rowCount());

		assertEquals(2, Remember.forgetAll(ALICE.id()));
		assertEquals(0, rowCount(), "パスワードを変えても盗まれた Cookie が生き残る");

	}

	@Test
	@DisplayName("F-W-30 利用者が消えていれば、記憶も消す")
	void forgetsWhenTheUserIsGone () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));
		browser.forgetSession();

		Principal restored = browser.get("/me", context -> {
			Remember.restore(context, id -> null);
			return Auth.principal(context);
		});

		assertFalse(restored.isAuthenticated());

		/*
		 * <b>残すと、id を作り直したときに別人が入る。</b>
		 */
		assertEquals(0, rowCount(), "退会した人の記憶が残っている");

	}

	// endregion

	// region 期限

	@Test
	@DisplayName("F-W-30 最後に使ってから間が空けば切れる")
	void expiresAfterTheSlidingWindow () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));
		browser.forgetSession();

		long old = Instant.now().minus(Duration.ofDays(31)).toEpochMilli();
		DBUtil.getMainDB().update("UPDATE %s SET last_used_at = ?".formatted(table()), old);

		Principal restored = browser.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(restored.isAuthenticated(), "30 日使っていないのに思い出している");
		assertEquals(0, rowCount());

	}

	@Test
	@DisplayName("F-W-30 使い続けていても、発行から 90 日で切れる")
	void expiresAfterTheAbsoluteLimit () {

		Browser browser = new Browser();

		browser.visit("/me", context -> Remember.issue(context, ALICE));
		browser.forgetSession();

		/*
		 * <b>滑る期限だけだと、毎日来る人の Cookie は永遠に有効なままになる。</b>
		 * 盗まれたことに誰も気づかなければ、盗んだ側も永遠に入れる。
		 */
		long old = Instant.now().minus(Duration.ofDays(91)).toEpochMilli();
		DBUtil.getMainDB().update("UPDATE %s SET created_at = ?".formatted(table()), old);

		Principal restored = browser.get("/me", context -> {
			Remember.restore(context, LOOKUP);
			return Auth.principal(context);
		});

		assertFalse(restored.isAuthenticated(), "使い続ければいつまでも切れない");
		assertEquals(0, rowCount());

	}

	@Test
	@DisplayName("F-W-30 切れたものを掃除できる")
	void cleanupRemovesExpiredRows () {

		new Browser().visit("/me", context -> Remember.issue(context, ALICE));
		new Browser().visit("/me", context -> Remember.issue(context, ALICE));

		String selector = DBUtil.getMainDB()
			.select("SELECT selector FROM %s".formatted(table())).getString("selector");

		long old = Instant.now().minus(Duration.ofDays(31)).toEpochMilli();
		DBUtil.getMainDB().update("UPDATE %s SET last_used_at = ? WHERE selector = ?"
			.formatted(table()), old, selector);

		assertEquals(1, Remember.cleanup());
		assertEquals(1, rowCount(), "生きているほうまで消している");

	}

	// endregion

	// region ブラウザのふり

	/**
	 * Cookie を持ち回る「ブラウザ」
	 */
	private static final class Browser {

		/* 受信済みの Cookie */
		private final Map<String, String> cookies = new LinkedHashMap<>();

		/**
		 * 1リクエスト
		 *
		 * @param path		パス
		 * @param action	中でやること
		 * @param <T>		戻り値
		 * @return	action の戻り値
		 */
		<T> T request (String path, ThrowingFunction<T> action) {

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", path);
			cookies.forEach(source::cookie);

			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			T result;

			try (WebContext context = new WebContext(source, sink)) {

				context.route(router.match("GET", path));

				try {
					result = action.apply(context);
				} catch (RuntimeException | Error cause) {
					throw cause;
				} catch (Exception cause) {
					throw new IllegalStateException(cause);
				}

				context.response().send("ok");

			}

			receive(sink);

			return result;

		}

		/**
		 * 1リクエスト
		 *
		 * @param path		パス
		 * @param action	中でやること
		 * @param <T>		戻り値
		 * @return	action の戻り値
		 */
		<T> T get (String path, ThrowingFunction<T> action) {

			return request(path, action);

		}

		/**
		 * 1リクエスト（戻り値なし）
		 *
		 * @param path		パス
		 * @param action	中でやること
		 */
		void visit (String path, ThrowingAction action) {

			request(path, context -> {
				action.run(context);
				return null;
			});

		}

		/**
		 * Set-Cookie を受け取る
		 *
		 * @param sink	出力口
		 */
		private void receive (Fakes.FakeResponseSink sink) {

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

		}

		/** Cookie を読む */
		String cookie (String name) {
			return cookies.get(name);
		}

		/** Cookie を置く（盗んだふりをする） */
		void setCookie (String name, String value) {
			cookies.put(name, value);
		}

		/** セッションだけ捨てる（ブラウザを閉じたのと同じ） */
		void forgetSession () {
			cookies.remove(SessionConf.cookieName());
		}

	}

	/**
	 * 例外を投げてよい処理
	 *
	 * @param <T>	戻り値
	 */
	@FunctionalInterface
	private interface ThrowingAction {

		/**
		 * 処理する
		 *
		 * @param context	コンテキスト
		 * @throws Exception	処理中の例外
		 */
		void run (WebContext context) throws Exception;

	}

	/**
	 * 例外を投げてよい処理（戻り値あり）
	 *
	 * @param <T>	戻り値
	 */
	@FunctionalInterface
	private interface ThrowingFunction<T> {

		/**
		 * 処理する
		 *
		 * @param context	コンテキスト
		 * @return	戻り値
		 * @throws Exception	処理中の例外
		 */
		T apply (WebContext context) throws Exception;

	}

	// endregion

	// region 補助

	/**
	 * テーブル名
	 *
	 * @return	テーブル名
	 */
	private static String table () {

		return DBUtil.getMainDB().dialect().identifier("auth_remember");

	}

	/**
	 * 行数
	 *
	 * @return	行数
	 */
	private static int rowCount () {

		Data row = DBUtil.getMainDB().select("SELECT count(*) as cnt FROM %s".formatted(table()));

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * Cookie の selector
	 *
	 * @param cookie	Cookie の値
	 * @return	selector
	 */
	private static String selectorOf (String cookie) {

		return cookie.substring(0, cookie.indexOf(':'));

	}

	// endregion

}
