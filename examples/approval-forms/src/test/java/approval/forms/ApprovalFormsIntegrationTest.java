package approval.forms;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-forms）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>PostgreSQL が要る。</p>
 *
 * <pre>
 * ./gradlew :examples:approval-forms:pgTest
 * </pre>
 */
@Tag("db")
class ApprovalFormsIntegrationTest {

	/* サーバー */
	private static JimbleServer server;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		Bootstrap.load();

		server = JimbleServer.start(new FormsApp(), 0);

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

	}

	// region 検証

	@Test
	@DisplayName("F-V-04 検証に落ちると 422 で、保存は走らない")
	void validationStopsTheSave () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client
			, "kind", "travel"
			, "amount", "0"
			, "submit", "true"
			, "items[0][name]", "新幹線"
			, "items[0][amount]", "14000"));

		assertEquals(422, response.statusCode(), response.body());
		assertTrue(response.body().contains("\"validation\""), response.body());
		assertTrue(response.body().contains("amount"), response.body());

		// 保存されていない（id が返っていない）
		assertFalse(response.body().contains("\"id\""), response.body());

	}

	@Test
	@DisplayName("F-W-09 検証に落ちても、送った値が返ってくる（入力し直さずに済む）")
	void submittedValuesComeBack () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client
			, "kind", "travel"
			, "amount", "0"
			, "note", "大阪出張のぶん"
			, "submit", "true"
			, "items[0][name]", "新幹線"
			, "items[0][amount]", "14000"));

		assertEquals(422, response.statusCode());

		/*
		 * <b>ここが無いと画面を組み直せない。</b>
		 * putForm に入れた値が応答へ載っていなかったので直した（D-133）
		 */
		assertTrue(response.body().contains("大阪出張のぶん")
			, "送った値が返ってきていません: " + response.body());

	}

	@Test
	@DisplayName("F-V-07 空は empty() でしか落ちない（型のバリデータは空を通す）")
	void emptyIsOnlyCaughtByEmpty () throws Exception {

		HttpClient client = newClient();

		// 下書きなので必須にならない。金額が空でも通る
		HttpResponse<String> draft = post(client, form(client, "kind", "travel"));

		assertEquals(201, draft.statusCode(), draft.body());

	}

	// endregion

	// region 下書きと提出で必須が変わる（要件 F-V-02）

	@Test
	@DisplayName("F-V-02 下書きなら金額が無くても保存できる")
	void draftAllowsMissingAmount () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client, "kind", "supply"));

		assertEquals(201, response.statusCode(), response.body());

		long id = idOf(response.body());
		assertTrue(id > 0, response.body());

		assertTrue(get(client, "/requests/" + id).body().contains("draft"));

	}

	@Test
	@DisplayName("F-V-02 提出なら金額も明細も要る（同じルールを使い回している）")
	void submitRequiresAmountAndItems () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client
			, "kind", "supply"
			, "submit", "true"));

		assertEquals(422, response.statusCode(), response.body());

		/*
		 * <b>「送られてこなかった項目」も落ちること。</b>
		 * 素朴に書くと「来ていないものは見ない」になり、
		 * <b>金額の欄を消して送れば通ってしまう</b>
		 */
		assertTrue(response.body().contains("amount"), response.body());
		assertTrue(response.body().contains("明細を1行以上"), response.body());

	}

	// endregion

	// region 明細（ネストパラメータ。要件 F-W-03）

	@Test
	@DisplayName("F-W-03 明細が items[0][name] の形で読めて、並び順どおりに入る")
	void nestedItems () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client
			, "kind", "travel"
			, "amount", "30000"
			, "submit", "true"
			, "items[0][name]", "新幹線"
			, "items[0][amount]", "14000"
			, "items[1][name]", "宿"
			, "items[1][amount]", "16000"));

		assertEquals(201, response.statusCode(), response.body());

		String body = get(client, "/requests/" + idOf(response.body())).body();

		assertTrue(body.contains("新幹線"), body);
		assertTrue(body.contains("宿"), body);

		// 並び順（sort_no）が送った順であること
		assertTrue(body.indexOf("新幹線") < body.indexOf("宿"), body);

	}

	@Test
	@DisplayName("F-V-03 明細のエラーは「何行目の何」で返る")
	void itemErrorsSayWhichRow () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = post(client, form(client
			, "kind", "travel"
			, "amount", "30000"
			, "submit", "true"
			, "items[0][name]", "新幹線"
			, "items[0][amount]", "14000"
			, "items[1][name]", ""
			, "items[1][amount]", "16000"));

		assertEquals(422, response.statusCode(), response.body());

		/*
		 * <b>「品目を入れてください」だけでは足りない。</b>
		 * 5行あったらどれのことか分からない
		 */
		assertTrue(response.body().contains("items[1][name]")
			, "何行目か分かりません: " + response.body());

		assertFalse(response.body().contains("items[0][name]")
			, "落ちていない行まで返しています: " + response.body());

	}

	// endregion

	// region 型付きアクセサ（要件 F-G-02 / F-D-22）

	@Test
	@DisplayName("F-G-02 生成した型付きアクセサで読み書きできる")
	void generatedAccessors () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> created = post(client, form(client
			, "kind", "book"
			, "amount", "3200"
			, "needed_on", "2026-10-01"
			, "submit", "true"
			, "items[0][name]", "技術書"
			, "items[0][amount]", "3200"));

		assertEquals(201, created.statusCode(), created.body());

		String body = get(client, "/requests/" + idOf(created.body())).body();

		assertTrue(body.contains("book"), body);
		assertTrue(body.contains("3200"), body);
		assertTrue(body.contains("pending"), body);

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>添付（F-W-06）は形だけ通している。</b>multipart で送るテストは
	 *   examples/blog の formWithUpload が見ているので、ここでは重ねていない
	 * - <b>ValidationMessages の差し替え</b>（文言のカスタマイズ）は見ていない。
	 *   プロセス全体で共有する static なので、結合テストで触ると他へ漏れる
	 * - <b>金額の上限</b>（1,000,000）そのものは見ていない。境界は単体テストの仕事
	 */

	// endregion

	/**
	 * Cookie を持ち回るクライアントを1つ作る
	 *
	 * @return	クライアント
	 */
	private static HttpClient newClient () {

		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}

	/**
	 * CSRF トークン込みのフォーム本文を組む
	 *
	 * @param client	クライアント
	 * @param pairs		名前・値の並び
	 * @return	本文
	 * @throws Exception	失敗した場合
	 */
	private static String form (HttpClient client, String...pairs) throws Exception {

		List<String> parts = new ArrayList<>();
		parts.add("csrf_token=" + enc(csrfToken(client)));

		for (int index = 0; index + 1 < pairs.length; index += 2) {
			parts.add(enc(pairs[index]) + "=" + enc(pairs[index + 1]));
		}

		return String.join("&", parts);

	}

	/**
	 * 入力画面を開いて CSRF トークンを取る
	 *
	 * @param client	クライアント
	 * @return	トークン
	 * @throws Exception	失敗した場合
	 */
	private static String csrfToken (HttpClient client) throws Exception {

		String html = get(client, "/requests/new").body();

		Matcher matcher = Pattern
			.compile("name=\"csrf_token\" value=\"([^\"]+)\"")
			.matcher(html);

		if (!matcher.find()) {
			throw new AssertionError("CSRF トークンがフォームに出ていません: " + html);
		}

		return matcher.group(1);

	}

	/**
	 * 応答から id を取る
	 *
	 * @param body	本文
	 * @return	id
	 */
	private static long idOf (String body) {

		Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("id が返っていません: " + body);
		}

		return Long.parseLong(matcher.group(1));

	}

	/**
	 * URL エンコードする
	 *
	 * @param value	値
	 * @return	エンコードしたもの
	 */
	private static String enc (String value) {

		return URLEncoder.encode(value, StandardCharsets.UTF_8);

	}

	/**
	 * GET する
	 *
	 * @param client	クライアント
	 * @param path		パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (HttpClient client, String path) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * 申請を送る
	 *
	 * @param client	クライアント
	 * @param body		本文
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (HttpClient client, String body) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/requests"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

}
