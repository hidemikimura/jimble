package io.jimble.mcp;

import io.jimble.util.data.Data;

/**
 * MCP のエラー（JSON-RPC 2.0 ＋ 仕様が足したもの）
 *
 * <p>
 * <b>エラーには2種類ある。</b>混ぜてはいけない。
 * </p>
 *
 * <ol>
 *   <li><b>プロトコルのエラー。</b>要求の形が悪い。ここで作るもの。
 *       モデルには直しようがない</li>
 *   <li><b>ツールの実行エラー。</b>結果の {@code isError: true} で返す
 *       （{@link io.jimble.mcp.tool.ToolResult#error}）。
 *       <b>モデルが読んで直せる</b>ように、何が悪いかを書く</li>
 * </ol>
 *
 * <p>
 * 「日付の形式が違う」をプロトコルエラーで返すと、
 * <b>モデルは直しようがないまま諦める。</b>逆に「知らないツール」を
 * {@code isError} で返すと、モデルは存在しないツールを呼び直す。
 * </p>
 */
public final class McpErrors {

	/** JSON が読めない */
	public static final int PARSE_ERROR = -32700;

	/** 要求の形が違う */
	public static final int INVALID_REQUEST = -32600;

	/** 知らないメソッド */
	public static final int METHOD_NOT_FOUND = -32601;

	/** 引数が違う */
	public static final int INVALID_PARAMS = -32602;

	/** サーバーの中で失敗した */
	public static final int INTERNAL_ERROR = -32603;

	/** ヘッダと本文が食い違う（仕様が定める） */
	public static final int HEADER_MISMATCH = -32020;

	private McpErrors () {}

	/**
	 * エラーの応答を作る
	 *
	 * @param id		要求の識別子。分からなければ null
	 * @param code		コード
	 * @param message	内容
	 * @return	応答
	 */
	public static Data of (Object id, int code, String message) {

		Data error = new Data();
		error.put("code", code);
		error.put("message", message);

		Data response = new Data();
		response.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		response.put("id", id);
		response.put("error", error);

		return response;

	}

	/**
	 * 対応していない版だと伝える
	 *
	 * <p>対応している版を返すのが仕様の求めるところである。</p>
	 *
	 * @param id		要求の識別子
	 * @param requested	求められた版
	 * @return	応答
	 */
	public static Data unsupportedVersion (Object id, String requested) {

		Data error = new Data();
		error.put("code", INVALID_REQUEST);
		error.put("message", "対応していないプロトコルの版です: %s".formatted(requested));

		Data data = new Data();
		data.put("supported", java.util.List.of(McpProtocol.version()));
		error.put("data", data);

		Data response = new Data();
		response.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
		response.put("id", id);
		response.put("error", error);

		return response;

	}

}
