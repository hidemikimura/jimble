package io.jimble.mcp;

import io.jimble.mcp.prompt.McpPrompt;
import io.jimble.mcp.resource.McpResource;
import io.jimble.mcp.schema.JsonSchema;
import io.jimble.mcp.tool.McpTool;
import io.jimble.mcp.tool.ToolResult;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
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
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP サーバーを実サーバーで確かめる（仕様 2026-07-28）
 *
 * <p>
 * <b>仕様の MUST を1つずつ叩く。</b>
 * とくにヘッダと本文の突き合わせは、実装しないと
 * 「手元では動くのに中継を挟んだ瞬間に落ちる」形になる。
 * </p>
 */
class McpIntegrationTest {

	// region テスト用のツールなど

	/** 天気を返すツール */
	public static final class WeatherTool implements McpTool {

		@Override
		public String description () {

			return "都市名から現在の天気を返す。過去や予報は返せない";

		}

		@Override
		public JsonSchema inputSchema () {

			return JsonSchema.object()
				.string("city", "都市名").required()
				.integer("days", "何日ぶん").min(1).max(7);

		}

		@Override
		public ToolResult call (WebContext context, Data arguments) {

			String city = arguments.getString("city");

			if ("火星".equals(city)) {
				// モデルが直せる失敗は isError で返す
				return ToolResult.error("その都市は扱えません: %s（地球の都市を指定してください）".formatted(city));
			}

			return ToolResult.text("%s は晴れ、22度です".formatted(city))
				.structured(new Data().putData("city", city).putData("temperature", 22));

		}

	}

	/** 落ちるツール */
	public static final class BrokenTool implements McpTool {

		@Override
		public String description () {

			return "必ず落ちる";

		}

		@Override
		public JsonSchema inputSchema () {

			return JsonSchema.empty();

		}

		@Override
		public ToolResult call (WebContext context, Data arguments) {

			throw new IllegalStateException("内部の秘密の事情");

		}

	}

	/** 設定を返すリソース */
	public static final class ConfigResource implements McpResource {

		@Override
		public String description () {

			return "アプリの設定";

		}

		@Override
		public String read (WebContext context, String uri) {

			return "env=test";

		}

	}

	/** 要約のプロンプト */
	public static final class SummarizePrompt implements McpPrompt {

		@Override
		public String description () {

			return "文章を3行で要約する";

		}

		@Override
		public List<Argument> arguments () {

			return List.of(new Argument("text", "要約する文章", true));

		}

		@Override
		public List<Message> get (WebContext context, Data arguments) {

			return List.of(Message.user("次を3行で要約して:\n" + arguments.getString("text")));

		}

	}

	/** テスト用の MCP サーバー */
	public static final class TestMcp extends McpController {

		/** ページ分けを起こすために積む数（既定の100件を超える） */
		static final int BULK = 150;

		{
			tool("get_weather", WeatherTool::new);
			tool("broken", BrokenTool::new);
			resource("config://app", ConfigResource::new);
			prompt("summarize", SummarizePrompt::new);

			/*
			 * <b>ページ分けの確認用（要件 F-MCP-14）。</b>
			 * 既定の1ページ100件を素の設定のまま超えさせたいので、実際に積む。
			 * 設定を差し替えると、動いているサーバーの他の設定まで消えてしまう
			 */
			for (int i = 0; i < BULK; i++) {
				resource("bulk://" + i, ConfigResource::new);
			}
		}

	}

	/** テスト用アプリケーション */
	static final class TestApp extends JimbleApp {

		{
			install(TestMcp::new);
		}

	}

	// endregion

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

	// region 一覧

