package io.jimble.web.http;

/**
 * ステータスコードの短い言い方（要件 F-C-17 / D-11）
 *
 * <p>
 * 既定のエラー応答に載せる語である。<b>ここに載るのは決め打ちの語だけ</b>で、
 * 例外のメッセージも、SQL も、パスも入れない（要件 NF-S-06）。
 * 原因はログにある。
 * </p>
 *
 * <p>
 * <b>英語のままにしてある。</b>RFC 9110 が定めている語で、
 * ステータス行やクライアントのライブラリに出てくるものと同じである。
 * ここだけ和訳すると、<b>検索しても何も出てこない語</b>になる。
 * </p>
 */
public final class HttpReasons {

	private HttpReasons () {
	}

	/**
	 * 短い言い方を返す
	 *
	 * @param statusCode	ステータスコード
	 * @return	短い言い方
	 */
	public static String of (int statusCode) {

		return switch (statusCode) {

			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 405 -> "Method Not Allowed";
			case 406 -> "Not Acceptable";
			case 408 -> "Request Timeout";
			case 409 -> "Conflict";
			case 410 -> "Gone";
			case 413 -> "Content Too Large";
			case 414 -> "URI Too Long";
			case 415 -> "Unsupported Media Type";
			case 422 -> "Unprocessable Content";
			case 429 -> "Too Many Requests";

			case 500 -> "Internal Server Error";
			case 501 -> "Not Implemented";
			case 502 -> "Bad Gateway";
			case 503 -> "Service Unavailable";
			case 504 -> "Gateway Timeout";

			/*
			 * <b>知らないコードでも、種別だけは言う。</b>
			 * ここで空を返すと、既定の本文が {"status":599,"message":""} になり、
			 * <b>何も分からないのに何かある</b>形になる。
			 */
			default -> statusCode >= 500 ? "Server Error" : "Error";

		};

	}

}
