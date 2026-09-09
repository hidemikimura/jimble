package blog;

import db.blog_example.BlogExample;
import db.blog_example.table.comment.Comment;
import db.blog_example.table.post.Post;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプルアプリを実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * <b>ドキュメントに書いた URL がそのまま動くことを機械で確かめる。</b>
 * サンプルは「これが動く＝その機能が動く」ためのものなので、
 * 気づかないうちに壊れていると、いちばん困る形で表に出る。
 * </p>
 *
 * <p>
 * 開発用 DB が要る（要件 D-16）。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:blog:dbTest
 * </pre>
 */
@Tag("db")
class BlogAppIntegrationTest {

	/* サーバー */
	private static JimbleServer server;

	/* クライアント。Cookie を持ち回る（CSRF と Flash を通すため） */
	private static HttpClient client;

	// docs:begin test-db-app
	@BeforeAll
	static void startServer () {

		Conf.reload();

		/*
		 * <b>本番の入口と同じものを呼ぶ。</b>
		 * ここでテストだけの用意を書くと、入口が変わったときに<b>テストだけ通る</b>。
		 */
		Bootstrap.load();

		// ポート 0 で空いているところを使う。他のテストと衝突しない
		server = JimbleServer.start(new BlogApp(), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}
	// docs:end

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

	}

	// region 記事