	@Test
	@DisplayName("tools/list で登録した順に返る")
	void toolsList () throws Exception {

		Data result = call("tools/list", null, "{}").getData("result");

		assertEquals("complete", result.getString("resultType"));

		List<Data> tools = result.getDataList("tools");

		// 登録した順を保つ（クライアントのキャッシュのため）
		assertEquals("get_weather", tools.get(0).getString("name"));
		assertEquals("broken", tools.get(1).getString("name"));

		Data weather = tools.get(0);
		assertTrue(weather.getString("description").contains("都市名"), weather.getJsonString());

		Data schema = weather.getData("inputSchema");
		assertEquals("object", schema.getString("type"));
		assertEquals(List.of("city"), schema.getObjectListOptional("required", Object.class));
		assertEquals("string", schema.getData("properties").getData("city").getString("type"));
		assertEquals(1, schema.getData("properties").getData("days").getInt("minimum"));

	}

	@Test
	@DisplayName("引数の無いツールは null ではなく空のオブジェクトを出す")
	void emptySchema () throws Exception {

		Data result = call("tools/list", null, "{}").getData("result");

		Data broken = result.getDataList("tools").get(1);
		Data schema = broken.getData("inputSchema");

		// inputSchema が null なのは仕様違反
		assertNotNull(schema);
		assertEquals("object", schema.getString("type"));
		assertFalse(schema.getBoolean("additionalProperties"));

	}

	@Test
	@DisplayName("resources/list と prompts/list が返る")
	void listOthers () throws Exception {

		Data resources = call("resources/list", null, "{}").getData("result");
		assertEquals("config://app", resources.getDataList("resources").get(0).getString("uri"));

		Data prompts = call("prompts/list", null, "{}").getData("result");
		Data prompt = prompts.getDataList("prompts").get(0);
		assertEquals("summarize", prompt.getString("name"));
		assertTrue(prompt.getDataList("arguments").get(0).getBoolean("required"));

	}

	// endregion

	// region 実行

	@Test
	@DisplayName("tools/call が結果を返す")
	void toolsCall () throws Exception {

		Data result = call("tools/call", "get_weather"
			, "{\"name\":\"get_weather\",\"arguments\":{\"city\":\"東京\"}}").getData("result");

		assertFalse(result.getBoolean("isError"));
		assertEquals("text", result.getDataList("content").get(0).getString("type"));
		assertTrue(result.getDataList("content").get(0).getString("text").contains("東京"));
		assertEquals(22, result.getData("structuredContent").getInt("temperature"));

	}

	@Test
	@DisplayName("ツールの中の失敗は isError で返る（プロトコルのエラーにしない）")
	void toolExecutionError () throws Exception {

		Data response = call("tools/call", "get_weather"
			, "{\"name\":\"get_weather\",\"arguments\":{\"city\":\"火星\"}}");

		/*
		 * モデルが読んで直せるように、結果として返す。
		 * JSON-RPC のエラーにすると、モデルは直しようがない。
		 */
		assertTrue(response.getDataOptional("error").isEmpty(), "プロトコルのエラーになっている");

		Data result = response.getData("result");
		assertTrue(result.getBoolean("isError"));
		assertTrue(result.getDataList("content").get(0).getString("text").contains("地球の都市"));

	}

	@Test
	@DisplayName("必須の引数が無ければ、足りないものをまとめて言う")
	void missingArgument () throws Exception {

		Data result = call("tools/call", "get_weather"
			, "{\"name\":\"get_weather\",\"arguments\":{}}").getData("result");

		assertTrue(result.getBoolean("isError"));
		assertTrue(result.getDataList("content").get(0).getString("text").contains("city")
			, result.getJsonString());

	}

	@Test
	@DisplayName("ツールが落ちても中身は外に出さない")
	void toolThrows () throws Exception {

		Data result = call("tools/call", "broken"
			, "{\"name\":\"broken\",\"arguments\":{}}").getData("result");

		assertTrue(result.getBoolean("isError"));

		String text = result.getDataList("content").get(0).getString("text");
		assertFalse(text.contains("内部の秘密の事情"), text);

	}

	@Test
	@DisplayName("知らないツールはプロトコルのエラー")
	void unknownTool () throws Exception {

		Data error = call("tools/call", "nope"
			, "{\"name\":\"nope\",\"arguments\":{}}").getData("error");

		assertEquals(McpErrors.INVALID_PARAMS, error.getInt("code"));

	}

