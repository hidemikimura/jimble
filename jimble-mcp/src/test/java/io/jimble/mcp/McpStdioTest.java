package io.jimble.mcp;

import io.jimble.mcp.schema.JsonSchema;
import io.jimble.mcp.tool.McpTool;
import io.jimble.mcp.tool.RouteTool;
import io.jimble.mcp.tool.ToolResult;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP を標準入出力で受ける（{@link McpStdio}／要件 F-MCP-12）
 *
 * <p>
 * <b>いちばん壊れやすいのは「標準出力に余計なものが混ざる」ことである。</b>
 * ログが1行入るだけでクライアントは切る。しかも<b>手元では気づけない</b>——
 * ログは普通のことなので、見ても何も変に思わない。ここで固定しておく。
 * </p>
 */
@Timeout(30)
class McpStdioTest {

	// region テスト用のもの

	/** 挨拶を返すツール */
	public static final class HelloTool implements McpTool {

		@Override public String description () { return "名前を受け取って挨拶を返す"; }

		@Override
		public JsonSchema inputSchema () {

			return JsonSchema.object().string("name", "名前").required();

		}

		@Override
		public ToolResult call (WebContext context, Data arguments) {

			// <b>わざと標準出力に書く。</b>これが混ざらないことを確かめる
			System.out.println("ツールの中から標準出力に書きました");

			Log.info("ツールの中からログを出しました");

			return ToolResult.text("こんにちは、%s さん".formatted(arguments.getString("name")));

		}

	}

	/** ルートを持つアプリ（RouteTool の確認用） */
	static final class TestApp extends JimbleApp {

		{
			get("/api/posts/{id}", context -> context.response()
				.json(new Data().putData("id", context.request().bodyPath().getString("id")).putData("title", "記事")));
		}

	}

	// endregion

	@AfterEach
	void closeSubscriptions () {

		McpSubscriptions.closeAll();

	}

	// region 基本

	@Test
	@DisplayName("1行1メッセージで応答が返る")
	void oneMessagePerLine () throws Exception {

		McpRegistry registry = new McpRegistry();
		registry.tool("hello", HelloTool::new);

		List<Data> out = run(registry, null
			, request(1, McpProtocol.METHOD_TOOLS_LIST, new Data())
			, request(2, McpProtocol.METHOD_TOOLS_CALL
				, new Data().putData("name", "hello").putData("arguments", new Data().putData("name", "太郎"))));

		assertEquals(2, out.size());

		assertEquals(1, out.get(0).getInt("id"));
		assertEquals("hello", out.get(0).getData("result").getDataList("tools").get(0).getString("name"));

		assertEquals(2, out.get(1).getInt("id"));
		assertTrue(out.get(1).getData("result").getDataList("content").get(0)
			.getString("text").contains("太郎"));

	}

	@Test
	@DisplayName("標準出力に MCP のメッセージ以外を書かせない（仕様 MUST）")
	void nothingButMessagesOnStdout () throws Exception {

		/*
		 * ツールの中で System.out.println と Log.info をしている。
		 * <b>どちらも混ざってはいけない。</b>混ざるとクライアントは
		 * 「壊れた JSON が来た」として切る
		 */
		McpRegistry registry = new McpRegistry();
		registry.tool("hello", HelloTool::new);

		String raw = runRaw(registry, null
			, request(1, McpProtocol.METHOD_TOOLS_CALL
				, new Data().putData("name", "hello").putData("arguments", new Data().putData("name", "花子"))));

		for (String line : raw.split("\n")) {

			if (line.isBlank()) {
				continue;
			}

			// 1行ずつ JSON として読めなければならない
			Data parsed = Dson.decodes(line, Data.class);

			assertNotNull(parsed, "JSON として読めない行がある: " + line);
			assertEquals(McpProtocol.JSONRPC_VERSION, parsed.getString("jsonrpc"), line);

		}

		assertFalse(raw.contains("ツールの中から標準出力に書きました"), raw);
		assertFalse(raw.contains("ツールの中からログを出しました"), raw);

	}

