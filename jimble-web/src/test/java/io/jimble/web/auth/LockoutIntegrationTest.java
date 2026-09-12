package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.hash.PasswordUtil;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ログイン失敗を数えるところが実 DB に対して動くことの確認（要件 F-W-29）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-web:dbTest
 * ./gradlew :jimble-web:pgTest
 * </pre>
 */
@Tag("db")
class LockoutIntegrationTest {

	/** 正しいパスワード */
	private static final String PASSWORD = "correct horse battery staple";

	/** 保存してあるハッシュ */
	private static String passwordHash;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), LockoutIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		passwordHash = PasswordUtil.createHash(PASSWORD);

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		// テーブルは Lockout が作る。まだ無いこともあるので、1回触って作らせる
		Lockout.waitSeconds("warm-up");

		DBUtil.getMainDB().execute("DELETE FROM %s".formatted(table()));

	}

	// region 数える

	@Test
	@DisplayName("F-W-29 打ち間違いのうちは待たされない")
	void freeAttemptsDoNotWait () {

		Lockout.fail("alice");
		Lockout.fail("alice");
		Lockout.fail("alice");

		assertEquals(0, Lockout.waitSeconds("alice")
			, "3回目までで待たされている（打ち間違いで反応が遅いサイトになる）");

	}

	@Test
	@DisplayName("F-W-29 4回目から待たされ、失敗するほど伸びる")
	void waitGrowsWithFailures () {

		for (int i = 0; i < 4; i++) {
			Lockout.fail("alice");
		}

		touch("alice");
		assertEquals(1, Lockout.waitSeconds("alice"));

		Lockout.fail("alice");

		touch("alice");
		assertEquals(2, Lockout.waitSeconds("alice"));

	}

	@Test
	@DisplayName("F-W-29 同時に来ても数えそこねない")
	void countsEveryFailure () throws Exception {

		/*
		 * <b>読んでから足すと、ここで落ちる。</b>
		 * 総当たりは並列で来るので、10 本同時に失敗させて 10 と数えられるかを見る。
		 */
		Thread[] threads = new Thread[10];

		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(() -> Lockout.fail("alice"));
			threads[i].start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		assertEquals(10, failedCount("alice"), "同時の失敗を数えそこねている");

	}

	@Test
	@DisplayName("F-W-29 綴りを変えても同じ単位で数える")
	void spellingDoesNotResetTheCount () {

		Lockout.fail("alice");
		Lockout.fail("Alice");
		Lockout.fail("ALICE");
		Lockout.fail("  alice  ");

		assertEquals(4, failedCount("alice")
			, "大小や空白を変えるだけで何回でも試せてしまう");

		touch("alice");
		assertEquals(1, Lockout.waitSeconds("alice"));

	}

	@Test
	@DisplayName("F-W-29 待っただけ、待ち時間が減る")
	void waitShrinksAsTimePasses () {

		/*
		 * <b>経過を引かないと、待ち時間はいつまでも減らない。</b>
		 * 「あと 64 秒」と言われた人が、64 秒待ってもまだ 64 秒待たされる——
		 * それは<b>「遅くする」ではなく「締め出す」</b>で、作りたかったものと違う。
		 */
		insertRow("alice", 10, Instant.now().minus(Duration.ofSeconds(30)).toEpochMilli());

		long seconds = Lockout.waitSeconds("alice");

		// 10 回失敗 = 2^6 = 64 秒。30 秒経っているので、残りは 34 秒あたり
		assertTrue(seconds >= 30 && seconds <= 34
			, "経過を引いていない（待っても減らない）: %d".formatted(seconds));

	}

	// endregion

	// region 消す・忘れる

	@Test
	@DisplayName("F-W-29 解除すると数えたものが消える")
	void clearRemovesTheRow () {

		for (int i = 0; i < 5; i++) {
			Lockout.fail("alice");
		}

		assertTrue(Lockout.waitSeconds("alice") > 0);

		Lockout.clear("alice");

		assertNull(row("alice"), "行が残っている");
		assertEquals(0, Lockout.waitSeconds("alice"));

	}

	@Test
	@DisplayName("F-W-29 間が空いたら数え直す")
	void forgetsAfterTheInterval () {

		insertRow("alice", 20, Instant.now().minus(Duration.ofHours(48)).toEpochMilli());

		assertEquals(0, Lockout.waitSeconds("alice")
			, "半年前に間違えた人が、今日いきなり待たされている");

		Lockout.fail("alice");

		assertEquals(1, failedCount("alice"), "数え直していない（20 に足している）");

	}

	@Test
	@DisplayName("F-W-29 上限を長くしても、間が空けば忘れる")
	void staysForgottenEvenWithALongMaximum () {

		/*
		 * <b>「経過を引く」だけでは足りない。</b>
		 * 既定（上限 300 秒 / 24 時間で忘れる）なら、忘れる頃には経過が上限を追い越しているので
		 * 引き算だけで 0 になる——が、<b>上限を forget_hours より長くした設定</b>では追い越さない。
		 *
		 * そのとき「忘れる」を見ていないと、<b>24 時間で忘れる約束のはずが数週間待たされる</b>。
		 */
		Config original = Conf.conf().config();

		try {

			Conf.replace(ConfigFactory
				.parseString("auth.lockout.max = 3000000s")
				.withFallback(original));

			// 25 回失敗 = 2^21 = 約 24 日。48 時間経ったくらいでは引き切れない
			insertRow("alice", 25, Instant.now().minus(Duration.ofHours(48)).toEpochMilli());

			assertEquals(0, Lockout.waitSeconds("alice")
				, "24 時間で忘れる約束なのに、まだ待たせている");

		} finally {
			Conf.replace(original);
		}

	}

	@Test
	@DisplayName("F-W-29 古い記録を掃除できる")
	void cleanupRemovesOldRows () {

		insertRow("old", 5, Instant.now().minus(Duration.ofHours(48)).toEpochMilli());
		Lockout.fail("fresh");

		assertTrue(Lockout.cleanup() >= 1);

		assertNull(row("old"), "古い行が残っている（放っておくと増え続ける）");
		assertNotNull(row("fresh"), "新しい行まで消している");

	}

	// endregion

	// region attemptLogin

	@Test
	@DisplayName("F-W-29 正しく入れれば通り、数えたものが消える")
	void successClearsTheCount () {

		Lockout.fail("alice");
		Lockout.fail("alice");

		try (WebContext context = Fakes.context("POST", "/login")) {
			assertTrue(Auth.attemptLogin(context, "alice", PASSWORD, passwordHash));
		}

		assertNull(row("alice")
			, "成功しても数えたものが残っている（正しく入れた人が翌日待たされる）");

	}

	@Test
	@DisplayName("F-W-29 間違えれば通らず、数えられる")
	void failureCounts () {

		try (WebContext context = Fakes.context("POST", "/login")) {
			assertFalse(Auth.attemptLogin(context, "alice", "wrong", passwordHash));
		}

		assertEquals(1, failedCount("alice"));

	}

	@Test
	@DisplayName("F-W-29 いない相手でも数える")
	void countsWhenTheUserDoesNotExist () {

		/*
		 * <b>ここを数えないと、待たされるかどうかで「その ID が在るか」が分かる。</b>
		 * 総当たりは存在しない ID から始まるので、数えないほうが穴になる。
		 */
		try (WebContext context = Fakes.context("POST", "/login")) {
			assertFalse(Auth.attemptLogin(context, "nobody", "whatever", null));
		}

		assertEquals(1, failedCount("nobody"));

	}

	@Test
	@DisplayName("F-W-29 待ち時間が残っていれば 429 で、Retry-After を付ける")
	void refusesWhileWaiting () {

		for (int i = 0; i < 4; i++) {
			Lockout.fail("alice");
		}

		touch("alice");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("POST", "/login"), sink)) {

			HttpException thrown = assertThrows(HttpException.class
				, () -> Auth.attemptLogin(context, "alice", PASSWORD, passwordHash)
				, "待ち時間が残っているのに照合してしまっている");

			assertEquals(429, thrown.statusCode());
			assertEquals("1", sink.headers().get("Retry-After"));

			assertFalse(thrown.getMessage().contains("1")
				, "あと何秒かをメッセージに書いている（何回失敗した状態かが分かる）");

		}

	}

	@Test
	@DisplayName("F-W-29 待たせている間は、正しいパスワードでも数え直さない")
	void doesNotCountWhileWaiting () {

		for (int i = 0; i < 4; i++) {
			Lockout.fail("alice");
		}

		touch("alice");

		try (WebContext context = Fakes.context("POST", "/login")) {
			assertThrows(HttpException.class, () -> Auth.attemptLogin(context, "alice", "wrong", passwordHash));
		}

		/*
		 * <b>待たせている間の試行を数えると、待ち時間が自分で伸びていく。</b>
		 * 待たされている人は「待って、やり直す」しかできなくなる。
		 */
		assertEquals(4, failedCount("alice"), "待たせている間の試行まで数えている");

	}

	@Test
	@DisplayName("F-W-29 コンテキストが無くても落ちない")
	void worksWithoutAContext () {

		for (int i = 0; i < 4; i++) {
			Lockout.fail("alice");
		}

		touch("alice");

		/*
		 * バッチや API 鍵の照合など、<b>レスポンスが無いところから呼ばれる</b>ことがある。
		 * Retry-After を付けられないだけで、断ることは断る。
		 */
		HttpException thrown = assertThrows(HttpException.class
			, () -> Auth.attemptLogin(null, "alice", PASSWORD, passwordHash));

		assertEquals(429, thrown.statusCode());

	}

	// endregion

	// region 補助

	/**
	 * テーブル名
	 *
	 * @return	テーブル名
	 */
	private static String table () {

		return DBUtil.getMainDB().dialect().identifier("auth_attempt");

	}

	/**
	 * 数える単位のハッシュ
	 *
	 * @param key	数える単位
	 * @return	ハッシュ
	 */
	private static String hashed (String key) {

		return Hash.sha256(key.strip().toLowerCase(java.util.Locale.ROOT));

	}

	/**
	 * 1件読む
	 *
	 * @param key	数える単位
	 * @return	行。無ければ null
	 */
	private static Data row (String key) {

		return DBUtil.getMainDB().select(
			"SELECT failed_count, last_failed_at FROM %s WHERE attempt_key = ?".formatted(table())
			, hashed(key));

	}

	/**
	 * 失敗回数
	 *
	 * @param key	数える単位
	 * @return	回数
	 */
	private static long failedCount (String key) {

		Data row = row(key);

		return row == null ? 0 : row.getLong("failed_count");

	}

	/**
	 * 最後に失敗した時刻を「いま」にする
	 *
	 * <p>
	 * <b>待ち時間は「要る秒数 - 経過秒数」である。</b>
	 * 失敗を数えるところと時刻を揃えておかないと、
	 * 遅い環境で<b>数ミリ秒の差でテストが落ちる</b>。
	 * </p>
	 *
	 * @param key	数える単位
	 */
	private static void touch (String key) {

		DBUtil.getMainDB().update(
			"UPDATE %s SET last_failed_at = ? WHERE attempt_key = ?".formatted(table())
			, Instant.now().toEpochMilli(), hashed(key));

	}

	/**
	 * 行を作る
	 *
	 * @param key			数える単位
	 * @param failedCount	失敗回数
	 * @param lastFailedAt	最後に失敗した時刻
	 */
	private static void insertRow (String key, long failedCount, long lastFailedAt) {

		DBUtil.getMainDB().insert(
			"INSERT INTO %s (attempt_key, failed_count, last_failed_at) VALUES (?, ?, ?)".formatted(table())
			, hashed(key), failedCount, lastFailedAt);

	}

	// endregion

}