	@Test
	@DisplayName("resources/read と prompts/get が動く")
	void readAndGet () throws Exception {

		Data resource = call("resources/read", "config://app"
			, "{\"uri\":\"config://app\"}").getData("result");
		assertEquals("env=test", resource.getDataList("contents").get(0).getString("text"));

		Data prompt = call("prompts/get", "summarize"
			, "{\"name\":\"summarize\",\"arguments\":{\"text\":\"あああ\"}}").getData("result");
		assertEquals("user", prompt.getDataList("messages").get(0).getString("role"));

	}

	// endregion

	// region 仕様の MUST

	@Test
	@DisplayName("知らないメソッドは 404 と -32601")
	void unknownMethod () throws Exception {

		HttpResponse<String> response = post("nothing/here", null, "{}");

		// 古い HTTP+SSE のサーバーが返す 404 と区別できるようにする
		assertEquals(404, response.statusCode());
		assertEquals(McpErrors.METHOD_NOT_FOUND
			, Data.fromJsonString(response.body()).getData("error").getInt("code"));

	}

	@Test
	@DisplayName("通知は 202 で本文なし")
	void notification () throws Exception {

		String body = """
			{"jsonrpc":"2.0","method":"notifications/something","params":{}}""";

		HttpResponse<String> response = send("POST", body
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "notifications/something");

		assertEquals(202, response.statusCode());
		assertTrue(response.body().isEmpty(), response.body());

	}

	@Test
	@DisplayName("GET と DELETE は 405")
	void getAndDelete () throws Exception {

		// 2026-07-28 で GET ストリームもセッションも消えた
		assertEquals(405, send("GET", null).statusCode());
		assertEquals(405, send("DELETE", null).statusCode());

	}

	@Test
	@DisplayName("Mcp-Method が本文と食い違えば 400 と -32020")
	void methodMismatch () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/call");

