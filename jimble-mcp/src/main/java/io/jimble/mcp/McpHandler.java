package io.jimble.mcp;

import io.jimble.mcp.prompt.McpPrompt;
import io.jimble.mcp.resource.McpResource;
import io.jimble.mcp.tool.McpTool;
import io.jimble.mcp.tool.ToolResult;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP の POST を1本で受ける（仕様 Streamable HTTP / 2026-07-28）
 *
 * <pre>
 * POST /mcp
 *   本文が通知      → 202 Accepted（本文なし）
 *   本文が要求      → application/json で1個返す
 *   知らないメソッド → 404 ＋ JSON-RPC -32601
 * </pre>
 *
 * <p>
 * <b>GET と DELETE は 405 で断る。</b>2025-11-25 までは
 * GET で常時接続を開き、{@code Mcp-Session-Id} でセッションを持っていたが、
 * <b>2026-07-28 でどちらも消えた。</b>
 * 古いクライアントが来たときに「動くように見えて途中で壊れる」より、
 * はっきり断るほうがよい。
 * </p>
 */
public final class McpHandler {

	/** 受け取れる中身の型 */
	private static final String CONTENT_TYPE_JSON = "application/json";

	/* 公開しているもの */
	private final McpRegistry registry;

	/**
	 * コンストラクタ
	 *
	 * @param registry	公開しているもの
	 */
	public McpHandler (McpRegistry registry) {

		this.registry = registry;

	}

	/**
	 * POST を受ける
	 *
	 * @param context	コンテキスト
	 * @throws Exception	どうしようもない失敗
	 */
	public void handle (WebContext context) throws Exception {

		/*
		 * 1. Origin（仕様 MUST）。
		 *    DNS リバインディングで、外のページからローカルの MCP サーバーを
		 *    叩かれるのを防ぐ。
		 */
		String origin = context.request().header().getStringOptional("origin");

		if (!McpHeaders.isAllowedOrigin(origin, McpConf.allowedOrigins())) {
			send(context, 403, McpErrors.of(null, McpErrors.INVALID_REQUEST
				, "許可されていないオリジンです: " + origin));
			return;
		}

		// 2. 本文を読む
		Data body = context.request().bodyJson();

		if (body.isEmpty() || body.getStringOptional("method").isEmpty()) {
			send(context, 400, McpErrors.of(null, McpErrors.PARSE_ERROR, "JSON-RPC の要求として読めません"));
			return;
		}

		Object id = body.get("id");
		String method = body.getString("method");

		/*
		 * 3. 通知（id が無い）は 202 で受け取るだけ。本文は付けない（仕様 MUST）。
		 *
		 * code(202).send() ではいけない。Accept に application/json があると
		 * 「JSON を求められている」と見なして空の {} を返してしまう。
		 * send(202) は本文なしで送る。
		 */
		if (id == null) {
			context.response().send(202);
			return;
		}

		// 4. プロトコルの版
		String version = header(context, McpProtocol.HEADER_PROTOCOL_VERSION);

		if (version == null) {
			send(context, 400, McpErrors.of(id, McpErrors.HEADER_MISMATCH
				, "%s ヘッダがありません".formatted(McpProtocol.HEADER_PROTOCOL_VERSION)));
			return;
		}

		if (!McpProtocol.VERSION.equals(version)) {
			send(context, 400, McpErrors.unsupportedVersion(id, version));
			return;
		}

		String metaVersion = body.getDataOptional("params")
			.getDataOptional("_meta")
			.getStringOptional(McpProtocol.META_PROTOCOL_VERSION);

		if (!metaVersion.isEmpty() && !metaVersion.equals(version)) {
			send(context, 400, McpErrors.of(id, McpErrors.HEADER_MISMATCH
				, "%s ヘッダの値 '%s' が本文の _meta の '%s' と一致しません"
					.formatted(McpProtocol.HEADER_PROTOCOL_VERSION, version, metaVersion)));
			return;
		}

		// 5. ヘッダと本文の突き合わせ
		String mismatch = McpHeaders.validate(context, body);

		if (mismatch != null) {
			send(context, 400, McpErrors.of(id, McpErrors.HEADER_MISMATCH, mismatch));
			return;
		}

		// 6. 振り分ける
		dispatch(context, id, method, body.getDataOptional("params"));

	}

