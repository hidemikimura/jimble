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

	/** メソッド：サーバーの素性と対応している版（仕様 MUST） */
	public static final String METHOD_SERVER_DISCOVER = "server/discover";

	/** メソッド：通知の購読（要件 F-MCP-13） */
	public static final String METHOD_SUBSCRIPTIONS_LISTEN = "subscriptions/listen";

	/** 通知：購読を受け付けた */
	public static final String NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED = "notifications/subscriptions/acknowledged";

	/** 通知：リソースが変わった */
	public static final String NOTIFICATION_RESOURCES_UPDATED = "notifications/resources/updated";

	/** 通知：リソースの一覧が変わった */
	public static final String NOTIFICATION_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";

	/** 通知：要求を取り消した */
	public static final String NOTIFICATION_CANCELLED = "notifications/cancelled";

	// endregion

	/** _meta：サーバーの素性 */
	public static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

	/** _meta：購読の識別子 */
	public static final String META_SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";

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
