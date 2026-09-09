package io.jimble.mcp;

import io.jimble.util.data.Data;

/**
 * 1つの要求への応答
 *
 * <p>
 * <b>ステータスコードは HTTP のためだけのものである。</b>
 * stdio には状態行が無いので、そちらでは {@link #body()} だけを1行として書き出す。
 * 「送るものが無い」（通知への応答）は {@link #none(int)} で表す。
 * </p>
 *
 * @param body			送る本文。無ければ null
 * @param httpStatus	HTTP のステータスコード（stdio では使わない）
 * @param stream		これから長く流し続けるものか（{@code subscriptions/listen}）
 */
public record McpResponse(Data body, int httpStatus, boolean stream) {

	/**
	 * ふつうの応答
	 *
	 * @param body			本文
	 * @param httpStatus	ステータスコード
	 * @return 応答
	 */
	public static McpResponse of (Data body, int httpStatus) {

		return new McpResponse(body, httpStatus, false);

	}

	/**
	 * 送るものが無い（通知を受け取っただけ）
	 *
	 * @param httpStatus ステータスコード
	 * @return 応答
	 */
	public static McpResponse none (int httpStatus) {

		return new McpResponse(null, httpStatus, false);

	}

	/**
	 * これから長く流し続ける
	 *
	 * @return 応答
	 */
	public static McpResponse streaming () {

		return new McpResponse(null, 200, true);

	}

}