	@Test
	@DisplayName("読めない行は、その旨を返す（黙って捨てない）")
	void brokenLine () throws Exception {

		/*
		 * <b>いろいろな壊れ方を通す。</b>途中で切れた JSON、配列、裸の値、ただの文字列——
		 * どれも「読めない」で1つ返るのが正しい。
		 * 黙って捨てると、クライアントは応答を待ち続けて止まる
		 */
		for (String broken : new String[]{"{ こわれている", "[1,2,3]", "\"ただの文字列\"", "12345", "これは JSON ではない", "{\"a\":"}) {

			List<Data> out = runLines(new McpRegistry(), null, broken, "");

			assertEquals(1, out.size(), broken);
			assertEquals(McpErrors.PARSE_ERROR, out.get(0).getData("error").getInt("code"), broken);

			// id が分からないので null で返す
			assertNull(out.get(0).get("id"), broken);

		}

	}

	@Test
	@DisplayName("通知には何も返さない")
	void notification () throws Exception {

		Data notification = new Data();
		notification.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		notification.put("method", "notifications/initialized");

		assertTrue(runLines(new McpRegistry(), null, notification.getJsonString()).isEmpty());

	}

	// endregion

	// region 版（ヘッダの層が無い）

	@Test
	@DisplayName("版は本文の _meta から読む（stdio にはヘッダが無い）")
	void versionFromMeta () throws Exception {

		List<Data> out = run(new McpRegistry(), null
			, request(1, McpProtocol.METHOD_SERVER_DISCOVER, new Data()));

		assertNotNull(out.get(0).getData("result"), out.get(0).toString());

	}

	@Test
	@DisplayName("版が無ければ断る")
	void versionMissing () throws Exception {

		Data body = new Data();
		body.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		body.put("id", 1);
		body.put("method", McpProtocol.METHOD_TOOLS_LIST);
		body.put("params", new Data());

		List<Data> out = runLines(new McpRegistry(), null, body.getJsonString());

		assertEquals(McpErrors.HEADER_MISMATCH, out.get(0).getData("error").getInt("code"));

	}

	@Test
	@DisplayName("知らない版には、対応している版を並べて返す")
	void versionUnsupported () throws Exception {

		Data params = new Data();
		params.putData("_meta", new Data().putData(McpProtocol.META_PROTOCOL_VERSION, "1900-01-01"));

		Data body = new Data();
		body.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		body.put("id", 1);
		body.put("method", McpProtocol.METHOD_TOOLS_LIST);
		body.put("params", params);

		Data error = runLines(new McpRegistry(), null, body.getJsonString()).get(0).getData("error");

		assertEquals(McpErrors.INVALID_REQUEST, error.getInt("code"));
		assertTrue(error.getData("data").getStringList("supported").contains(McpProtocol.VERSION));

	}

	// endregion

	// region server/discover（仕様 MUST）

	@Test
	@DisplayName("server/discover が素性と対応している版を返す")
	void discover () throws Exception {

		McpRegistry registry = new McpRegistry();
		registry.tool("hello", HelloTool::new);

		Data result = run(registry, null
			, request(1, McpProtocol.METHOD_SERVER_DISCOVER, new Data())).get(0).getData("result");

		assertEquals(List.of(McpProtocol.VERSION), result.getStringList("supportedVersions"));
		assertTrue(result.getData("capabilities").containsKey("tools"));

		// 1つも登録していないものは名乗らない
		assertFalse(result.getData("capabilities").containsKey("resources"));
		assertFalse(result.getData("capabilities").containsKey("prompts"));

		assertNotNull(result.getData("_meta").getData(McpProtocol.META_SERVER_INFO).getString("name"));

	}

	// endregion

	// region RouteTool（要件 F-MCP-15）

	@Test
	@DisplayName("stdio でも、実装済みの API をそのままツールにできる")
	void routeTool () throws Exception {

		/*
		 * <b>ポートは開いていない。</b>それでもルート表とディスパッチャは組んであるので、
		 * ハンドラも before も検証も、HTTP のときとまったく同じ道を通る
		 */
		McpRegistry registry = new McpRegistry();
		registry.tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
			.description("記事を1件返す")
			.input(JsonSchema.object().string("id", "記事ID").required()));

		List<Data> out = run(registry, new Dispatcher(new TestApp())
			, request(1, McpProtocol.METHOD_TOOLS_CALL
				, new Data().putData("name", "get_post")
					.putData("arguments", new Data().putData("id", "42"))));

		Data result = out.get(0).getData("result");