		assertEquals(400, response.statusCode());
		assertEquals(McpErrors.HEADER_MISMATCH
			, Data.fromJsonString(response.body()).getData("error").getInt("code"));

	}

	@Test
	@DisplayName("Mcp-Name が本文と食い違えば 400")
	void nameMismatch () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_weather","arguments":{"city":"東京"}}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/call"
			, McpProtocol.HEADER_NAME, "broken");

		assertEquals(400, response.statusCode());

	}

	@Test
	@DisplayName("Base64 で包まれた Mcp-Name を解いて突き合わせる")
	void base64Name () throws Exception {

		/*
		 * 日本語のツール名やリソース URI はヘッダにそのまま置けないので、
		 * =?base64?...?= の形で来る。
		 * 解かずに比べると、日本語の名前がすべて食い違い扱いになる。
		 */
		String encoded = McpProtocol.BASE64_PREFIX
			+ Base64.getEncoder().encodeToString("config://app".getBytes(StandardCharsets.UTF_8))
			+ McpProtocol.BASE64_SUFFIX;

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"uri":"config://app"}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "resources/read"
			, McpProtocol.HEADER_NAME, encoded);

		assertEquals(200, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("Mcp-Param-* も本文と突き合わせる")
	void paramMismatch () throws Exception {

		HttpResponse<String> ok = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_weather","arguments":{"city":"東京","days":3}}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/call"
			, McpProtocol.HEADER_NAME, "get_weather"
			, "Mcp-Param-Days", "3");

		assertEquals(200, ok.statusCode(), ok.body());

		HttpResponse<String> bad = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_weather","arguments":{"city":"東京","days":3}}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/call"
			, McpProtocol.HEADER_NAME, "get_weather"
			, "Mcp-Param-Days", "7");

		assertEquals(400, bad.statusCode(), bad.body());

	}

	@Test
	@DisplayName("プロトコルの版が違えば 400 と、対応している版を返す")
	void versionMismatch () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, "2025-03-26"
			, McpProtocol.HEADER_METHOD, "tools/list");

		assertEquals(400, response.statusCode());

		Data error = Data.fromJsonString(response.body()).getData("error");
		assertTrue(error.getData("data").getObjectListOptional("supported", Object.class)
			.contains(McpProtocol.version()), response.body());

	}

	@Test
	@DisplayName("版のヘッダが無ければ 400")
	void versionMissing () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
			, McpProtocol.HEADER_METHOD, "tools/list");

		assertEquals(400, response.statusCode());

	}

	@Test
	@DisplayName("_meta の版とヘッダが食い違えば 400")
	void metaMismatch () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-03-26"}}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/list");

		assertEquals(400, response.statusCode());

	}

	@Test
	@DisplayName("知らないオリジンは 403（DNS リバインディング対策）")
	void origin () throws Exception {

		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, "tools/list"
			, "Origin", "https://evil.example");

		assertEquals(403, response.statusCode());

	}

	// endregion

	// region server/discover（仕様 MUST）

	@Test
	@DisplayName("server/discover が素性と対応している版を返す")
	void discover () throws Exception {

		Data result = call(McpProtocol.METHOD_SERVER_DISCOVER, null, "{}").getData("result");

		/*
		 * <b>仕様は「サーバーは実装しなければならない」と定めている。</b>
		 * クライアントはこれで、今の版で話せる相手かどうかを最初に見分ける
		 */
		assertEquals(List.of(McpProtocol.version()), result.getStringList("supportedVersions"));

		Data info = result.getData("_meta").getData(McpProtocol.META_SERVER_INFO);

		assertFalse(info.getString("name").isEmpty(), result.getJsonString());

		Data capabilities = result.getData("capabilities");

		assertTrue(capabilities.containsKey("tools"), capabilities.getJsonString());
		assertTrue(capabilities.containsKey("resources"), capabilities.getJsonString());
		assertTrue(capabilities.containsKey("prompts"), capabilities.getJsonString());

	}

	@Test
	@DisplayName("server/discover は版のヘッダが無くても答える")
	void discoverWithoutVersion () throws Exception {

		/*
		 * <b>版を確かめるための呼び出しに、版を要求してはいけない。</b>
		 * ここで断ると、クライアントは版を知る手立てが無くなる
		 */
		HttpResponse<String> response = send("POST"
			, """
				{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{}}""");

		assertEquals(200, response.statusCode(), response.body());

		assertEquals(List.of(McpProtocol.version())
			, Data.fromJsonString(response.body()).getData("result").getStringList("supportedVersions"));

	}

	// endregion

	// region ページ分け（要件 F-MCP-14）

	@Test
	@DisplayName("多いときはページに切って、続きのカーソルを付ける")
	void pagination () throws Exception {

		Data first = call("resources/list", null, "{}").getData("result");

		assertEquals(McpPaging.DEFAULT_PAGE_SIZE, first.getDataList("resources").size());

		String cursor = first.getString("nextCursor");

		assertFalse(cursor.isEmpty(), first.getJsonString());

		Data second = call("resources/list", null
			, "{\"cursor\":\"%s\"}".formatted(cursor)).getData("result");

		// 1 + 150 件を100件で切ったので、残りは51件
		assertEquals(TestMcp.BULK + 1 - McpPaging.DEFAULT_PAGE_SIZE
			, second.getDataList("resources").size());

		// 最後のページにカーソルを付けると、クライアントは1周多く読みにくる
		assertTrue(second.getStringOptional("nextCursor").isEmpty(), second.getJsonString());

	}

	@Test
	@DisplayName("全部たどると、1件も落とさず1件も重ならない")
	void paginationCoversEverything () throws Exception {

		List<String> seen = new java.util.ArrayList<>();

		String cursor = null;

		for (int guard = 0; guard < 20; guard++) {

			Data page = call("resources/list", null
				, cursor == null ? "{}" : "{\"cursor\":\"%s\"}".formatted(cursor)).getData("result");

			for (Data resource : page.getDataList("resources")) {
				seen.add(resource.getString("uri"));
			}

			cursor = page.getStringOptional("nextCursor");

			if (cursor.isEmpty()) {
				break;
			}

		}

		assertEquals(TestMcp.BULK + 1, seen.size());
		assertEquals(seen.size(), seen.stream().distinct().count(), "同じものを2度返している");
		assertEquals("config://app", seen.get(0));

	}

	@Test
	@DisplayName("読めないカーソルは -32602 で断る（黙って先頭に倒さない）")
	void paginationInvalidCursor () throws Exception {

		/*
		 * <b>黙って先頭に倒すと、クライアントは同じページを永遠に読み続ける。</b>
		 * しかもエラーが1つも出ない
		 */
		Data error = call("resources/list", null, "{\"cursor\":\"こわれている\"}").getData("error");

		assertEquals(McpErrors.INVALID_PARAMS, error.getInt("code"));

	}

	@Test
	@DisplayName("少ないものにはカーソルを付けない（今までの応答が変わらない）")
	void paginationNoCursorWhenSmall () throws Exception {

		Data tools = call("tools/list", null, "{}").getData("result");

		assertEquals(2, tools.getDataList("tools").size());
		assertTrue(tools.getStringOptional("nextCursor").isEmpty(), tools.getJsonString());

	}

	// endregion

	// region 購読（要件 F-MCP-13）

	@Test
	@DisplayName("subscriptions/listen が SSE になり、頼んだ更新だけ流れてくる")
	void subscribe () throws Exception {

		String body = """
			{"jsonrpc":"2.0","id":99,"method":"subscriptions/listen","params":{\
			"notifications":{"resourceSubscriptions":["config://app"]}}}""";

		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + McpConf.DEFAULT_PATH))
			.timeout(Duration.ofSeconds(30))
			/*
			 * <b>HTTP/1.1 で張る。</b>JDK のクライアントは既定で HTTP/2 への
			 * 昇格を試み、その最中は本文を溜める。SSE では最初の1件が届かない
			 */
			.version(HttpClient.Version.HTTP_1_1)
			.header("Content-Type", "application/json")
			.header("Accept", "text/event-stream")
			.header(McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version())
			.header(McpProtocol.HEADER_METHOD, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN)
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();

		HttpResponse<java.io.InputStream> response
			= client.send(request, HttpResponse.BodyHandlers.ofInputStream());

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream")
			, response.headers().toString());

		java.io.BufferedReader lines = new java.io.BufferedReader(
			new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8));

		Data acknowledged = nextMessage(lines);

		/*
		 * <b>まず acknowledged を返す（仕様 MUST）。</b>
		 * これが来ないと、クライアントは購読が始まったのかどうか分からないまま待つ
		 */
		assertEquals(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED, acknowledged.getString("method"));
		assertEquals(99, acknowledged.getData("params").getData("_meta")
			.getInt(McpProtocol.META_SUBSCRIPTION_ID));

		// 別のスレッドから流す（アプリが McpNotify を呼んだつもり）
		Thread notifier = Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(200);
				McpNotify.resourceUpdated("config://other");   // 頼んでいない
				McpNotify.resourceUpdated("config://app");     // 頼んだ
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});

		Data updated = nextMessage(lines);

		notifier.join();

		assertEquals(McpProtocol.NOTIFICATION_RESOURCES_UPDATED, updated.getString("method"));

		// 頼んでいない config://other は<b>飛ばされている</b>
		assertEquals("config://app", updated.getData("params").getString("uri"));

		/*
		 * <b>サーバー側から終わるときは、元の要求への応答を返してから切る</b>
		 * （仕様の graceful closure）。返さずに切ると、
		 * クライアントは「落ちた」と思って繋ぎ直す
		 */
		McpSubscriptions.complete(99);

		Data closing = nextMessage(lines);

		assertEquals(99, closing.getInt("id"));
		assertEquals(McpProtocol.RESULT_TYPE_COMPLETE, closing.getData("result").getString("resultType"));

	}

	@Test
	@DisplayName("取り消されたら、応答を返さずに閉じる（仕様 MUST NOT）")
	void subscribeCancelled () throws Exception {

		String body = """
			{"jsonrpc":"2.0","id":55,"method":"subscriptions/listen","params":{}}""";

		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + McpConf.DEFAULT_PATH))
			.timeout(Duration.ofSeconds(30))
			.version(HttpClient.Version.HTTP_1_1)
			.header("Content-Type", "application/json")
			.header("Accept", "text/event-stream")
			.header(McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version())
			.header(McpProtocol.HEADER_METHOD, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN)
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();

		HttpResponse<java.io.InputStream> response
			= client.send(request, HttpResponse.BodyHandlers.ofInputStream());

		java.io.BufferedReader lines = new java.io.BufferedReader(
			new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8));

		assertEquals(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED
			, nextMessage(lines).getString("method"));

		long start = System.nanoTime();

		HttpResponse<String> cancelled = send("POST"
			, """
				{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":55}}"""
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, McpProtocol.NOTIFICATION_CANCELLED);

		assertEquals(202, cancelled.statusCode());

		/*
		 * <b>応答は返さない</b>（仕様は「取り消された要求に応答を返してはならない」）。
		 * ストリームがそのまま終わる
		 */
		assertNull(readLineOrNull(lines), "取り消したのに何か返している");

		/*
		 * <b>すぐ手放す。</b>空行を送る時間（15秒）まで居座るようだと、
		 * 取り消しの意味が無い
		 */
		long ms = (System.nanoTime() - start) / 1_000_000;

		assertTrue(ms < 5_000, "取り消しても接続を手放していない: " + ms + "ms");

	}

	/**
	 * SSE から次の中身のある行を読む。無ければ null
	 *
	 * @param lines	行
	 * @return	行。ストリームが終わっていれば null
	 * @throws Exception 失敗した場合
	 */
	private static String readLineOrNull (java.io.BufferedReader lines) throws Exception {

		String line;

		while ((line = lines.readLine()) != null) {

			if (line.startsWith("data:")) {
				return line;
			}

		}

		return null;

	}

	/**
	 * SSE から次のメッセージを1つ取り出す
	 *
	 * @param lines	行
	 * @return	メッセージ
	 */
	private static Data nextMessage (java.io.BufferedReader lines) throws Exception {

		String line;

		while ((line = lines.readLine()) != null) {

			if (line.startsWith("data:")) {
				return Data.fromJsonString(line.substring("data:".length()).trim());
			}

		}

		throw new IllegalStateException("メッセージが来ないままストリームが終わりました");

	}

	// endregion

	// region 小物

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

		return Data.fromJsonString(post(method, name, params).body());

	}

	/**
	 * 呼ぶ
	 *
	 * @param method	メソッド
	 * @param name		対象の名前
	 * @param params	引数の JSON
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (String method, String name, String params) throws Exception {

		String body = """
			{"jsonrpc":"2.0","id":1,"method":"%s","params":%s}""".formatted(method, params);

		if (name == null) {
			return send("POST", body
				, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
				, McpProtocol.HEADER_METHOD, method);
		}

		return send("POST", body
			, McpProtocol.HEADER_PROTOCOL_VERSION, McpProtocol.version()
			, McpProtocol.HEADER_METHOD, method
			, McpProtocol.HEADER_NAME, name);

	}

	/**
	 * 送る
	 *
	 * @param method	HTTP メソッド
	 * @param body		本文。無ければ null
	 * @param headers	ヘッダ（名前と値の並び）
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> send (String method, String body, String... headers) throws Exception {

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + McpConf.DEFAULT_PATH))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.method(method, body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

		for (int i = 0; i + 1 < headers.length; i += 2) {
			builder.header(headers[i], headers[i + 1]);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	// endregion

}
