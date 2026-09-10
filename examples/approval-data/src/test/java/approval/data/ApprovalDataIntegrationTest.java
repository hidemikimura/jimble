package approval.data;

import db.approval_data_audit_example.ApprovalDataAuditExample;
import db.approval_data_example.ApprovalDataExample;
import db.approval_data_example.table.rate.Rate;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.ICache;
import io.jimble.db.migration.code.CodeMigration;
import io.jimble.db.redis.RedisClient;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.redis.lock.RedisLock;
import io.jimble.db.redis.lock.RedisLockResult;
import io.jimble.db.redis.lock.RedisLockStatus;
import io.jimble.db.value.DBValue;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleServer;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-data）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p><b>開発用 DB が2つ要る</b>（要件 D-16）。どちらも設定の {@code create_database_sql} で作られる。</p>
 *
 * <pre>
 * ./gradlew :examples:approval-data:pgTest
 * </pre>
 */
@Tag("db")
class ApprovalDataIntegrationTest {

	/** 初期データの申請の件数 */
	private static final int SEEDED = 5;

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		Bootstrap.load();

		server = JimbleServer.start(new DataApp(), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = ApprovalDataExample.db();

		db.execute("TRUNCATE TABLE notice");
		db.execute("TRUNCATE TABLE request");
		db.execute("TRUNCATE TABLE rate");
		db.execute("TRUNCATE TABLE db_cache");

		db.execute("""
				insert into request (staff_id, amount, status, created_at) values
					  (1,  12000, 'pending',  now())
					, (1,  35000, 'pending',  now())
					, (2,   4800, 'pending',  now())
					, (2, 120000, 'pending',  now())
					, (3,   9800, 'approved', now())
			""");

		db.execute("""
				insert into rate (code, value, updated_at) values
					  ('JPY', 1,   now())
					, ('USD', 150, now())
					, ('EUR', 165, now())
			""");

		// 監査ログは別の DB
		ApprovalDataAuditExample.db().execute("TRUNCATE TABLE audit_log");

	}

	// region 2つの DB（要件 F-D-14 / F-G-09）

	@Test
	@DisplayName("F-G-09 データソースごとにマイグレーションが当たっている")
	void bothDatabasesMigrated () {

		/*
		 * <b>トップレベルの db { } にしてあるから当たる。</b>
		 * subs にぶら下げていたら、監査ログのテーブルは存在しない。
		 */
		assertNotNull(ApprovalDataExample.db()
			.select("SELECT 1 AS ok FROM request LIMIT 1"), "メインのテーブルが無い");

		assertNotNull(ApprovalDataAuditExample.db()
			.select("SELECT COUNT(1) AS cnt FROM audit_log"), "監査 DB のテーブルが無い");

		// 履歴もデータソースごとにできる
		assertNotNull(ApprovalDataAuditExample.db()
			.select("SELECT COUNT(1) AS cnt FROM migration"), "監査 DB に migration が無い");

	}

	@Test
	@DisplayName("F-D-14 メインからは監査ログのテーブルが見えない")
	void auditIsSeparate () {

		DB db = ApprovalDataExample.db();

		Data row = db.select("SELECT COUNT(1) AS cnt FROM audit_log");

		/*
		 * <b>例外ではなく戻り値で返る</b>（要件 F-D-11）。
		 * null が返り、isError() が立つ。
		 */
		assertNull(row, "メインから監査ログが見えている");
		assertTrue(db.isError(), "エラーが立っていない");

	}

	@Test
	@DisplayName("F-D-14 ぶら下げたサブ DB は、同じ DB を指していても接続が別")
	void subDbIsSeparateConnection () throws Exception {

		DB main = ApprovalDataExample.db();
		DB scratch = ApprovalDataExample.scratchDB();

		assertNotSame(main, scratch);

		/*
		 * <b>トランザクションを共有しない。</b>
		 *
		 * 同じデータベースを指しているので「共有してもよさそう」に見えるが、
		 * 別のコネクションプールから別の接続を取っている。
		 * 片方で始めたトランザクションは、もう片方から見えない。
		 *
		 * ここを取り違えると、<b>片方だけ入る</b>事故になる。
		 * jimble は分散トランザクションをやらない。
		 */
		try (DBTransaction transaction = new DBTransaction(main)) {

			transaction.beginTransaction();

			main.execute("insert into rate (code, value, updated_at) values ('TMP', 1, now())");

			// 別の接続からは、まだ見えない
			assertNull(scratch.select("SELECT id FROM rate WHERE code = 'TMP'")
				, "サブ DB から未確定の行が見えている（接続を共有している）");

			transaction.rollbackEndTransaction();

		}

		// 戻したので、どちらからも見えない
		assertNull(main.select("SELECT id FROM rate WHERE code = 'TMP'"));

	}

