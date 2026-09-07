package io.jimble.batch.manager;

import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.mq.SchedulerQueue;
import io.jimble.batch.status.BatchHistoryStatus;
import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleApp;
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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * バッチ管理画面（要件 F-B-11）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-batch-manager:dbTest
 * </pre>
 */
@Tag("db")
class BatchManagerIntegrationTest {

	/** パス */
	static final String PATH = "/jimble/batch/manager";

	/** ユーザー名 */
	static final String USER = "admin";

	/** パスワード */
	static final String PASSWORD = "s3cret";

	/**
	 * テスト用のバッチ
	 */
	public static class SampleBatch extends AbstractBatch {

		@Override public String batchName () { return "サンプル"; }
		@Override public String cron () { return "0 3 * * *"; }
		@Override public void execute (BatchArgs args) { }

	}

	/* 認証つきのアプリ */
	private static JimbleServer server;

	/* 認証情報が無いアプリ */
	private static JimbleServer openServer;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startAll () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), BatchManagerIntegrationTest.class), "DB に接続できませんでした");

		BatchTables.install(DBUtil.getMainDB());
		SchedulerQueue.queue().install();

		server = JimbleServer.start(new JimbleApp() {
			{
				install(() -> new BatchManagerController(PATH, USER, PASSWORD, "test"));
			}
		}, 0);

		// 認証情報を渡さないと組み込まれないこと
		openServer = JimbleServer.start(new JimbleApp() {
			{
				get("/ping", context -> context.response().send("pong"));
				install(() -> new BatchManagerController(PATH, "", "", "test"));
			}
		}, 0);

		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopAll () {

		if (server != null) {
			server.stop();
		}

		if (openServer != null) {
			openServer.stop();
		}

		BatchRegistry.clear();
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE batch_master");
		db.execute("TRUNCATE TABLE batch_history");
		db.execute("TRUNCATE TABLE batch_execute_info");
		db.execute("TRUNCATE TABLE `%s`".formatted(SchedulerQueue.name()));

		BatchRegistry.clear();
		BatchRegistry.add(SampleBatch::new);
		BatchRegistry.sync(db);

	}

	// region 補助

	/**
	 * 認証つきで叩く
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param body		本文（無ければ null）
	 * @return	応答
	 */
	private HttpResponse<String> request (String method, String path, String body) throws Exception {

		return request(method, path, body, USER, PASSWORD);

	}

	/**
	 * 叩く
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param body		本文（無ければ null）
	 * @param user		ユーザー名（null なら認証ヘッダを付けない）
	 * @param password	パスワード
	 * @return	応答
	 */
	private HttpResponse<String> request (String method, String path, String body
		, String user, String password) throws Exception {

		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.method(method, body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body));

		if (user != null) {
			builder.header("Authorization", "Basic " + Base64.getEncoder()
				.encodeToString("%s:%s".formatted(user, password).getBytes(StandardCharsets.UTF_8)));
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

	}

	/**
	 * バッチマスタの行
	 *
	 * @return	行
	 */
	private Data master () {

		return DBUtil.getMainDB().select("SELECT * FROM batch_master WHERE class_name = ?"
			, SampleBatch.class.getName());

	}

	// endregion

	// region 認証（要件 F-B-11）

	@Test
	@DisplayName("認証が無ければ 401")
	void unauthenticated () throws Exception {

		HttpResponse<String> response = request("GET", PATH + "/api/status", null, null, null);

		assertEquals(401, response.statusCode());
		assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("Basic")
			, response.headers().toString());

	}

	@Test
	@DisplayName("パスワードが違えば 401")
	void wrongPassword () throws Exception {

		assertEquals(401, request("GET", PATH + "/api/status", null, USER, "wrong").statusCode());

	}

	@Test
	@DisplayName("認証情報が無ければ画面そのものを組み込まない")
	void notMountedWithoutCredentials () throws Exception {

		/*
		 * 移送元は Basic 認証が public static の差し替え可能なフィールドで、
		 * 設定し忘れると管理 API が誰でも叩ける状態で公開されていた。
		 */
		HttpResponse<String> ping = client.send(HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + openServer.port() + "/ping"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, ping.statusCode(), "アプリ自体は動いていること");

		HttpResponse<String> manager = client.send(HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + openServer.port() + PATH + "/api/status"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertEquals(404, manager.statusCode(), "認証なしで管理 API が生えている");

	}

	// endregion

	// region 画面と状態

	@Test
	@DisplayName("画面が返る")
	void page () throws Exception {

		HttpResponse<String> response = request("GET", PATH + "/", null);

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("jimble batch manager"), response.body());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));

	}

	@Test
	@DisplayName("全体の状態が読める")
	void status () throws Exception {

		HttpResponse<String> response = request("GET", PATH + "/api/status", null);

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("batch_enabled"), response.body());
		assertTrue(response.body().contains("scheduler_enabled"), response.body());

	}

	@Test
	@DisplayName("全体の入り切りができる")
	void statusChange () throws Exception {

		assertEquals(200, request("POST", PATH + "/api/status/change"
			, "{\"kind\":\"scheduler\",\"enabled\":false}").statusCode());

		assertFalse(io.jimble.batch.scheduler.SchedulerControl.isEnabled());

		assertEquals(200, request("POST", PATH + "/api/status/change"
			, "{\"kind\":\"scheduler\",\"enabled\":true}").statusCode());

		assertTrue(io.jimble.batch.scheduler.SchedulerControl.isEnabled());

	}

	@Test
	@DisplayName("知らない kind は 400")
	void unknownKind () throws Exception {

		// 移送元は黙って 200 を返していた
		assertEquals(400, request("POST", PATH + "/api/status/change"
			, "{\"kind\":\"nothing\",\"enabled\":true}").statusCode());

	}

	// endregion

	// region バッチ

	@Test
	@DisplayName("バッチ一覧が読める")
	void masterList () throws Exception {

		HttpResponse<String> response = request("POST", PATH + "/api/master", "{}");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains(SampleBatch.class.getName()), response.body());

	}

	@Test
	@DisplayName("cron を保存できる")
	void masterSave () throws Exception {

		HttpResponse<String> response = request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName()
			, "{\"cron\":\"*/10 * * * *\"}");

		assertEquals(200, response.statusCode(), response.body());
		assertEquals("*/10 * * * *", master().getString("cron"));

	}

	@Test
	@DisplayName("読めない cron は 400。保存もしない")
	void masterSaveInvalidCron () throws Exception {

		/*
		 * 移送元は確かめずに保存していた。
		 * 打ち間違えるとスケジューラが黙って読み飛ばし、そのバッチだけ動かなくなる。
		 */
		String before = master().getString("cron");

		HttpResponse<String> response = request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName()
			, "{\"cron\":\"まいにち\"}");

		assertEquals(400, response.statusCode());
		assertEquals(before, master().getString("cron"), "保存されてしまっている");

	}

	@Test
	@DisplayName("有効・無効を切り替えられる")
	void masterStatus () throws Exception {

		assertEquals(200, request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName() + "/status"
			, "{\"status\":\"disable\"}").statusCode());

		assertEquals(BatchMasterStatus.disable.name(), master().getString("status"));

		assertEquals(200, request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName() + "/status"
			, "{\"status\":\"enable\"}").statusCode());

		assertEquals(BatchMasterStatus.enable.name(), master().getString("status"));

	}

	@Test
	@DisplayName("nothing は付けられない")
	void masterStatusNothing () throws Exception {

		// 「コードから消えた」を表す内部の値。人が付けるものではない
		assertEquals(400, request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName() + "/status"
			, "{\"status\":\"nothing\"}").statusCode());

		assertEquals(BatchMasterStatus.enable.name(), master().getString("status"));

	}

	@Test
	@DisplayName("知らないバッチは 404")
	void masterNotFound () throws Exception {

		assertEquals(404, request("GET", PATH + "/api/master/app.batch.NotExists", null).statusCode());

	}

	@Test
	@DisplayName("スケジューラが動いていなければ「いま動かす」は 409")
	void executeWithoutScheduler () throws Exception {

		HttpResponse<String> response = request("POST"
			, PATH + "/api/master/" + SampleBatch.class.getName() + "/execute", "{}");

		assertEquals(409, response.statusCode(), response.body());

	}

	// endregion

	// region 履歴

	/**
	 * 履歴を1件入れる
	 *
	 * @param status	ステータス
	 * @return	ID
	 */
	private long insertHistory (BatchHistoryStatus status) {

		return DBUtil.getMainDB().insert("""
				INSERT INTO batch_history (class_name, name, status, cancel_status, execute_info, starts_at)
				VALUES (?, ?, ?, 0, ?, NOW())
			"""
			, SampleBatch.class.getName()
			, "サンプル"
			, status.name()
			, new Data().putData("args", new Data().putData("site_id", "42")));

	}

	@Test
	@DisplayName("履歴一覧と詳細が読める")
	void historyList () throws Exception {

		long id = insertHistory(BatchHistoryStatus.completed);

		HttpResponse<String> list = request("POST", PATH + "/api/history", "{}");

		assertEquals(200, list.statusCode());
		assertTrue(list.body().contains("\"id\":" + id), list.body());

		HttpResponse<String> detail = request("GET", PATH + "/api/history/" + id, null);

		assertEquals(200, detail.statusCode());
		assertTrue(detail.body().contains("site_id"), detail.body());

	}

	@Test
	@DisplayName("クラス名とステータスで絞れる")
	void historyFilter () throws Exception {

		insertHistory(BatchHistoryStatus.completed);
		insertHistory(BatchHistoryStatus.error);

		HttpResponse<String> response = request("POST", PATH + "/api/history"
			, "{\"status\":[\"error\"]}");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("error"), response.body());
		assertFalse(response.body().contains("completed"), response.body());

		assertEquals(200, request("POST", PATH + "/api/history"
			, "{\"class_name\":\"app.batch.NotExists\"}").statusCode());

	}

	@Test
	@DisplayName("実行中の履歴だけ中断できる")
	void historyCancel () throws Exception {

		long running = insertHistory(BatchHistoryStatus.in_process);
		long done = insertHistory(BatchHistoryStatus.completed);

		assertEquals(200, request("POST", PATH + "/api/history/" + running + "/cancel", "{}").statusCode());

		Data row = DBUtil.getMainDB().select("SELECT cancel_status FROM batch_history WHERE id = ?", running);

		assertNotNull(row);
		assertTrue(row.getBoolean("cancel_status"), "中断の指示が入っていない");

		assertEquals(409, request("POST", PATH + "/api/history/" + done + "/cancel", "{}").statusCode()
			, "終わった履歴を中断できてしまう");

	}

	@Test
	@DisplayName("無い履歴は 404")
	void historyNotFound () throws Exception {

		assertEquals(404, request("GET", PATH + "/api/history/999999", null).statusCode());
		assertEquals(404, request("POST", PATH + "/api/history/999999/cancel", "{}").statusCode());

	}

	@Test
	@DisplayName("状態を変える操作は GET では通らない")
	void mutationsAreNotGet () throws Exception {

		/*
		 * 移送元は中断・やり直し・実行が GET だった。
		 * リンクを踏んだだけで、クローラが辿っただけでバッチが動く。
		 */
		long id = insertHistory(BatchHistoryStatus.in_process);

		assertEquals(404, request("GET", PATH + "/api/history/" + id + "/cancel", null).statusCode());
		assertEquals(404, request("GET", PATH + "/api/history/" + id + "/re-execute", null).statusCode());
		assertEquals(404, request("GET"
			, PATH + "/api/master/" + SampleBatch.class.getName() + "/execute", null).statusCode());

	}

	// endregion

}