		assertFalse(result.getBoolean("isError"), result.toString());
		assertEquals("42", result.getData("structuredContent").getString("id"));

	}

	@Test
	@DisplayName("アプリを渡していなければ、理由を言って断る")
	void routeToolWithoutApp () throws Exception {

		McpRegistry registry = new McpRegistry();
		registry.tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
			.description("記事を1件返す")
			.input(JsonSchema.object().string("id", "記事ID").required()));

		Data result = run(registry, null
			, request(1, McpProtocol.METHOD_TOOLS_CALL
				, new Data().putData("name", "get_post")
					.putData("arguments", new Data().putData("id", "42")))).get(0).getData("result");

		// <b>黙って 404 を返さない。</b>設定の間違いだと分かる形で返す
		assertTrue(result.getBoolean("isError"), result.toString());

	}

	// endregion

	// region 購読（要件 F-MCP-13）

	@Test
	@DisplayName("購読すると、まず acknowledged が届く")
	void subscribeAcknowledged () throws Exception {

		Data params = new Data();
		params.putData("notifications", new Data()
			.putData("resourceSubscriptions", List.of("blog://posts/1"))
			.putData("toolsListChanged", true));

		List<Data> out = run(new McpRegistry(), null
			, request(7, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN, params));

		/*
		 * acknowledged と、標準入力が閉じたときの<b>締めの応答</b>の2つ。
		 * 締めを返さずに切ると、クライアントは「落ちた」と思って繋ぎ直す
		 */
		assertEquals(2, out.size(), out.toString());

		Data acknowledged = out.get(0);

		assertEquals(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED, acknowledged.getString("method"));
		assertEquals(7, acknowledged.getData("params").getData("_meta").getInt(McpProtocol.META_SUBSCRIPTION_ID));

		Data notifications = acknowledged.getData("params").getData("notifications");

		assertEquals(List.of("blog://posts/1"), notifications.getStringList("resourceSubscriptions"));

		/*
		 * <b>ツールの一覧は起動時に決まるので、変わりようが無い。</b>
		 * 「対応している」と答えて一生届かないほうが悪いので、落として返す
		 */
		assertFalse(notifications.containsKey("toolsListChanged"), notifications.toString());

		// 締めは元の要求への応答である（通知ではない）
		Data closing = out.get(1);

		assertEquals(7, closing.getInt("id"));
		assertTrue(closing.containsKey("result"), closing.toString());

	}

	@Test
	@DisplayName("頼んだリソースが変わったときだけ届く")
	void resourceUpdated () throws Exception {

		Data params = new Data();
		params.putData("notifications", new Data()
			.putData("resourceSubscriptions", List.of("blog://posts/1")));

		List<Data> out = runWith(new McpRegistry(), null
			, () -> {
				McpNotify.resourceUpdated("blog://posts/1");
				McpNotify.resourceUpdated("blog://posts/2");   // 頼んでいない
				McpNotify.resourcesListChanged();               // 頼んでいない
			}
			, request(7, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN, params));

		// acknowledged ／ 頼んだ1件 ／ 締めの応答。<b>頼んでいない2つは入らない</b>
		assertEquals(3, out.size(), out.toString());

		assertEquals(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED, out.get(0).getString("method"));
		assertEquals(McpProtocol.NOTIFICATION_RESOURCES_UPDATED, out.get(1).getString("method"));
		assertTrue(out.get(2).containsKey("result"), out.get(2).toString());
		assertEquals("blog://posts/1", out.get(1).getData("params").getString("uri"));

		// 全部の通知に購読の識別子が付く（stdio は1本の口を共有するので、これで見分ける）
		assertEquals(7, out.get(1).getData("params").getData("_meta").getInt(McpProtocol.META_SUBSCRIPTION_ID));

	}

	@Test
	@DisplayName("取り消しの通知で閉じる")
	void cancel () throws Exception {

		Data params = new Data();
		params.putData("notifications", new Data()
			.putData("resourceSubscriptions", List.of("blog://posts/1")));

		Data cancel = new Data();
		cancel.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		cancel.put("method", McpProtocol.NOTIFICATION_CANCELLED);
		cancel.put("params", new Data().putData("requestId", 7));

		List<Data> out = runWith(new McpRegistry(), null
			, () -> McpNotify.resourceUpdated("blog://posts/1")
			, line(request(7, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN, params)), cancel.getJsonString());

		/*
		 * <b>取り消しのあとに流したものは届かない。</b>
		 * 届くと、クライアントは知らない購読のメッセージを受け取ることになる
		 */
		assertEquals(1, out.size(), out.toString());
		assertEquals(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED, out.get(0).getString("method"));
		assertEquals(0, McpSubscriptions.openCount());

	}

	@Test
	@DisplayName("標準入力が閉じたら、購読をきれいに閉じてから終わる")
	void gracefulClosure () throws Exception {

		Data params = new Data();
		params.putData("notifications", new Data()
			.putData("resourceSubscriptions", List.of("blog://posts/1")));

		List<Data> out = run(new McpRegistry(), null
			, request(7, McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN, params));

		/*
		 * <b>切るのではなく、応答を返してから閉じる</b>（仕様の graceful closure）。
		 * 応答が無いままストリームが切れると、クライアントは「落ちた」と思って繋ぎ直す
		 */
		assertEquals(2, out.size(), out.toString());

		Data completed = out.get(1);

		assertEquals(7, completed.getInt("id"));
		assertEquals(McpProtocol.RESULT_TYPE_COMPLETE, completed.getData("result").getString("resultType"));
		assertEquals(7, completed.getData("result").getData("_meta").getInt(McpProtocol.META_SUBSCRIPTION_ID));

	}

	// endregion

	// region 道具

	/**
	 * 要求を1つ組み立てる
	 *
	 * @param id		識別子
	 * @param method	メソッド
	 * @param params	引数
	 * @return 要求
	 */
	private static Data request (int id, String method, Data params) {

		Data meta = params.getDataOptional("_meta");
		meta.put(McpProtocol.META_PROTOCOL_VERSION, McpProtocol.VERSION);
		params.put("_meta", meta);

		Data body = new Data();
		body.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		body.put("id", id);
		body.put("method", method);
		body.put("params", params);

		return body;

	}

	/**
	 * 1行にする
	 *
	 * @param body 本文
	 * @return 1行
	 */
	private static String line (Data body) {

		return body.getJsonString();

	}

	/**
	 * 流して、返ってきたものを読む
	 *
	 * @param registry		登録簿
	 * @param dispatcher	ディスパッチャ
	 * @param requests		要求
	 * @return 返ってきたもの
	 * @throws Exception 失敗
	 */
	private static List<Data> run (McpRegistry registry, Dispatcher dispatcher, Data... requests) throws Exception {

		String[] lines = new String[requests.length];

		for (int i = 0; i < requests.length; i++) {
			lines[i] = line(requests[i]);
		}

		return runLines(registry, dispatcher, lines);

	}

	/**
	 * 流して、途中で何かをして、返ってきたものを読む
	 *
	 * @param registry		登録簿
	 * @param dispatcher	ディスパッチャ
	 * @param between		全部流したあとにすること
	 * @param lines			流す行
	 * @return 返ってきたもの
	 * @throws Exception 失敗
	 */
	private static List<Data> runWith (McpRegistry registry, Dispatcher dispatcher
		, Runnable between, Object... lines) throws Exception {

		String[] text = new String[lines.length];

		for (int i = 0; i < lines.length; i++) {
			text[i] = lines[i] instanceof Data data ? line(data) : String.valueOf(lines[i]);
		}

		return parse(runRawLines(registry, dispatcher, between, text));

	}

	/**
	 * 流して、返ってきたものを読む
	 *
	 * @param registry		登録簿
	 * @param dispatcher	ディスパッチャ
	 * @param lines			流す行
	 * @return 返ってきたもの
	 * @throws Exception 失敗
	 */
	private static List<Data> runLines (McpRegistry registry, Dispatcher dispatcher, String... lines) throws Exception {

		return parse(runRawLines(registry, dispatcher, null, lines));

	}

	/**
	 * 流して、返ってきたものをそのまま取る
	 *
	 * @param registry		登録簿
	 * @param dispatcher	ディスパッチャ
	 * @param requests		要求
	 * @return 返ってきたもの
	 * @throws Exception 失敗
	 */
	private static String runRaw (McpRegistry registry, Dispatcher dispatcher, Data... requests) throws Exception {

		String[] lines = new String[requests.length];

		for (int i = 0; i < requests.length; i++) {
			lines[i] = line(requests[i]);
		}

		return runRawLines(registry, dispatcher, null, lines);

	}

	/**
	 * 流す
	 *
	 * @param registry		登録簿
	 * @param dispatcher	ディスパッチャ
	 * @param between		全部流したあとにすること
	 * @param lines			流す行
	 * @return 返ってきたもの
	 * @throws Exception 失敗
	 */
	private static String runRawLines (McpRegistry registry, Dispatcher dispatcher
		, Runnable between, String... lines) throws Exception {

		/*
		 * <b>System.out を差し替えるので、元に戻すところまで面倒を見る。</b>
		 * 戻し忘れると、以降のテストの出力が全部 stderr へ行く
		 */
		java.io.PrintStream original = System.out;

		ByteArrayOutputStream captured = new ByteArrayOutputStream();

		try (PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8)) {

			/*
			 * <b>本番と同じ形にする。</b>本物のプロセスでは
			 * {@code System.out} がクライアントへの口そのものである。
			 * ここを揃えておかないと、{@code McpStdio} が
			 * <b>差し替えを忘れていても気づけない</b>——
			 * アプリの {@code System.out.println} がテストの外へ逃げてしまう
			 */
			System.setOut(out);


			byte[] input = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);

			McpStdio.run(dispatcher, registry, new ByteArrayInputStream(input) {

				@Override
				public int read (byte[] b, int off, int len) {

					int read = super.read(b, off, len);

					// 全部読み終わったところで、外から何かを起こす
					if (read < 0 && between != null) {
						between.run();
					}

					return read;

				}

			}, out);

		} finally {
			System.setOut(original);
		}

		return captured.toString(StandardCharsets.UTF_8);

	}

	/**
	 * 1行ずつ読む
	 *
	 * @param raw 返ってきたもの
	 * @return 読んだもの
	 */
	private static List<Data> parse (String raw) {

		List<Data> list = new ArrayList<>();

		for (String line : raw.split("\n")) {

			if (line.isBlank()) {
				continue;
			}

			list.add(Dson.decodes(line, Data.class));

		}

		return list;

	}

	@Test
	@DisplayName("端末の文字コードに関係なく UTF-8 で書く")
	void alwaysUtf8 () throws Exception {

		McpRegistry registry = new McpRegistry();
		registry.tool("hello", HelloTool::new);

		ByteArrayOutputStream captured = new ByteArrayOutputStream();

		java.io.PrintStream original = System.out;

		/*
		 * <b>文字コードが ASCII の出力を渡す。</b>
		 * {@code LANG} が決まっていないコンテナで {@code System.out} がこうなる。
		 * ここで {@code print} に任せていると、<b>日本語が全部 {@code ?} になって出る</b>——
		 * 例外は出ないので、<b>動いているように見えてしまう</b>
		 */
		try (PrintStream ascii = new PrintStream(captured, true, StandardCharsets.US_ASCII)) {

			System.setOut(ascii);

			String line = line(request(1, McpProtocol.METHOD_TOOLS_LIST, new Data()));

			McpStdio.run(null, registry
				, new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8)), ascii);

		} finally {
			System.setOut(original);
		}

		String out = captured.toString(StandardCharsets.UTF_8);

		assertTrue(out.contains("挨拶"), out);
		assertFalse(out.contains("?"), out);

	}

	@Test
	@DisplayName("識別子の型が違っても取り消せる（1 と 1L を別物にしない）")
	void cancelWithDifferentNumberType () {

		List<Data> written = new java.util.ArrayList<>();

		/*
		 * <b>JSON-RPC の識別子は数でも文字列でもよい。</b>
		 * 同じ 1 でも、通り道によって {@code Integer} にも {@code Long} にもなる。
		 * そのままキーにすると<b>取り消しが効かず、購読が残り続ける</b>
		 */
		McpSubscriptions.Subscription subscription
			= McpSubscriptions.open(Integer.valueOf(1), new Data(), written::add);

		assertEquals(1, McpSubscriptions.openCount());

		McpSubscriptions.cancel(Long.valueOf(1L));

		assertTrue(subscription.isClosed(), "型が違うだけで取り消せていない");
		assertEquals(0, McpSubscriptions.openCount());

	}

	// endregion

}