	// endregion

	// region 承認（要件 F-D-15 / F-D-16）

	@Test
	@DisplayName("F-D-15 承認すると、状態と通知が両方できる")
	void approveWritesBoth () throws Exception {

		long id = firstPendingId();

		HttpResponse<String> response = post("/requests/" + id + "/approve", "by=9");

		assertEquals(200, response.statusCode(), response.body());

		assertEquals("approved", ApprovalDataExample.db()
			.select("SELECT status FROM request WHERE id = ?", id).getString("status"));

		assertEquals(1, count(ApprovalDataExample.db()
			, "SELECT COUNT(1) AS cnt FROM notice WHERE request_id = " + id));

	}

	@Test
	@DisplayName("F-D-15 もう決まっている申請は 409。何も変わらない")
	void approveTwice () throws Exception {

		long id = firstPendingId();

		assertEquals(200, post("/requests/" + id + "/approve", "by=9").statusCode());

		int noticesBefore = count(ApprovalDataExample.db(), "SELECT COUNT(1) AS cnt FROM notice");

		assertEquals(409, post("/requests/" + id + "/approve", "by=9").statusCode());

		// 通知が二重にできない（ロールバックされている）
		assertEquals(noticesBefore, count(ApprovalDataExample.db()
			, "SELECT COUNT(1) AS cnt FROM notice"));

	}

	@Test
	@DisplayName("F-D-15 無い申請は 404。通知もできない")
	void approveMissing () throws Exception {

		assertEquals(404, post("/requests/999999/approve", "by=9").statusCode());

		assertEquals(0, count(ApprovalDataExample.db(), "SELECT COUNT(1) AS cnt FROM notice"));

	}

	@Test
	@DisplayName("F-D-14 承認すると監査ログが別の DB に残る")
	void approveWritesAudit () throws Exception {

		long id = firstPendingId();

		post("/requests/" + id + "/approve", "by=9");

		Data row = ApprovalDataAuditExample.db().select(
			"SELECT staff_id, action, target FROM audit_log ORDER BY id DESC LIMIT 1");

		assertNotNull(row, "監査ログが書かれていない");
		assertEquals("approve", row.getString("action"));
		assertEquals("request:" + id, row.getString("target"));
		assertEquals(9, row.getInt("staff_id"));

	}

	@Test
	@DisplayName("F-D-14 承認が失敗したら監査ログも残らない")
	void failedApproveLeavesNoAudit () throws Exception {

		post("/requests/999999/approve", "by=9");

		/*
		 * 別の DB なのでトランザクションでは守られない。
		 * <b>確定したあとに書く</b>という順序だけが守っている。
		 */
		assertEquals(0, count(ApprovalDataAuditExample.db()
			, "SELECT COUNT(1) AS cnt FROM audit_log"), "失敗したのに監査ログがある");

	}

	@Test
	@DisplayName("F-D-16 通知が弾かれたら、変えた状態も戻る")
	void approveRollsBackWhenNoticeRejected () throws Exception {

		long id = firstPendingId();

		/*
		 * 同じ申請・同じ種類の通知を<b>先に</b>入れておく。
		 *
		 * 承認は「状態を変える → 通知を作る」の順なので、
		 * notice__request_kind の一意キーに<b>後半だけが</b>弾かれる。
		 * これがトランザクションを実際に戻らせる唯一の道である
		 * （404 も 409 も、1行も書く前に抜けてしまう）。
		 */
		ApprovalDataExample.db().execute(
			"insert into notice (request_id, to_staff_id, kind, created_at) values (?, 1, 'approved', now())"
			, id);

		HttpResponse<String> response = post("/requests/" + id + "/approve", "by=9");

		assertEquals(500, response.statusCode(), response.body());

		/*
		 * <b>ここが本題。</b>
		 * 囲っていなければ、状態だけ approved になって通知が無い申請が残る。
		 * 誰も例外を見ないので、気づくのは<b>通知が来ないと言われたとき</b>である。
		 */
		assertEquals("pending", ApprovalDataExample.db()
			.select("SELECT status FROM request WHERE id = ?", id).getString("status")
			, "通知が入らなかったのに、状態だけ変わっている");

		assertNull(ApprovalDataExample.db()
			.select("SELECT decided_by FROM request WHERE id = ? AND decided_by IS NOT NULL", id)
			, "決めた人だけ残っている");

		// 確定していないので監査ログも書かれない
		assertEquals(0, count(ApprovalDataAuditExample.db()
			, "SELECT COUNT(1) AS cnt FROM audit_log"), "戻ったのに監査ログがある");

	}