	/**
	 * 振り分ける
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param method	メソッド
	 * @param params	引数
	 * @throws Exception	どうしようもない失敗
	 */
	private void dispatch (WebContext context, Object id, String method, Data params) throws Exception {

		switch (method) {

			case McpProtocol.METHOD_TOOLS_LIST -> send(context, 200, result(id, toolsList()));
			case McpProtocol.METHOD_TOOLS_CALL -> toolsCall(context, id, params);

			case McpProtocol.METHOD_RESOURCES_LIST -> send(context, 200, result(id, resourcesList()));
			case McpProtocol.METHOD_RESOURCES_READ -> resourcesRead(context, id, params);

			case McpProtocol.METHOD_PROMPTS_LIST -> send(context, 200, result(id, promptsList()));
			case McpProtocol.METHOD_PROMPTS_GET -> promptsGet(context, id, params);

			/*
			 * 知らないメソッドは 404。
			 * 仕様がこう決めているのは、古い HTTP+SSE のサーバーが返す 404 と
			 * 区別できるようにするためである（本文に JSON-RPC のエラーが入っているかで見る）。
			 */
			default -> send(context, 404, McpErrors.of(id, McpErrors.METHOD_NOT_FOUND
				, "知らないメソッドです: " + method));

		}

	}

	// region ツール

	/**
	 * ツールの一覧
	 *
	 * @return	一覧
	 */
	private Data toolsList () {

		List<Data> list = new ArrayList<>();

		for (Map.Entry<String, Supplier<McpTool>> entry : registry.tools().entrySet()) {

			McpTool tool = entry.getValue().get();

			Data data = new Data();
			data.put("name", entry.getKey());

			if (tool.title() != null) {
				data.put("title", tool.title());
			}

			data.put("description", tool.description());
			data.put("inputSchema", tool.inputSchema().toData());

			if (tool.outputSchema() != null) {
				data.put("outputSchema", tool.outputSchema().toData());
			}

			list.add(data);

		}

		Data result = new Data();
		result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		result.put("tools", list);

		return result;

	}

