package io.jimble.web.session;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB セッションが実 DB に対して動くことの確認（要件 F-S-01 / F-S-09）
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-web:dbTest
 * </pre>
 */
@Tag("db")
class DbSessionIntegrationTest {

	/** テーブル名 */
	private static final String TABLE = "session_test";

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbSessionIntegrationTest.class);

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS %s".formatted(quoted()));
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();
		try {
			DBUtil.getMainDB().execute("DELETE FROM %s".formatted(quoted()));
		} catch (io.jimble.db.SqlExecuteException notYet) {
			// 表はストアが最初に使うときに作る。まだ無ければ消すものも無い
		}

	}

	// region テスト

	@Test
	@DisplayName("保存すると行ができ、次のリクエストで読める")
	void saveAndLoad () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		String sessionId;

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 42);
			context.session().put("name", "きむら");
			context.session().save();
			context.response().send("ok");
		}

		sessionId = sessionIdFrom(sink);
		assertNotNull(sessionId, "セッション ID の Cookie が出ていない");

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals(42, context.session().getInt("user_id"));
			assertEquals("きむら", context.session().get("name"));
		}

	}

	@Test
	@DisplayName("D-260 absolute_timeout を書いたら、発行から過ぎたセッションは使い続けていても読めず、ID も作り直す")
	void absoluteTimeoutWhenWritten () {

		withAbsoluteTimeout("1h", () -> {

			String sessionId = saveUser(42);

			// 使い続けている（last_accessed_at は新しい）が、発行は2時間前
			age(sessionId);

			Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
			source.cookie(SessionConf.cookieName(), sessionId);
			Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

			try (WebContext context = new WebContext(source, sink)) {
				assertEquals("", context.session().get("user_id"), "発行から過ぎたセッションが読めた");
				context.session().put("user_id", 7);
				context.session().save();
				context.response().send("ok");
			}

			// 同じ ID で書き直すと created_at が古いまま残り、二度と使えなくなるので、ID を作り直している
			String renewed = lastSessionIdFrom(sink);
			assertNotNull(renewed);
			assertNotEquals(sessionId, renewed);

			Fakes.FakeRequestSource again = new Fakes.FakeRequestSource("GET", "/");
			again.cookie(SessionConf.cookieName(), renewed);

			try (WebContext context = new WebContext(again, new Fakes.FakeResponseSink())) {
				assertEquals(7, context.session().getInt("user_id"), "作り直したセッションが読めない");
			}

		});

	}

	@Test
	@DisplayName("D-260 absolute_timeout を書かなければ、DB セッションは発行からの時間では切らない（2.2.3 までと同じ）")
	void noAbsoluteTimeoutByDefault () {

		String sessionId = saveUser(42);
		age(sessionId);

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals(42, context.session().getInt("user_id"));
		}

	}

	@Test
	@DisplayName("2回保存しても行は1つ（同時実行でデータが消えない）")
	void saveTwiceUpserts () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");
		}

		String sessionId = sessionIdFrom(sink);

		// 同じセッション ID で「未読み込みのまま保存」する。
		// 移送元は INSERT IGNORE だったので、この2回目が黙って捨てられていた
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			context.session().put("user_id", 2);
			context.session().save();
		}

		assertEquals(1, count());

		Fakes.FakeRequestSource again = new Fakes.FakeRequestSource("GET", "/");
		again.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(again, new Fakes.FakeResponseSink())) {
			assertEquals(2, context.session().getInt("user_id"), "2回目の保存が捨てられている");
		}

	}

	@Test
	@DisplayName("D-284 期限切れの行を少しずつ消し切る（1回に消す件数を区切る）。生きている行は残す")
	void cleanupInBatches () {

		DbSessionStore store = new DbSessionStore(TABLE, 30);

		// 表を作らせる
		store.cleanupExpired();

		List<List<Object>> rows = new ArrayList<>();
		for (int i = 0; i < DbSessionStore.CLEANUP_BATCH * 2 + 500; i++) {
			rows.add(List.of("old-" + i));
		}

		DBUtil.getMainDB().executeBatch(
			"INSERT INTO %s (session_id, data, created_at, last_accessed_at) VALUES (?, NULL, '2000-01-01 00:00:00', '2000-01-01 00:00:00')"
				.formatted(quoted())
			, rows);
		DBUtil.getMainDB().execute(
			"INSERT INTO %s (session_id, data, created_at, last_accessed_at) VALUES ('live', NULL, NOW(), NOW())".formatted(quoted()));

		assertEquals(DbSessionStore.CLEANUP_BATCH * 2 + 500, store.cleanupExpired());
		assertEquals(1, count(), "生きている行まで消えた");

	}

	@Test
	@DisplayName("D-275 読んだあとでログアウト（destroy）されたら、そのリクエストの保存は書き戻さない")
	void saveAfterDestroyDoesNotResurrect () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");
		}

		String sessionId = sessionIdFrom(sink);

		// 攻撃者のリクエスト（盗んだ ID）。先に読み込む
		Fakes.FakeRequestSource attackerSource = new Fakes.FakeRequestSource("GET", "/cart");
		attackerSource.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext attacker = new WebContext(attackerSource, new Fakes.FakeResponseSink())) {

			assertEquals(1, attacker.session().getInt("user_id"));

			// そのあいだに本人がログアウトした
			Fakes.FakeRequestSource victimSource = new Fakes.FakeRequestSource("POST", "/logout");
			victimSource.cookie(SessionConf.cookieName(), sessionId);

			try (WebContext victim = new WebContext(victimSource, new Fakes.FakeResponseSink())) {
				victim.session().destroy();
			}

			assertEquals(0, count());

			// 攻撃者のリクエストが保存する（カートに入れた、など）
			attacker.session().put("cart", "x");
			attacker.session().save();

		}

		assertEquals(0, count(), "ログアウトしたセッションが書き戻された");

		Fakes.FakeRequestSource again = new Fakes.FakeRequestSource("GET", "/");
		again.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(again, new Fakes.FakeResponseSink())) {
			assertEquals(0, context.session().getInt("user_id"), "ログアウトしたのに、まだログインしている");
		}

	}

	@Test
	@DisplayName("F-S-13 regenerateId() で ID が変わり、中身は残り、古い行は消える")
	void regenerateId () {

		// 1. ログイン前のセッション（攻撃者が仕込んだ ID のつもり）
		Fakes.FakeResponseSink first = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/login"), first)) {
			context.session().put("back_to", "/requests");
			context.session().save();
			context.response().send("ok");
		}

		String before = sessionIdFrom(first);
		assertNotNull(before);
		assertEquals(1, count());

		// 2. ログインが通ったとして、振り直す
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/login");
		source.cookie(SessionConf.cookieName(), before);

		Fakes.FakeResponseSink second = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, second)) {

			context.session().regenerateId();
			context.session().put("staff_id", 7);
			context.session().save();

			context.response().send("ok");

		}

		String after = sessionIdFrom(second);

		assertNotNull(after, "新しいセッション ID の Cookie が出ていない");
		assertNotEquals(before, after, "ID が変わっていない（セッション固定化を防げていない）");

		/*
		 * <b>行は1つ。</b>古いほうが残っていると、
		 * 攻撃者は仕込んだ ID でそのまま入れてしまう。
		 */
		assertEquals(1, count(), "古い行が残っている");

		// 3. 古い ID ではもう入れない
		Fakes.FakeRequestSource old = new Fakes.FakeRequestSource("GET", "/me");
		old.cookie(SessionConf.cookieName(), before);

		try (WebContext context = new WebContext(old, new Fakes.FakeResponseSink())) {
			assertEquals(0, context.session().getInt("staff_id"), "古い ID でログイン後のセッションが引ける");
		}

		// 4. 新しい ID では、振り直す前に入れたものも残っている
		Fakes.FakeRequestSource fresh = new Fakes.FakeRequestSource("GET", "/me");
		fresh.cookie(SessionConf.cookieName(), after);

		try (WebContext context = new WebContext(fresh, new Fakes.FakeResponseSink())) {
			assertEquals(7, context.session().getInt("staff_id"));
			assertEquals("/requests", context.session().get("back_to"), "振り直しで中身が消えている");
		}

	}

	@Test
	@DisplayName("F-S-13 先に save() していても、振り直したあとの save() が効く")
	void regenerateAfterSaveStillSaves () {

		Fakes.FakeResponseSink first = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/login"), first)) {
			context.session().put("back_to", "/requests");
			context.session().save();
			context.response().send("ok");
		}

		String before = sessionIdFrom(first);

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/login");
		source.cookie(SessionConf.cookieName(), before);

		Fakes.FakeResponseSink second = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, second)) {

			/*
			 * <b>同じリクエストの中で、先に1回保存してから振り直す。</b>
			 *
			 * save() は「1リクエストに1回」なので、振り直しで印を戻さないと
			 * <b>あとの save() が黙って帰る</b>——古い側は消えているので、
			 * <b>ログインしたのにログインしていない</b>状態になる。
			 * 例外は出ず、次のリクエストで 401 になるだけなので、原因が遠い。
			 */
			context.session().put("step", "1");
			context.session().save();

			context.session().regenerateId();
			context.session().put("staff_id", 7);
			context.session().save();

			context.response().send("ok");

		}

		String after = sessionIdFrom(second);

		assertNotNull(after, "新しいセッション ID の Cookie が出ていない");
		assertNotEquals(before, after);

		Fakes.FakeRequestSource fresh = new Fakes.FakeRequestSource("GET", "/me");
		fresh.cookie(SessionConf.cookieName(), after);

		try (WebContext context = new WebContext(fresh, new Fakes.FakeResponseSink())) {
			assertEquals(7, context.session().getInt("staff_id")
				, "振り直したあとの save() が効いていない（先に save() していると捨てられる）");
		}

	}

	@Test
	@DisplayName("F-S-13 振り直したあと保存しなければ、どちらの ID でも入れない")
	void regenerateWithoutSaveLeavesNoSession () {

		Fakes.FakeResponseSink first = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/login"), first)) {
			context.session().put("staff_id", 7);
			context.session().save();
			context.response().send("ok");
		}

		String before = sessionIdFrom(first);

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/login");
		source.cookie(SessionConf.cookieName(), before);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			// 振り直しただけで save() しない
			context.session().regenerateId();
		}

		/*
		 * <b>閉じるほうに倒れること。</b>
		 * 古い側は消えていて、新しい側は書かれていない——つまりログインしていない。
		 * 逆（古い側が生き残る）だと、<b>振り直したつもりで固定化が残る。</b>
		 */
		assertEquals(0, count(), "保存していないのに行が残っている");

		Fakes.FakeRequestSource old = new Fakes.FakeRequestSource("GET", "/me");
		old.cookie(SessionConf.cookieName(), before);

		try (WebContext context = new WebContext(old, new Fakes.FakeResponseSink())) {
			assertEquals(0, context.session().getInt("staff_id"), "古い ID でまだ入れる");
		}

	}

	@Test
	@DisplayName("destroy() で行も Cookie も消える")
	void destroy () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");
		}

		String sessionId = sessionIdFrom(sink);
		assertEquals(1, count());

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);
		Fakes.FakeResponseSink destroySink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, destroySink)) {
			context.session().destroy();
			context.response().send("ok");
		}

		assertEquals(0, count());
		assertTrue(destroySink.setCookies().stream().anyMatch(c -> c.contains("Max-Age=0")),
			destroySink.setCookies().toString());

	}

	@Test
	@DisplayName("期限切れは読めない")
	void expired () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");
		}

		String sessionId = sessionIdFrom(sink);

		// 最終アクセスを過去にする
		DBUtil.getMainDB().update(
			"UPDATE %s SET last_accessed_at = %s WHERE session_id = ?"
				.formatted(quoted(), DBUtil.getMainDB().dialect().intervalFromNow("DAY", true))
			, 1, sessionId);

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(SessionConf.cookieName(), sessionId);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertTrue(context.session().data().isEmpty());
		}

	}

	@Test
	@DisplayName("cleanupExpired() が期限切れの行を消す（F-S-09）")
	void cleanupExpired () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");
		}

		DBUtil.getMainDB().execute(
			"UPDATE %s SET last_accessed_at = %s"
				.formatted(quoted(), DBUtil.getMainDB().dialect().intervalFromNow("DAY", true))
			, 1);

		/*
		 * <b>具体クラスを名指ししない。</b>{@code SessionStores.db()} と書くと、
		 * 設定を Redis に変えた日に<b>DB のセッション表だけを掃除しつづける</b>。
		 * ここを通しておけば、{@link SessionStore#cleanupExpired()} の
		 * 上書きが外れたとき（既定の 0 に落ちたとき）に落ちる。
		 */
		assertEquals(1, SessionStores.defaultStore().cleanupExpired());
		assertEquals(0, count());

	}

	@Test
	@DisplayName("セッションを触らなければ行も Cookie もできない（F-S-12）")
	void untouchedCreatesNothing () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.response().send("ok");
		}

		assertEquals(0, count());
		assertTrue(sink.setCookies().isEmpty());

	}

	// endregion

	// region ヘルパー

	/**
	 * セッションテーブル名を製品に合わせて囲む（要件 F-D-30）
	 *
	 * @return	囲んだテーブル名
	 */
	private static String quoted () {

		return DBUtil.getMainDB().dialect().identifier(TABLE);

	}

	/**
	 * 行数
	 *
	 * @return	行数
	 */
	private long count () {

		return DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS cnt FROM %s".formatted(quoted())).orElseThrow().getLong("cnt");

	}

	/** 設定に absolute_timeout を書いて動かす */
	private static void withAbsoluteTimeout (String value, Runnable body) {

		com.typesafe.config.Config original = Conf.conf().config();

		Conf.replace(com.typesafe.config.ConfigFactory.parseString("session.absolute_timeout = " + value).withFallback(original));
		SessionStores.reset();

		try {
			body.run();
		} finally {
			Conf.replace(original);
			SessionStores.reset();
		}

	}

	/** 保存して ID を返す */
	private String saveUser (int userId) {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.session().put("user_id", userId);
			context.session().save();
			context.response().send("ok");
		}

		return sessionIdFrom(sink);

	}

	/** 発行を2時間前にする（最後に使ったのはいま） */
	private static void age (String sessionId) {

		// DB の時計で書く（JVM と DB のタイムゾーンが違っても、ずれない）
		DBUtil.getMainDB().update("UPDATE %s SET created_at = %s WHERE session_id = ?"
			.formatted(quoted(), DBUtil.getMainDB().dialect().intervalFromNow("HOUR", true)), 2, sessionId);

	}

	/** 最後に出したセッション ID（消す Cookie は飛ばす） */
	private String lastSessionIdFrom (Fakes.FakeResponseSink sink) {

		String prefix = SessionConf.cookieName() + "=";
		String found = null;

		for (String setCookie : sink.setCookies()) {
			if (setCookie.startsWith(prefix)) {
				String value = setCookie.substring(prefix.length(), setCookie.indexOf(';'));
				if (!value.isEmpty()) {
					found = value;
				}
			}
		}

		return found;

	}

	/**
	 * Set-Cookie からセッション ID を取り出す
	 *
	 * @param sink	レスポンス
	 * @return	セッション ID（無ければ null）
	 */
	private String sessionIdFrom (Fakes.FakeResponseSink sink) {

		String prefix = SessionConf.cookieName() + "=";

		for (String setCookie : sink.setCookies()) {
			if (setCookie.startsWith(prefix)) {
				return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
			}
		}

		return null;

	}

	// endregion

}
