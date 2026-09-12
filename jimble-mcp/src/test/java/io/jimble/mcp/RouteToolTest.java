package io.jimble.mcp;

import io.jimble.mcp.schema.JsonSchema;
import io.jimble.mcp.tool.RouteTool;
import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 実装済みの API をツールにする（要件 F-MCP-15）
 *
 * <p>
 * <b>ハンドラを2度書かないための道具</b>なので、
 * 「API に書いたことが MCP からも同じように効くか」を確かめる。
 * とくに {@code before}（認証）と 4xx / 5xx の扱いである。
 * </p>
 */
class RouteToolTest {

	/* API 側で起きたことの記録 */
	static final List<String> log = new ArrayList<>();

	// docs:begin mcp-route-tool

	/** すでにある API */
	static final class Api extends io.jimble.web.router.Controller {

		{
			path("/api", () -> {

				before(context -> log.add("auth"));

				get("/posts", context -> {
					log.add("list");
					context.response().json("page", context.request().bodyQuery().getStringOptional("page"));
					context.response().json("items", List.of("a", "b"));
				});

				get("/posts/{id}", context -> {
					if ("999".equals(context.request().bodyPath().getString("id"))) {
						context.response().code(404);
						context.response().json("message", "その記事はありません: 999");
						return;
					}
					context.response().json("id", context.request().bodyPath().getString("id"));
				});

				post("/posts", context -> context.response()
					.json("title", context.request().bodyJson().getString("title")));

				get("/posts/broken", context -> {
					throw new IllegalStateException("パスワードは secret です");
				});

				get("/health", context -> context.response().text("OK"));

				get("/search", context -> context.response()
					.json("filter", context.request().bodyQuery().getString("filter"))
					.json("tags", context.request().source().queryParams().get("tag")));

			});
		}

	}

	/** その API をそのまま MCP にも出す */
	static final class Mcp extends McpController {

		{
			tool("list_posts", RouteTool.of("GET", "/api/posts")
				.description("記事の一覧を返す。page で頁を指定する")
				.input(JsonSchema.object()
					.integer("page", "ページ番号（1から）").min(1)));

			tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
				.description("記事を1件返す")
				.input(JsonSchema.object()
					.string("id", "記事ID").required()));

			tool("create_post", RouteTool.of("POST", "/api/posts")
				.description("記事を1件登録する")
				.input(JsonSchema.object()
					.string("title", "題名").required()));

			tool("broken", RouteTool.of("GET", "/api/posts/broken")
				.description("必ず失敗する"));

			tool("health", RouteTool.of("GET", "/api/health")
				.description("生きているかを返す"));

			tool("search", RouteTool.of("GET", "/api/search")
				.description("条件で探す")
				.input(JsonSchema.object()
					.object("filter", "絞り込み", JsonSchema.object().string("status", "状態"))
					.array("tag", "タグ", JsonSchema.object())));

