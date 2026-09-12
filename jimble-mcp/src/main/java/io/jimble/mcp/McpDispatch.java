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
 * JSON-RPC の振り分け（トランスポートを知らない）
 *
 * <p>
 * <b>ここには HTTP の話が1つも無い。</b>{@code Origin} の検証もヘッダの突き合わせも
 * {@link McpHandler}（HTTP）の仕事で、こちらは<b>本文を受け取って本文を返す</b>だけである。
 * stdio（要件 F-MCP-12）が同じものを通せるようにするためで、
 * <b>2つ目の経路を作らないこと</b>がここの目的である
 * （D-92 で「ハンドラを2度書かない」と決めたのと同じ理由）。
 * </p>
 *
 * <p>
 * 返り値の {@link McpResponse#httpStatus()} は HTTP のための助言で、
 * stdio は本文だけを1行として書き出す。
 * </p>
 */
public final class McpDispatch {

	/** 登録簿 */
	private final McpRegistry registry;

	/**
	 * コンストラクタ
	 *
	 * @param registry 登録簿
	 */
	public McpDispatch (McpRegistry registry) {

		this.registry = registry;

	}

	/**
	 * 1つの要求を処理する
	 *
	 * @param context		コンテキスト（ツールはこれを通してアプリの中を触る）
	 * @param body			JSON-RPC の本文
	 * @param headerVersion	トランスポートが別に運んできた版。stdio では null
	 * @return 応答
	 * @throws Exception どうしようもない失敗
	 */
	public McpResponse handle (WebContext context, Data body, String headerVersion) throws Exception {

		if (body == null || body.isEmpty() || body.getStringOptional("method").isEmpty()) {
			return McpResponse.of(McpErrors.of(null, McpErrors.PARSE_ERROR
				, "JSON-RPC の要求として読めません"), 400);
		}

		Object id = body.get("id");
		String method = body.getString("method");
		Data params = body.getDataOptional("params");

		/*
		 * 通知（id が無い）は受け取るだけ。本文は付けない（仕様 MUST）。
		 * HTTP では 202 になる。
		 */
		if (id == null) {
			return notification(method, params);
		}

		/*
		 * プロトコルの版。
		 *
		 * <b>本文の _meta が正で、ヘッダはその写しである</b>（仕様）。
		 * HTTP はヘッダを運んでくるので突き合わせる（要件 F-MCP-05）。
		 * stdio にはヘッダの層が無いので、_meta だけを見る。
		 */
		String metaVersion = params.getDataOptional("_meta")
			.getStringOptional(McpProtocol.META_PROTOCOL_VERSION);

		if (headerVersion != null && !metaVersion.isEmpty() && !metaVersion.equals(headerVersion)) {
			return McpResponse.of(McpErrors.of(id, McpErrors.HEADER_MISMATCH
				, "%s ヘッダの値 '%s' が本文の _meta の '%s' と一致しません"
					.formatted(McpProtocol.HEADER_PROTOCOL_VERSION, headerVersion, metaVersion)), 400);
		}

		String version = headerVersion != null ? headerVersion : (metaVersion.isEmpty() ? null : metaVersion);

		/*
		 * <b>server/discover には版を求めない。</b>
		 * これは「どの版で話せるか」を訊くための呼び出しである。
		 * ここで版を要求すると、<b>版を知るために版が要る</b>ことになり、
		 * 初めて繋ぐクライアントは何もできない。
		 * 知らない版を名乗ってきた場合も、断らずに対応している版を並べて返す
		 * （それが訊かれていることそのものである）。
		 */
		if (McpProtocol.METHOD_SERVER_DISCOVER.equals(method)) {
			return dispatch(context, id, method, params);
		}

		if (version == null) {
			return McpResponse.of(McpErrors.of(id, McpErrors.HEADER_MISMATCH
				, "プロトコルの版がありません（本文の params._meta.%s に入れてください）"
					.formatted(McpProtocol.META_PROTOCOL_VERSION)), 400);
		}

		if (!McpProtocol.version().equals(version)) {
			return McpResponse.of(McpErrors.unsupportedVersion(id, version), 400);
		}

		return dispatch(context, id, method, params);

	}

	/**
	 * 通知を受け取る
	 *
	 * @param method	メソッド
	 * @param params	引数
	 * @return 応答（本文なし）
	 */
	private McpResponse notification (String method, Data params) {

		/*
		 * 取り消しは stdio でだけ意味を持つ。
		 * HTTP には応答のストリームがあり、閉じることで取り消しを伝える。
		 */
		if (McpProtocol.NOTIFICATION_CANCELLED.equals(method)) {
			McpSubscriptions.cancel(params.get("requestId"));
		}

		return McpResponse.none(202);

	}

	/**
	 * メソッドごとに振り分ける
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param method	メソッド
	 * @param params	引数
	 * @return 応答
	 * @throws Exception どうしようもない失敗
	 */
	private McpResponse dispatch (WebContext context, Object id, String method, Data params) throws Exception {

		try {

			return switch (method) {

				case McpProtocol.METHOD_SERVER_DISCOVER -> McpResponse.of(result(id, discover()), 200);

				case McpProtocol.METHOD_TOOLS_LIST -> McpResponse.of(result(id, toolsList(params)), 200);
				case McpProtocol.METHOD_TOOLS_CALL -> toolsCall(context, id, params);

				case McpProtocol.METHOD_RESOURCES_LIST -> McpResponse.of(result(id, resourcesList(params)), 200);
				case McpProtocol.METHOD_RESOURCES_READ -> resourcesRead(context, id, params);

				case McpProtocol.METHOD_PROMPTS_LIST -> McpResponse.of(result(id, promptsList(params)), 200);
				case McpProtocol.METHOD_PROMPTS_GET -> promptsGet(context, id, params);

				// 長く流し続けるものなので、トランスポートに任せる（要件 F-MCP-13）
				case McpProtocol.METHOD_SUBSCRIPTIONS_LISTEN -> McpResponse.streaming();

				/*
				 * 知らないメソッドは 404。
				 * 仕様がこう決めているのは、古い HTTP+SSE のサーバーが返す 404 と
				 * 区別できるようにするためである（本文に JSON-RPC のエラーが入っているかで見る）。
				 */
				default -> McpResponse.of(McpErrors.of(id, McpErrors.METHOD_NOT_FOUND
					, "知らないメソッドです: " + method), 404);

			};

		} catch (McpPaging.McpPagingException ex) {

			// 読めないカーソルは -32602（仕様 SHOULD）
			return McpResponse.of(McpErrors.of(id, McpErrors.INVALID_PARAMS, ex.getMessage()), 200);

		}

	}

	// region server/discover

	/**
	 * サーバーの素性と対応している版
	 *
	 * <p>
	 * <b>仕様はこれを「サーバーは実装しなければならない」と定めている。</b>
	 * とくに stdio では、クライアントが<b>世代を見分けるのにこれを使う</b>——
	 * HTTP と違ってステータスコードが無いので、これに応えないサーバーは
	 * 「古い世代（{@code initialize} が要る）」と判断され、
	 * <b>jimble はそちらにも応えないので繋がらない</b>。
	 * </p>
	 *
	 * @return 結果
	 */
	private Data discover () {

		Data capabilities = new Data();

		// <b>空のものは出さない。</b>ツールを1つも持たないサーバーが「tools 対応」と名乗らない
		if (!registry.tools().isEmpty()) {
			capabilities.put("tools", new Data());
		}

		if (!registry.resources().isEmpty()) {
			capabilities.put("resources", new Data());
		}

		if (!registry.prompts().isEmpty()) {
			capabilities.put("prompts", new Data());
		}

		Data serverInfo = new Data();
		serverInfo.put("name", McpConf.name());
		serverInfo.put("version", McpConf.version());

		Data meta = new Data();
		meta.put(McpProtocol.META_SERVER_INFO, serverInfo);

		Data result = new Data();
		result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		result.put("supportedVersions", List.of(McpProtocol.version()));
		result.put("capabilities", capabilities);
		result.put("_meta", meta);

		String instructions = McpConf.instructions();

		if (!instructions.isEmpty()) {
			result.put("instructions", instructions);
		}

		return result;

	}

	// endregion

	// region ツール

	/**
	 * ツールの一覧
	 *
	 * @param params 引数（cursor）
	 * @return 一覧
	 * @throws McpPaging.McpPagingException 読めないカーソル
	 */
	private Data toolsList (Data params) throws McpPaging.McpPagingException {

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

		return page("tools", list, params);

	}

	/**
	 * ツールを実行する
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @return 応答
	 * @throws Exception どうしようもない失敗
	 */
	private McpResponse toolsCall (WebContext context, Object id, Data params) throws Exception {

		String name = params.getStringOptional("name");

		Supplier<McpTool> supplier = registry.tools().get(name);

		if (supplier == null) {
			/*
			 * 知らないツールは「プロトコルのエラー」。
			 * isError で返すと、モデルは存在しないツールを呼び直す。
			 */
			return McpResponse.of(McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないツールです: " + name), 200);
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
			return McpResponse.of(result(id, ToolResult.error(missing).toData()), 200);
		}

		try {

			return McpResponse.of(result(id, tool.call(context, arguments).toData()), 200);

		} catch (Exception ex) {

			/*
			 * ツールが例外を投げた。
			 * 中身は外に出さない（何が動いているか教えることになる）。
			 * ログには残す。
			 */
			Log.error(ex, "ツールの実行に失敗しました: " + name);

			return McpResponse.of(result(id, ToolResult.error(
				"ツールの実行に失敗しました: " + name).toData()), 200);

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
	 * @param params 引数（cursor）
	 * @return 一覧
	 * @throws McpPaging.McpPagingException 読めないカーソル
	 */
	private Data resourcesList (Data params) throws McpPaging.McpPagingException {

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

		return page("resources", list, params);

	}

	/**
	 * リソースを読む
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @return 応答
	 * @throws Exception どうしようもない失敗
	 */
	private McpResponse resourcesRead (WebContext context, Object id, Data params) throws Exception {

		String uri = params.getStringOptional("uri");

		Supplier<McpResource> supplier = registry.resources().get(uri);

		if (supplier == null) {
			return McpResponse.of(McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないリソースです: " + uri), 200);
		}

		McpResource resource = supplier.get();

		return McpResponse.of(result(id, resource.toContents(uri, resource.read(context, uri))), 200);

	}

	// endregion

	// region プロンプト

	/**
	 * プロンプトの一覧
	 *
	 * @param params 引数（cursor）
	 * @return 一覧
	 * @throws McpPaging.McpPagingException 読めないカーソル
	 */
	private Data promptsList (Data params) throws McpPaging.McpPagingException {

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

		return page("prompts", list, params);

	}

	/**
	 * プロンプトを取る
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子
	 * @param params	引数
	 * @return 応答
	 * @throws Exception どうしようもない失敗
	 */
	private McpResponse promptsGet (WebContext context, Object id, Data params) throws Exception {

		String name = params.getStringOptional("name");

		Supplier<McpPrompt> supplier = registry.prompts().get(name);

		if (supplier == null) {
			return McpResponse.of(McpErrors.of(id, McpErrors.INVALID_PARAMS, "知らないプロンプトです: " + name), 200);
		}

		McpPrompt prompt = supplier.get();
		Data arguments = params.getDataOptional("arguments");

		return McpResponse.of(result(id
			, McpPrompt.toResult(prompt.description(), prompt.get(context, arguments))), 200);

	}

	// endregion

	// region 組み立て

	/**
	 * 一覧を1ページぶんにして結果にする（要件 F-MCP-14）
	 *
	 * @param key		一覧のキー（{@code tools} など）
	 * @param all		全部
	 * @param params	引数（cursor）
	 * @return 結果
	 * @throws McpPaging.McpPagingException 読めないカーソル
	 */
	private static Data page (String key, List<Data> all, Data params) throws McpPaging.McpPagingException {

		McpPaging.Page page = McpPaging.of(all, params.getStringOptional("cursor"));

		Data result = new Data();
		result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		result.put(key, page.items());

		if (page.nextCursor() != null) {
			result.put("nextCursor", page.nextCursor());
		}

		return result;

	}

	/**
	 * 成功の応答を組み立てる
	 *
	 * @param id		要求の識別子
	 * @param result	結果
	 * @return	応答
	 */
	static Data result (Object id, Data result) {

		Data response = new Data();
		response.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		response.put("id", id);
		response.put("result", result);

		return response;

	}

	// endregion

}