	@Test
	@DisplayName("F-D-16 畳み忘れたトランザクションは実行の終わりに戻る")
	void unclosedTransactionRollsBack () {

		/*
		 * わざと閉じずに抜ける。
		 * <b>実行（Context）の終わりに拾ってロールバックし、エラーログを出す。</b>
		 * 拾わないと、接続がプールへ戻らないまま溜まる。
		 */
		io.jimble.core.context.BatchContext context =
			new io.jimble.core.context.BatchContext("畳み忘れ");

		try (context) {

			context.run(() -> {

				DB db = ApprovalDataExample.db();

				DBTransaction transaction = new DBTransaction(db);

				try {
					transaction.beginTransaction();
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

				db.execute("insert into rate (code, value, updated_at) values ('LEAK', 1, now())");

				// 閉じない

			});

		}

		assertNull(ApprovalDataExample.db().select("SELECT id FROM rate WHERE code = 'LEAK'")
			, "畳み忘れたトランザクションが確定している");

	}

	// endregion

	// region 一括登録（要件 F-D-08）

	@Test
	@DisplayName("F-D-08 まとめて登録すると、採番値がその数だけ返る")
	void importInsertsAll () throws Exception {

		HttpResponse<String> response = post("/import", "count=50");

		assertEquals(200, response.statusCode(), response.body());
		assertEquals(50, number(response.body(), "inserted"), response.body());

		assertEquals(SEEDED + 50, count(ApprovalDataExample.db()
			, "SELECT COUNT(1) AS cnt FROM request"));

		/*
		 * <b>戻るのは件数ではなく採番値である。</b>
		 * executeBatch（件数）とは型が違う。
		 */
		assertTrue(number(response.body(), "first_id") > 0, response.body());

	}

	@Test
	@DisplayName("F-D-08 SQL が揃っていない一括登録は止まる（値が横にずれない）")
	void importRejectsMismatchedSql () {

		/*
		 * <b>value() の並びが違うと SQL が変わる。</b>
		 *
		 * 直すまでは、先頭の SQL に全員のパラメータを流し込んでいた。
		 * 個数が合っていると DB も気づかず、<b>値が入れ替わって入る</b>。
		 */
		DB target = ApprovalDataExample.db();

		List<Long> ids = target.insertBatch(List.of(
			SQL.insert(Rate.instance())
				.value(Rate.code, "AAA")
				.value(Rate.value, 1L)
				.value(Rate.updated_at, Dsl.now())
			, SQL.insert(Rate.instance())
				.value(Rate.value, 2L)
				.value(Rate.code, "BBB")
				.value(Rate.updated_at, Dsl.now())
		));

		assertNull(ids, "SQL が違うのに通っている");
		assertEquals("DB_998", target.getError().getCode(), String.valueOf(target.getError()));

		// 1件も入っていない
		assertEquals(0, count(target, "SELECT COUNT(1) AS cnt FROM rate WHERE code IN ('AAA','BBB')"));

	}

	// endregion

	// region キャッシュとロック（要件 F-U-07 / F-U-08 / F-U-09）

	@Test
	@DisplayName("F-U-07 2回目はキャッシュから返る")
	void rateIsCached () throws Exception {

		assertEquals("db", text(get("/rates").body(), "from"), get("/rates").body());

		// 1回目でキャッシュに乗ったので、2回目はそちらから
		assertEquals("cache", text(get("/rates").body(), "from"), get("/rates").body());

	}

	@Test
	@DisplayName("F-U-08 書き換えるとキャッシュがまとまりごと消える")
	void cacheIsInvalidated () throws Exception {

		// キャッシュに乗せる
		get("/rates");
		assertEquals("cache", text(get("/rates").body(), "from"));

		assertEquals(200, post("/rates", "code=USD&value=999").statusCode());

		/*
		 * <b>1件だけ消すのではなく、まとまりごと捨てる。</b>
		 * 消し忘れて古いものが出続けるほうが怖い。
		 */
		String after = get("/rates").body();

		assertEquals("db", text(after, "from"), "キャッシュが古いまま: " + after);
		assertTrue(after.contains("999"), after);

		/*
		 * <b>キャッシュから返るときも、同じものが返らなければ意味がない。</b>
		 *
		 * ここを見ていなかったせいで、キャッシュに Data.toString() を入れていたのを
		 * 見逃しかけた——当たったときだけ
		 * "Data(1件) {rates=ArrayList(3件)}" という壊れた応答が返っていた。
		 */
		String fromCache = get("/rates").body();

		assertEquals("cache", text(fromCache, "from"), fromCache);
		assertTrue(fromCache.contains("999"), "キャッシュから返る中身が壊れている: " + fromCache);
		assertTrue(fromCache.contains("USD"), "キャッシュから返る中身が壊れている: " + fromCache);

	}

	@Test
	@DisplayName("F-U-07 置き場を変えてもアプリのコードは同じ")
	void cacheStoreIsSwappable () {

		/*
		 * <b>設定を読んでいるだけのように見えるが、そうではない。</b>
		 * ここで確かめているのは「インターフェースで受けている」こと——
		 * 実装を名指しで書いていたら、この差し替えでアプリが壊れる。
		 */
		DB db = ApprovalDataExample.db();

		ICache dbCache = Cache.instance(db);

		dbCache.set("k", "v", "text/plain", "swap");
		assertEquals("v", dbCache.getString("k", "swap"));

		dbCache.removeGroup("swap");
		assertTrue(dbCache.getString("k", "swap") == null
			|| dbCache.getString("k", "swap").isEmpty());

	}

	@Test
	@DisplayName("F-U-09 Redis が設定してあれば分散ロックが取れる")
	void redisLock () throws Exception {

		/*
		 * application.pgtest.conf にだけ redis を書いてある。
		 * 既定の設定（application.conf）には無い——
		 * <b>Redis は前提ではない</b>（要件 F-U-10）ことを形にするためである。
		 */
		assertTrue(RedisClient.isConfigured(), "テストの設定に redis が無い");

		try (RedisLockResult lock = RedisLock.tryLock("approval-data-test", 1000, 5000)) {

			assertEquals(RedisLockStatus.Success, lock.status());

			// 同じ鍵は取れない…と言いたいが、同じスレッドは再入で取れてしまう
			// （そこは jimble-db 側のテストが見ている）

		}

	}

	// endregion

	// region DBValue とコードマイグレーション（要件 F-Y-15 / F-G-18）

	@Test
	@DisplayName("F-Y-15 DBValue は読むだけのつもりで書き込む")
	void dbValueWritesOnRead () throws Exception {

		DB db = ApprovalDataExample.db();

		db.execute("DELETE FROM db_value WHERE value_key = ?", RateController.KEY_IMPORTED_ON);

		assertEquals(0, count(db, "SELECT COUNT(1) AS cnt FROM db_value WHERE value_key = '"
			+ RateController.KEY_IMPORTED_ON + "'"));

		// 読むだけ
		assertEquals("-", text(get("/imported-on").body(), "imported_on"));

		/*
		 * <b>行ができている。</b>
		 * 「無ければ既定値を書き込む」ので、読むだけのつもりで INSERT が飛ぶ。
		 */
		assertEquals(1, count(db, "SELECT COUNT(1) AS cnt FROM db_value WHERE value_key = '"
			+ RateController.KEY_IMPORTED_ON + "'"), "読んだのに行ができていない");

	}

	@Test
	@DisplayName("F-Y-15 書けば次から読める")
	void dbValueRoundTrip () throws Exception {

		assertEquals(200, post("/imported-on", "on=2026-09-10").statusCode());
		assertEquals("2026-09-10", text(get("/imported-on").body(), "imported_on"));

	}

	@Test
	@DisplayName("F-G-18 コードマイグレーションが記録され、値が丸まる")
	void codeMigrationRoundsAndRecords () {

		DB db = ApprovalDataExample.db();

		// 起動のときに走っている（Bootstrap に登録されている）
		assertNotNull(db.select("SELECT version FROM migration_code WHERE version LIKE ?"
			, "20260910-%"), "起動のときに走っていない");

		/*
		 * <b>消してから、もう一度走らせる。</b>
		 *
		 * 記録が在ることだけを見ていると、
		 * <b>前に走ったときの行が残っているだけ</b>で通ってしまう
		 * （登録を消しても、開発用の DB では気づけない）。
		 * 消してから呼べば、いま登録されているものが走ったと言える。
		 */
		db.execute("DELETE FROM migration_code WHERE version LIKE '20260910-%'");

		db.execute("TRUNCATE TABLE rate");
		db.execute("""
				insert into rate (code, value, updated_at) values
					  ('USD', 151, now())
					, ('EUR', 165, now())
					, ('GBP', 190, now())
			""");

		CodeMigration.execute();

		Data row = db.select(
			"SELECT state, execute_info FROM migration_code WHERE version LIKE ?"
			, "20260910-%");

		assertNotNull(row, "コードマイグレーションの記録が無い");
		assertEquals("completed", row.getString("state"), row.toString());

		// 何をしたかが JSON で残る。丸めたのは2件
		assertEquals(2, row.getDataOptional("execute_info").getInt("rounded")
			, row.getStringOptional("execute_info"));

		assertEquals(150, rateOf("USD"), "151 が丸まっていない");
		assertEquals(170, rateOf("EUR"), "165 が丸まっていない（四捨五入）");
		assertEquals(190, rateOf("GBP"), "もともと丸い値が動いている");

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>参照用と書込用が本当に別の接続であることは見ていない。</b>
	 *   このサンプルは read に同じ DB を書いているので、外から見て差が出ない。
	 *   分けたときに sticky が効くことは jimble-db 側（DbInfraIntegrationTest）が見ている
	 * - <b>DBLock が実際に待たせることは見ていない。</b>
	 *   別スレッドから同じ鍵を取りにいく形が要り、テストが時間に依存する。
	 *   トランザクションの中でしか効かないことも含め、jimble-db 側が見ている
	 * - <b>cache.type = memory / redis での動きは見ていない。</b>
	 *   ここで見ているのは「インターフェースで受けている」ことまでで、
	 *   実装ごとの挙動は jimble-db 側のテストが見ている
	 * - <b>DBValue のキャッシュが DB 名を持たない件</b>は踏ませていない。
	 *   キーの頭を分けて避けている（避けなければ混ざる）
	 * - <b>コードマイグレーションが落ちたときに起動が止まること</b>は見ていない。
	 *   サンプルの起動そのものが止まるので、テストの中で作れない
	 */

	// endregion

	// region 道具

	/**
	 * 最初の pending な申請の id
	 *
	 * @return	id
	 */
	private static long firstPendingId () {

		return ApprovalDataExample.db()
			.select("SELECT id FROM request WHERE status = 'pending' ORDER BY id LIMIT 1")
			.getLong("id");

	}

	/**
	 * レートの値
	 *
	 * @param code	コード
	 * @return	値
	 */
	private static long rateOf (String code) {

		return ApprovalDataExample.db()
			.select("SELECT value FROM rate WHERE code = ?", code).getLong("value");

	}

	/**
	 * 数える
	 *
	 * @param db	DB
	 * @param sql	SQL
	 * @return	件数
	 */
	private static int count (DB db, String sql) {

		Data row = db.select(sql);

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * GET する
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * POST する
	 *
	 * @param path	パス
	 * @param body	本文
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (String path, String body) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * JSON から数を1つ取る
	 *
	 * @param body	本文
	 * @param name	名前
	 * @return	数
	 */
	private static long number (String body, String name) {

		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("%s が返っていません: %s".formatted(name, body));
		}

		return Long.parseLong(matcher.group(1));

	}

	/**
	 * JSON から文字列を1つ取る
	 *
	 * @param body	本文
	 * @param name	名前
	 * @return	文字列
	 */
	private static String text (String body, String name) {

		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("%s が返っていません: %s".formatted(name, body));
		}

		return matcher.group(1);

	}

	// endregion

}