			// 説明を書かない（メソッドとパスが説明になる）
			tool("no_description", RouteTool.of("GET", "/api/health"));
		}

	}

	// docs:end

	/** テスト用アプリケーション */
	static final class TestApp extends JimbleApp {

		{
			install(Api::new);
			install(Mcp::new);
		}

	}

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new TestApp(), 0);
		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

	}

	@Test
	@DisplayName("ルートから作ったツールが tools/list に出る")
	void listed () throws Exception {

		Data result = call("tools/list", null, "{}").getData("result");

		List<String> names = new ArrayList<>();
		for (Object tool : result.getObjectList("tools", Object.class)) {
			names.add(((java.util.Map<?, ?>) tool).get("name").toString());
		}

		assertEquals(List.of("list_posts", "get_post", "create_post", "broken", "health", "search"
			, "no_description"), names);

	}

	@Test
	@DisplayName("実装済みのハンドラがそのまま呼ばれ、JSON は構造化して返る")
	void callsHandler () throws Exception {

		log.clear();

		Data result = call("tools/call", "list_posts"
			, """
				{"name":"list_posts","arguments":{"page":2}}""").getData("result");

		assertFalse(result.getBoolean("isError"), result.getJsonString());

		// 構造化した中身（API のレスポンスそのもの）
		assertEquals("2", result.getData("structuredContent").getString("page"));

		// 同じものが文字でも入っている
		assertTrue(text(result).contains("\"page\":\"2\""), text(result));

		// API の before（認証）もハンドラも動いている
		assertEquals(List.of("auth", "list"), log);

	}

	@Test
	@DisplayName("パス変数は引数から埋まる")
	void fillsPathVariable () throws Exception {

		Data result = call("tools/call", "get_post"
			, """
				{"name":"get_post","arguments":{"id":"42"}}""").getData("result");

		assertEquals("42", result.getData("structuredContent").getString("id"));

	}

	@Test
	@DisplayName("POST の引数は JSON の本文になる")
	void sendsJsonBody () throws Exception {

		Data result = call("tools/call", "create_post"
			, """
				{"name":"create_post","arguments":{"title":"こんにちは"}}""").getData("result");

		assertEquals("こんにちは", result.getData("structuredContent").getString("title"));

	}

	@Test
	@DisplayName("4xx は本文ごと isError で返す（モデルが直せる）")
	void clientError () throws Exception {

		Data result = call("tools/call", "get_post"
			, """
				{"name":"get_post","arguments":{"id":"999"}}""").getData("result");

		assertTrue(result.getBoolean("isError"), result.getJsonString());
		assertTrue(text(result).contains("その記事はありません"), text(result));

	}

	@Test
	@DisplayName("5xx は本文を渡さない（要件 F-MCP-11）")
	void serverErrorHidesDetail () throws Exception {

		Data result = call("tools/call", "broken"
			, """
				{"name":"broken","arguments":{}}""").getData("result");

		assertTrue(result.getBoolean("isError"), result.getJsonString());
		assertFalse(text(result).contains("secret"), "500 の中身が漏れている: " + text(result));

	}

	@Test
	@DisplayName("必須の引数が足りなければ呼ぶ前に断る")
	void missingRequired () throws Exception {

		log.clear();

		Data result = call("tools/call", "get_post"
			, """
				{"name":"get_post","arguments":{}}""").getData("result");

		assertTrue(result.getBoolean("isError"), result.getJsonString());
		assertTrue(log.isEmpty(), "引数が足りないのに API が呼ばれた");

	}

	@Test
	@DisplayName("JSON でない本文は文字だけで返る")
	void plainText () throws Exception {

		Data result = call("tools/call", "health"
			, """
				{"name":"health","arguments":{}}""").getData("result");

		assertFalse(result.getBoolean("isError"), result.getJsonString());
		assertEquals("OK", text(result));
		assertFalse(result.containsKey("structuredContent"), result.getJsonString());

	}

	@Test
	@DisplayName("説明を書かなければメソッドとパスが説明になる")
	void defaultDescription () throws Exception {

		Data result = call("tools/list", null, "{}").getData("result");

		for (Object tool : result.getObjectList("tools", Object.class)) {
			java.util.Map<?, ?> map = (java.util.Map<?, ?>) tool;
			if ("no_description".equals(map.get("name"))) {
				assertEquals("GET /api/health", map.get("description"));
				return;
			}
		}

		throw new AssertionError("no_description が見つからない");

	}

	@Test
	@DisplayName("入れ子のオブジェクトと配列の引数が値のままクエリに載る")
	void nestedArguments () throws Exception {

		/*
		 * Data.toString() は「キーと型名」しか出さない。
		 * そのまま使うと filter=Data(1件) {status=String} という
		 * 値の消えたクエリができ、しかも例外は出ない。
		 */
		Data result = call("tools/call", "search"
			, """
				{"name":"search","arguments":{"filter":{"status":"open"},"tag":["a","b"]}}""").getData("result");

		assertFalse(result.getBoolean("isError"), result.getJsonString());

		Data structured = result.getData("structuredContent");

		assertTrue(structured.getString("filter").contains("open"), structured.getJsonString());
		assertEquals(2, structured.getObjectList("tags", Object.class).size(), structured.getJsonString());

	}

	@Test
	@DisplayName("ワイルドカードのルートは登録した時点で止める")
	void rejectsWildcard () {

		IllegalArgumentException thrown = org.junit.jupiter.api.Assertions.assertThrows(
			IllegalArgumentException.class, () -> RouteTool.of("GET", "/files/*"));

		assertTrue(thrown.getMessage().contains("ワイルドカード"), thrown.getMessage());

	}

	@Test
	@DisplayName("パス変数にオブジェクトを渡したら断る")
	void rejectsObjectPathVariable () throws Exception {

		Data result = call("tools/call", "get_post"
			, """
				{"name":"get_post","arguments":{"id":{"a":1}}}""").getData("result");

		assertTrue(result.getBoolean("isError"), result.getJsonString());

	}

	// region 小物

	/**
	 * 結果の文字を取り出す
	 *
	 * @param result	結果
	 * @return	文字
	 */
	private static String text (Data result) {

		StringBuilder builder = new StringBuilder();

		for (Object block : result.getObjectList("content", Object.class)) {
			Object value = ((java.util.Map<?, ?>) block).get("text");
			if (value != null) {
				builder.append(value);
			}
		}

		return builder.toString();

	}

	/**
	 * 呼ぶ
	 *
	 * @param method	メソッド
	 * @param name		対象の名前。要らなければ null
	 * @param params	引数の JSON
	 * @return	応答の本文
	 * @throws Exception	失敗した場合
	 */
	private static Data call (String method, String name, String params) throws Exception {

		String body = """
			{"jsonrpc":"2.0","id":1,"method":"%s","params":%s}""".formatted(method, params);

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + McpConf.DEFAULT_PATH))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.header(McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version())
			.header(McpProtocol.HEADER_METHOD, method)
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

		if (name != null) {
			builder.header(McpProtocol.HEADER_NAME, name);
		}

		HttpResponse<String> response = client.send(builder.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		return Data.fromJsonString(response.body());

	}

	// endregion

}