	@Test
	@DisplayName("一覧と HTML が返る")
	void listAndHtml () throws Exception {

		long id = insertPost("一覧に出る記事");

		HttpResponse<String> json = get("/posts");
		assertEquals(200, json.statusCode());
		assertTrue(json.body().contains("一覧に出る記事"), json.body());

		HttpResponse<String> html = get("/");
		assertEquals(200, html.statusCode());
		assertTrue(html.headers().firstValue("content-type").orElse("").contains("text/html")
			, html.headers().map().toString());
		assertTrue(html.body().contains("一覧に出る記事"), html.body());

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("登録するとキューにも積まれる（同じトランザクション）")
	void createPost () throws Exception {

		HttpResponse<String> response = send("POST", "/posts"
			, HttpRequest.BodyPublishers.ofString(
				"{\"title\":\"作った記事\",\"body\":\"本文\",\"published\":true}", StandardCharsets.UTF_8)
			, "Content-Type", "application/json");

		assertEquals(201, response.statusCode());

		long id = Data.fromJsonString(response.body()).getLong("id");
		assertTrue(id > 0, response.body());

		// お知らせが同じトランザクションで積まれている（要件 F-M-03）
		assertEquals(1, queuedCount(id), "キューに積まれていない");

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("一覧にコメントを付けても、先読みでまとまる（要件 F-A-06）")
	void prefetchedComments () throws Exception {

		long a = insertPost("先読み1");
		long b = insertPost("先読み2");

		insertComment(a, "きむら", "1へのコメント");
		insertComment(b, "さとう", "2へのコメント1");
		insertComment(b, "たなか", "2へのコメント2");

		HttpResponse<String> response = get("/posts/with-comments");

		assertEquals(200, response.statusCode());

		List<Data> posts = Data.fromJsonString(response.body()).getDataList("posts");

		/*
		 * 中身が個別読みのときと同じであることを見る。
		 * <b>何本 SQL が飛んだか</b>は HTTP からは見えないので、
		 * そちらは AsyncIntegrationTest（実 DB）で数えている。
		 */
		Data first = null;
		Data second = null;

		for (Data post : posts) {
			if (post.getLong(Post.id) == a) { first = post; }
			if (post.getLong(Post.id) == b) { second = post; }
		}

		assertNotNull(first, response.body());
		assertNotNull(second, response.body());

		assertEquals(1, first.getDataList("comments").size(), response.body());
		assertEquals(2, second.getDataList("comments").size(), response.body());
		assertEquals("さとう"
			, second.getDataList("comments").get(0).getString(Comment.name), response.body());

		delete("/posts/" + a);
		delete("/posts/" + b);

	}

	@Test
	@DisplayName("コメントは参照されたときに読まれる（AsyncList）")
	void asyncComments () throws Exception {

		long id = insertPost("コメントのある記事");
		insertComment(id, "きむら", "こんにちは");
		insertComment(id, "さとう", "どうも");

		HttpResponse<String> response = get("/posts/" + id);

		assertEquals(200, response.statusCode());

		Data post = Data.fromJsonString(response.body()).getData("post");

		/*
		 * JSON にした時点でコメントの SQL が飛ぶ。
		 * ここに2件あるということは、遅延読み込みが実際に走ったということである。
		 */
		List<Data> comments = post.getDataList("comments");

		assertEquals(2, comments.size(), response.body());
		assertEquals("きむら", comments.get(0).getString(Comment.name), response.body());

		delete("/posts/" + id);

	}

	// endregion

	// region REST 風のメソッド（要件 F-R-02）

	@Test
	@DisplayName("PUT は全部置き換える")
	void put () throws Exception {

		long id = insertPost("置き換える前");

		HttpResponse<String> response = send("PUT", "/posts/" + id
			, HttpRequest.BodyPublishers.ofString(
				"{\"title\":\"置き換えた\",\"body\":\"新しい本文\",\"published\":false}", StandardCharsets.UTF_8)
			, "Content-Type", "application/json");

		assertEquals(200, response.statusCode(), response.body());
		assertEquals(1, Data.fromJsonString(response.body()).getInt("updated"));

		assertEquals("置き換えた", title(id));

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("PATCH は送った項目だけ変える")
	void patch () throws Exception {

		long id = insertPost("元のタイトル");

		HttpResponse<String> response = send("PATCH", "/posts/" + id
			, HttpRequest.BodyPublishers.ofString(
				"{\"published\":true}", StandardCharsets.UTF_8)
			, "Content-Type", "application/json");

		assertEquals(200, response.statusCode(), response.body());

		// title は送っていないので変わらない
		assertEquals("元のタイトル", title(id));

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("PATCH で変える項目が無ければ 400")
	void patchWithoutChange () throws Exception {

		long id = insertPost("変えない記事");

		HttpResponse<String> response = send("PATCH", "/posts/" + id
			, HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)
			, "Content-Type", "application/json");

		assertEquals(400, response.statusCode(), response.body());

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("DELETE で消える")
	void deletePost () throws Exception {

		long id = insertPost("消される記事");

		assertEquals(200, delete("/posts/" + id).statusCode());

		assertEquals(404, get("/posts/" + id).statusCode(), "消えていない");

	}

	@Test
	@DisplayName("無い記事は 404 で、本文に理由が入る")
	void notFound () throws Exception {

		HttpResponse<String> response = get("/posts/999999999");

		assertEquals(404, response.statusCode());
		assertFalse(response.body().isEmpty(), "本文が空");

	}

	// endregion

	// region CORS（要件 F-W-12）

	@Test
	@DisplayName("OPTIONS のプリフライトに許可が返る")
	void preflight () throws Exception {

		HttpResponse<String> response = send("OPTIONS", "/posts"
			, HttpRequest.BodyPublishers.noBody()
			, "Origin", "https://example.com"
			, "Access-Control-Request-Method", "POST");

		assertEquals("https://example.com"
			, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null)
			, response.headers().map().toString());

	}

	@Test
	@DisplayName("許していないオリジンは断る")
	void corsRejected () throws Exception {

		HttpResponse<String> response = send("GET", "/posts"
			, HttpRequest.BodyPublishers.noBody()
			, "Origin", "https://evil.example");

		assertEquals(403, response.statusCode());

	}

	// endregion

	// region CSV（要件 F-W-07）

	@Test
	@DisplayName("CSV をストリームで返す")
	void csv () throws Exception {

		long id = insertPost("CSV に出る記事");

		HttpResponse<String> response = get("/posts.csv");

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/csv")
			, response.headers().map().toString());

		String[] lines = response.body().split("\r?\n");

		assertTrue(lines[0].contains("title"), lines[0]);
		assertTrue(response.body().contains("CSV に出る記事"), response.body());

		delete("/posts/" + id);

	}

	// endregion

	// region フォーム（要件 F-S-05〜07 / F-W-06）

	@Test
	@DisplayName("CSRF トークンが無ければ 403")
	void formWithoutCsrf () throws Exception {

		HttpResponse<String> response = send("POST", "/form"
			, HttpRequest.BodyPublishers.ofString("title=だめな投稿", StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

		assertEquals(403, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("フォームから登録すると、リダイレクトして Flash が出る")
	void formSubmit () throws Exception {

		// 1. フォームを開く。ここで CSRF トークンの Cookie が載る
		HttpResponse<String> form = get("/form");
		assertEquals(200, form.statusCode());

		String token = csrfToken();
		assertNotNull(token, "CSRF トークンが Cookie に載っていない");
		assertTrue(form.body().contains(token), "フォームにトークンが埋まっていない");

		// 2. 送る
		String body = "csrf_token=%s&title=%s&published=true".formatted(
			java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)
			, java.net.URLEncoder.encode("フォームから登録", StandardCharsets.UTF_8));

		HttpResponse<String> posted = send("POST", "/form"
			, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

		// リロードで二重投稿にならないよう、リダイレクトする
		assertEquals(302, posted.statusCode(), posted.body());
		assertEquals("/form", posted.headers().firstValue("location").orElse(null));

		// 3. リダイレクト先で Flash が読める。読んだら消える（要件 F-S-07）
		HttpResponse<String> after = get("/form");

		assertTrue(after.body().contains("フォームから登録"), after.body());
		assertTrue(after.body().contains("id=\"last-post\""), "Cookie が載っていない");

		HttpResponse<String> again = get("/form");
		assertFalse(again.body().contains("id=\"flash\""), "Flash が2回出ている");

		// 後始末
		Data row = BlogExample.db().select(
			SQL.select().from(Post.instance()).where(Post.title.eq("フォームから登録")));
		if (row != null) {
			delete("/posts/" + row.getLong(Post.id));
		}

	}

	@Test
	@DisplayName("画像を付けて登録できる")
	void formWithUpload () throws Exception {

		get("/form");
		String token = csrfToken();

		String boundary = "----jimble" + System.nanoTime();
		String multipart = """
			--%1$s\r
			Content-Disposition: form-data; name="csrf_token"\r
			\r
			%2$s\r
			--%1$s\r
			Content-Disposition: form-data; name="title"\r
			\r
			画像つきの記事\r
			--%1$s\r
			Content-Disposition: form-data; name="image"; filename="logo.png"\r
			Content-Type: image/png\r
			\r
			PNG-DATA\r
			--%1$s--\r
			""".formatted(boundary, token);

		HttpResponse<String> response = send("POST", "/form"
			, HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8)
			, "Content-Type", "multipart/form-data; boundary=" + boundary);

		assertEquals(302, response.statusCode(), response.body());

		Data row = BlogExample.db().select(
			SQL.select().from(Post.instance()).where(Post.title.eq("画像つきの記事")));

		assertNotNull(row, "記事が入っていない");

		Data post = row.extractTableData(Post.instance());
		String imageName = post.getStringOptional(Post.image_name);

		assertFalse(imageName.isEmpty(), "画像のファイル名が入っていない");

		// 送られてきた名前をそのまま使わない
		assertFalse(imageName.contains("logo"), imageName);
		assertTrue(imageName.endsWith(".png"), imageName);

		assertTrue(java.nio.file.Files.isRegularFile(
			java.nio.file.Path.of(FormController.UPLOAD_DIR, imageName)), imageName);

		delete("/posts/" + post.getLong(Post.id));

	}

	@Test
	@DisplayName("受け付けない形式は断る")
	void formWithBadUpload () throws Exception {

		get("/form");
		String token = csrfToken();

		String boundary = "----jimble" + System.nanoTime();
		String multipart = """
			--%1$s\r
			Content-Disposition: form-data; name="csrf_token"\r
			\r
			%2$s\r
			--%1$s\r
			Content-Disposition: form-data; name="title"\r
			\r
			あぶない記事\r
			--%1$s\r
			Content-Disposition: form-data; name="image"; filename="evil.sh"\r
			Content-Type: text/plain\r
			\r
			rm -rf /\r
			--%1$s--\r
			""".formatted(boundary, token);

		HttpResponse<String> response = send("POST", "/form"
			, HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8)
			, "Content-Type", "multipart/form-data; boundary=" + boundary);

		assertEquals(400, response.statusCode(), response.body());

	}

	// endregion

	// region SSE と MCP（要件 F-W-21 / F-MCP-01）

	@Test
	@DisplayName("進捗を SSE で流す")
	void sse () throws Exception {

		long id = insertPost("再構築される記事");

		HttpResponse<String> response = get("/posts/reindex");

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream")
			, response.headers().map().toString());

		String body = response.body();

		assertTrue(body.contains("event: start"), body);
		assertTrue(body.contains("event: progress"), body);
		assertTrue(body.contains("event: done"), body);
		assertTrue(body.contains("再構築される記事"), body);

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("MCP でツールの一覧が取れる")
	void mcpToolsList () throws Exception {

		HttpResponse<String> response = mcp("tools/list", null
			, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

		assertEquals(200, response.statusCode(), response.body());

		List<Data> tools = Data.fromJsonString(response.body()).getData("result").getDataList("tools");

		assertEquals("search_posts", tools.get(0).getString("name"));
		assertEquals("create_post", tools.get(1).getString("name"));

	}

	@Test
	@DisplayName("MCP から記事を探せる")
	void mcpSearch () throws Exception {

		long id = insertPost("MCP から見つかる記事");

		HttpResponse<String> response = mcp("tools/call", "search_posts"
			, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\""
				+ ",\"params\":{\"name\":\"search_posts\",\"arguments\":{\"keyword\":\"MCP から\"}}}");

		assertEquals(200, response.statusCode(), response.body());

		Data result = Data.fromJsonString(response.body()).getData("result");

		assertFalse(result.getBoolean("isError"), response.body());
		assertTrue(result.getData("structuredContent").getInt("count") >= 1, response.body());

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("MCP から記事を書ける（トランザクションと MQ も通る）")
	void mcpCreate () throws Exception {

		HttpResponse<String> response = mcp("tools/call", "create_post"
			, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\""
				+ ",\"params\":{\"name\":\"create_post\",\"arguments\":"
				+ "{\"title\":\"MCP が書いた記事\",\"body\":\"本文\",\"published\":true}}}");

		assertEquals(200, response.statusCode(), response.body());

		Data result = Data.fromJsonString(response.body()).getData("result");
		assertFalse(result.getBoolean("isError"), response.body());

		long id = result.getData("structuredContent").getLong("id");
		assertTrue(id > 0);

		// MQ にも積まれている（同じトランザクション）
		assertEquals(1, queuedCount(id));

		delete("/posts/" + id);

	}

	@Test
	@DisplayName("MCP のリソースが読める")
	void mcpResource () throws Exception {

		HttpResponse<String> response = mcp("resources/read", "blog://latest"
			, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\""
				+ ",\"params\":{\"uri\":\"blog://latest\"}}");

		assertEquals(200, response.statusCode(), response.body());

		Data contents = Data.fromJsonString(response.body())
			.getData("result").getDataList("contents").get(0);

		assertEquals("text/markdown", contents.getString("mimeType"));
		assertTrue(contents.getString("text").contains("最近の記事"), contents.getString("text"));

	}

	/**
	 * MCP を叩く
	 *
	 * @param method	メソッド
	 * @param name		対象の名前。要らなければ null
	 * @param body		本文
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> mcp (String method, String name, String body) throws Exception {

		if (name == null) {
			return send("POST", "/mcp"
				, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
				, "Content-Type", "application/json"
				, "MCP-Protocol-Version", "2026-07-28"
				, "Mcp-Method", method);
		}

		return send("POST", "/mcp"
			, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
			, "Content-Type", "application/json"
			, "MCP-Protocol-Version", "2026-07-28"
			, "Mcp-Method", method
			, "Mcp-Name", name);

	}

	// endregion

	// region WebSocket（要件 F-W-22）

	@Test
	@DisplayName("WebSocket で一覧が取れて、新しい記事が流れてくる")
	void webSocket () throws Exception {

		List<String> received = new java.util.ArrayList<>();
		CountDownLatch welcome = new CountDownLatch(1);
		CountDownLatch subscribed = new CountDownLatch(1);
		CountDownLatch notified = new CountDownLatch(1);

		WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/ws/posts")
				, new WebSocket.Listener() {

					@Override
					public CompletionStage<?> onText (WebSocket socket, CharSequence data, boolean last) {

						String text = data.toString();

						synchronized (received) {
							received.add(text);
						}

						if (text.contains("welcome")) {
							welcome.countDown();
						}
						if (text.contains("subscribed")) {
							subscribed.countDown();
						}
						if (text.contains("new_post")) {
							notified.countDown();
						}

						socket.request(1);

						return null;

					}

				})
			.join();

		assertTrue(welcome.await(5, TimeUnit.SECONDS), received.toString());

		// 知らないコマンドには、使えるものを返す
		ws.sendText("{\"command\":\"なにこれ\"}", true);

		ws.sendText("{\"command\":\"subscribe\"}", true);
		assertTrue(subscribed.await(5, TimeUnit.SECONDS), received.toString());

		// 記事を入れると流れてくる
		HttpResponse<String> created = send("POST", "/posts"
			, HttpRequest.BodyPublishers.ofString(
				"{\"title\":\"WebSocket に流れる記事\",\"body\":\"本文\",\"published\":true}"
				, StandardCharsets.UTF_8)
			, "Content-Type", "application/json");

		assertEquals(201, created.statusCode());

		assertTrue(notified.await(5, TimeUnit.SECONDS), received.toString());

		synchronized (received) {
			assertTrue(received.stream().anyMatch(text -> text.contains("知らないコマンド"))
				, received.toString());
			assertTrue(received.stream().anyMatch(text -> text.contains("WebSocket に流れる記事"))
				, received.toString());
		}

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

		delete("/posts/" + Data.fromJsonString(created.body()).getLong("id"));

	}

	// endregion

	// region 小物

	/**
	 * Cookie に載った CSRF トークン
	 *
	 * @return	トークン
	 */
	private static String csrfToken () {

		CookieManager manager = (CookieManager) CookieHandler.getDefault();

		return client.cookieHandler()
			.map(handler -> ((CookieManager) handler).getCookieStore().getCookies().stream()
				.filter(cookie -> Csrf.COOKIE_NAME.equals(cookie.getName()))
				.map(java.net.HttpCookie::getValue)
				.findFirst()
				.orElse(null))
			.orElse(null);

	}

	/**
	 * 記事を入れる
	 *
	 * @param title	タイトル
	 * @return	記事ID
	 */
	private static long insertPost (String title) {

		return BlogExample.db().insert(
			SQL.insert(Post.instance())
				.value(Post.title, title)
				.value(Post.body, "本文")
				.value(Post.published, true)
				.value(Post.created_at, new Date()));

	}

	/**
	 * コメントを入れる
	 *
	 * @param postId	記事ID
	 * @param name		名前
	 * @param body		本文
	 */
	private static void insertComment (long postId, String name, String body) {

		BlogExample.db().insert(
			SQL.insert(Comment.instance())
				.value(Comment.post_id, postId)
				.value(Comment.name, name)
				.value(Comment.body, body)
				.value(Comment.created_at, new Date()));

	}

	/**
	 * 記事のタイトル
	 *
	 * @param id	記事ID
	 * @return	タイトル
	 */
	private static String title (long id) {

		return BlogExample.db()
			.select(SQL.select().from(Post.instance()).where(Post.id.eq(id)))
			.extractTableData(Post.instance())
			.getString(Post.title);

	}

	/**
	 * GET
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return send("GET", path, HttpRequest.BodyPublishers.noBody());

	}

	/**
	 * DELETE
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> delete (String path) throws Exception {

		return send("DELETE", path, HttpRequest.BodyPublishers.noBody());

	}

	/**
	 * 送る
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param body		本文
	 * @param headers	ヘッダ（名前と値の並び）
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> send (
		String method, String path, HttpRequest.BodyPublisher body, String... headers) throws Exception {

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(20))
			.method(method, body);

		for (int i = 0; i + 1 < headers.length; i += 2) {
			builder.header(headers[i], headers[i + 1]);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * キューに積まれた件数
	 *
	 * <p>
	 * MQ の {@code data} 列は<b>製品で型が違う</b>（MySQL は {@code json}、
	 * PostgreSQL は {@code jsonb}）。jsonb には {@code LIKE} が使えないので、
	 * <b>SQL では絞らずに読んでから Java で数える</b>。
	 * </p>
	 *
	 * <p>
	 * {@code LIKE '%5%'} のような当て方にしないのは、記事を消して作り直すと
	 * ID が1から振り直され、<b>残っているキューの {@code post_id=500} が
	 * {@code 5} に当たる</b>ためである。
	 * </p>
	 *
	 * @param id	記事ID
	 * @return	件数
	 */
	private static int queuedCount (long id) {

		List<Data> rows = BlogExample.db().selectList("SELECT data FROM mq_blog");

		assertNotNull(rows, "mq_blog を読めませんでした");

		int count = 0;

		for (Data row : rows) {

			// json / jsonb の列は Data になって返る。生の文字列で返る製品もありうる
			Object raw = row.get("data");
			Data data = raw instanceof Data value ? value : Data.fromJsonString(String.valueOf(raw));

			if (data != null && data.getLong("post_id") == id) {
				count++;
			}

		}

		return count;

	}

	// endregion

}
