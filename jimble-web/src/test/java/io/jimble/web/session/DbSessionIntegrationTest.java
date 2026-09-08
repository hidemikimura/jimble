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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertTrue(
			DBUtil.load(Conf.conf().config(), DbSessionIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS %s".formatted(quoted()));
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();
		DBUtil.getMainDB().execute("DELETE FROM %s".formatted(quoted()));

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

		assertEquals(1, SessionStores.db().cleanupExpired());
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
			.select("SELECT COUNT(*) AS cnt FROM %s".formatted(quoted())).getLong("cnt");

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
