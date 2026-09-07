package io.jimble.mcp;

/**
 * MCP のプロトコル上の決まり（仕様 2026-07-28）
 *
 * <p>
 * <b>jimble が対応するのはこの1版だけである。</b>
 * MCP は2年で5版が出ており、毎回破壊的な変更が入っている
 * （2026-07-28 では<b>セッションと GET ストリームが消えた</b>）。
 * 複数版を同時に見ると、互換の分岐がコード中に散る。
 * <b>対応する版を明記して、1つだけ実装する。</b>
 * </p>
 */
public final class McpProtocol {

	/** 対応する仕様の版 */
	public static final String VERSION = "2026-07-28";

	/** JSON-RPC の版 */
	public static final String JSONRPC_VERSION = "2.0";

	// region ヘッダ（本文と突き合わせる。仕様 Streamable HTTP / Request Metadata）

	/** ヘッダ：プロトコルの版 */
	public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";

	/** ヘッダ：メソッド名 */
	public static final String HEADER_METHOD = "Mcp-Method";

	/** ヘッダ：対象の名前（ツール名 / リソース URI / プロンプト名） */
	public static final String HEADER_NAME = "Mcp-Name";

	/** ヘッダ：ツール引数の写し（{@code Mcp-Param-XXX}） */
	public static final String HEADER_PARAM_PREFIX = "mcp-param-";

	/** Base64 で包んだ値の頭 */
	public static final String BASE64_PREFIX = "=?base64?";

	/** Base64 で包んだ値の尻 */
	public static final String BASE64_SUFFIX = "?=";

	// endregion

	// region _meta

	/** {@code _meta} のキー：プロトコルの版 */
	public static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";

	/** {@code _meta} のキー：クライアントの情報 */
	public static final String META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo";

	// endregion

	// region メソッド

	/** メソッド：ツールの一覧 */
	public static final String METHOD_TOOLS_LIST = "tools/list";

	/** メソッド：ツールの実行 */
	public static final String METHOD_TOOLS_CALL = "tools/call";

	/** メソッド：リソースの一覧 */
	public static final String METHOD_RESOURCES_LIST = "resources/list";

	/** メソッド：リソースの読み取り */
	public static final String METHOD_RESOURCES_READ = "resources/read";

	/** メソッド：プロンプトの一覧 */
	public static final String METHOD_PROMPTS_LIST = "prompts/list";

	/** メソッド：プロンプトの取得 */
	public static final String METHOD_PROMPTS_GET = "prompts/get";

	// endregion

	/** 結果の種別（完了） */
	public static final String RESULT_TYPE_COMPLETE = "complete";

	private McpProtocol () {}

	/**
	 * {@link #HEADER_NAME} が要るメソッドか
	 *
	 * @param method	メソッド
	 * @return	要る場合 = true
	 */
	public static boolean requiresName (String method) {

		return METHOD_TOOLS_CALL.equals(method)
			|| METHOD_RESOURCES_READ.equals(method)
			|| METHOD_PROMPTS_GET.equals(method);

	}

}