	/**
	 * ツールを実行する
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @throws Exception	どうしようもない失敗
	 */
	private void toolsCall (WebContext context, Object id, Data params) throws Exception {

		String name = params.getStringOptional("name");

		Supplier<McpTool> supplier = registry.tools().get(name);

		if (supplier == null) {
			/*
			 * 知らないツールは「プロトコルのエラー」。
			 * isError で返すと、モデルは存在しないツールを呼び直す。
			 */
			send(context, 200, McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないツールです: " + name));
			return;
		}

		// 呼ばれるたびに1つ作る（原則3）
		McpTool tool = supplier.get();

		Data arguments = params.getDataOptional("arguments");

		String missing = missingRequired(tool, arguments);

		if (missing != null) {
			/*
			 * 引数が足りないのは「モデルが直せる」種類なので、
			 * プロトコルのエラーではなく isError で返す。
			 */
			send(context, 200, result(id, ToolResult.error(missing).toData()));
			return;
		}

		try {

			send(context, 200, result(id, tool.call(context, arguments).toData()));

		} catch (Exception ex) {

			/*
			 * ツールが例外を投げた。
			 * 中身は外に出さない（何が動いているか教えることになる）。
			 * ログには残す。
			 */
			Log.error(ex, "ツールの実行に失敗しました: " + name);

			send(context, 200, result(id, ToolResult.error(
				"ツールの実行に失敗しました: " + name).toData()));

		}

	}

	/**
	 * 足りない必須の引数
	 *
	 * @param tool		ツール
	 * @param arguments	引数
	 * @return	足りなければ内容。足りていれば null
	 */
	private static String missingRequired (McpTool tool, Data arguments) {

		List<String> missing = new ArrayList<>();

		for (String name : tool.inputSchema().requiredNames()) {
			if (!arguments.containsKey(name) || arguments.get(name) == null) {
				missing.add(name);
			}
		}

		if (missing.isEmpty()) {
			return null;
		}

		// まとめて言う（要件 F-U-03 / F-X-05 と同じ考え方）
		return "必須の引数がありません: " + String.join(", ", missing);

	}

	// endregion

	// region リソース

	/**
	 * リソースの一覧
	 *
	 * @return	一覧
	 */
	private Data resourcesList () {

		List<Data> list = new ArrayList<>();

		for (Map.Entry<String, Supplier<McpResource>> entry : registry.resources().entrySet()) {

			McpResource resource = entry.getValue().get();

			Data data = new Data();
			data.put("uri", entry.getKey());
			data.put("name", resource.title() == null ? entry.getKey() : resource.title());
			data.put("description", resource.description());
			data.put("mimeType", resource.mimeType());

			list.add(data);

		}

		Data result = new Data();
		result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		result.put("resources", list);

		return result;

	}

	/**
	 * リソースを読む
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @throws Exception	どうしようもない失敗
	 */
	private void resourcesRead (WebContext context, Object id, Data params) throws Exception {

		String uri = params.getStringOptional("uri");

		Supplier<McpResource> supplier = registry.resources().get(uri);

		if (supplier == null) {
			send(context, 200, McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないリソースです: " + uri));
			return;
		}

		McpResource resource = supplier.get();

		send(context, 200, result(id, resource.toContents(uri, resource.read(context, uri))));

	}

	// endregion

	// region プロンプト

	/**
	 * プロンプトの一覧
	 *
	 * @return	一覧
	 */
	private Data promptsList () {

		List<Data> list = new ArrayList<>();

		for (Map.Entry<String, Supplier<McpPrompt>> entry : registry.prompts().entrySet()) {

			McpPrompt prompt = entry.getValue().get();

			List<Data> arguments = new ArrayList<>();
			for (McpPrompt.Argument argument : prompt.arguments()) {
				arguments.add(argument.toData());
			}

			Data data = new Data();
			data.put("name", entry.getKey());
			data.put("description", prompt.description());
			data.put("arguments", arguments);

			list.add(data);

		}

		Data result = new Data();
		result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		result.put("prompts", list);

		return result;

	}

	/**
	 * プロンプトを取る
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @throws Exception	どうしようもない失敗
	 */
	private void promptsGet (WebContext context, Object id, Data params) throws Exception {

		String name = params.getStringOptional("name");

		Supplier<McpPrompt> supplier = registry.prompts().get(name);

		if (supplier == null) {
			send(context, 200, McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないプロンプトです: " + name));
			return;
		}

		McpPrompt prompt = supplier.get();
		Data arguments = params.getDataOptional("arguments");

		send(context, 200, result(id
			, McpPrompt.toResult(prompt.description(), prompt.get(context, arguments))));

	}

	// endregion

	// region 送る

	/**
	 * 成功の応答を組み立てる
	 *
	 * @param id		要求の識別子
	 * @param result	結果
	 * @return	応答
	 */
	private static Data result (Object id, Data result) {

		Data response = new Data();
		response.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		response.put("id", id);
		response.put("result", result);

		return response;

	}

	/**
	 * 送る
	 *
	 * @param context	コンテキスト
	 * @param code		ステータスコード
	 * @param body		本文
	 */
	private static void send (WebContext context, int code, Data body) {

		context.response().code(code);
		context.response().setResponseHeader("Content-Type", CONTENT_TYPE_JSON + "; charset=UTF-8");
		context.response().send(body.getJsonString(), CONTENT_TYPE_JSON);

	}

	/**
	 * ヘッダを引く
	 *
	 * @param context	コンテキスト
	 * @param name		名前
	 * @return	値。無ければ null
	 */
	private static String header (WebContext context, String name) {

		String value = context.request().header().getStringOptional(name.toLowerCase(java.util.Locale.ROOT));

		return value.isEmpty() ? null : value;

	}

	// endregion

}
