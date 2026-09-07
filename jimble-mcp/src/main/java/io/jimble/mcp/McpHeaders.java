package io.jimble.mcp;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ヘッダと本文の突き合わせ（仕様 Streamable HTTP / Server Validation）
 *
 * <p>
 * MCP は本文の一部をヘッダにも写す。中継（ロードバランサや WAF）が
 * 本文を読まずに振り分けたり止めたりできるようにするためである。
 * </p>
 *
 * <p>
 * <b>だから、写しと本文が食い違っていたら断らなければならない</b>（MUST）。
 * 食い違いを許すと、<b>中継はヘッダを見て通し、サーバーは本文を見て動く</b>という
 * ずれが生まれる。テナントごとの振り分けや流量制限を、本文だけ差し替えてすり抜けられる。
 * </p>
 *
 * <p>
 * ここを実装しないと、<b>手元では動くのに中継を挟んだ瞬間に落ちる</b>形になる。
 * </p>
 */
public final class McpHeaders {

	private McpHeaders () {}

	/**
	 * 突き合わせる
	 *
	 * @param context	コンテキスト
	 * @param body		本文
	 * @return	食い違っていれば理由。合っていれば null
	 */
	public static String validate (WebContext context, Data body) {

		String method = body.getStringOptional("method");

		// メソッド
		String headerMethod = header(context, McpProtocol.HEADER_METHOD);
		if (headerMethod == null) {
			return "%s ヘッダがありません".formatted(McpProtocol.HEADER_METHOD);
		}
		if (!headerMethod.equals(method)) {
			return "%s ヘッダの値 '%s' が本文の '%s' と一致しません"
				.formatted(McpProtocol.HEADER_METHOD, headerMethod, method);
		}

		// 対象の名前
		if (McpProtocol.requiresName(method)) {

			String expected = nameOf(body);
			String headerName = decode(header(context, McpProtocol.HEADER_NAME));

			if (headerName == null) {
				return "%s ヘッダがありません".formatted(McpProtocol.HEADER_NAME);
			}

			if (!headerName.equals(expected)) {
				return "%s ヘッダの値 '%s' が本文の '%s' と一致しません"
					.formatted(McpProtocol.HEADER_NAME, headerName, expected);
			}

		}

		// ツールの引数の写し
		return validateParams(context, body);

	}

	/**
	 * ツールの引数の写しを突き合わせる
	 *
	 * @param context	コンテキスト
	 * @param body		本文
	 * @return	食い違っていれば理由。合っていれば null
	 */
	private static String validateParams (WebContext context, Data body) {

		Data arguments = body.getDataOptional("params").getDataOptional("arguments");

		for (Map.Entry<String, String> entry : context.request().source().headers().entrySet()) {

			String lower = entry.getKey().toLowerCase(Locale.ROOT);

			if (!lower.startsWith(McpProtocol.HEADER_PARAM_PREFIX)) {
				continue;
			}

			String name = lower.substring(McpProtocol.HEADER_PARAM_PREFIX.length());
			String headerValue = decode(entry.getValue());

			String bodyValue = findArgument(arguments, name);

			if (bodyValue == null) {
				return "%s ヘッダに対応する値が本文にありません".formatted(entry.getKey());
			}

			if (!matches(headerValue, bodyValue)) {
				return "%s ヘッダの値 '%s' が本文の '%s' と一致しません"
					.formatted(entry.getKey(), headerValue, bodyValue);
			}

		}

		return null;

	}

	/**
	 * 引数を名前で探す
	 *
	 * <p>ヘッダ名は大文字小文字を区別しないので、引数側もそろえて比べる。</p>
	 *
	 * @param arguments	引数
	 * @param name		名前
	 * @return	値。無ければ null
	 */
	private static String findArgument (Data arguments, String name) {

		for (Map.Entry<String, Object> entry : arguments.entrySet()) {

			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue() == null ? null : String.valueOf(entry.getValue());
			}

		}

		return null;

	}

	/**
	 * 値が合っているか
	 *
	 * <p>
	 * 整数は<b>文字ではなく数として</b>比べる（仕様の SHOULD）。
	 * {@code 42} と {@code 42.0} は同じである。
	 * </p>
	 *
	 * @param headerValue	ヘッダの値
	 * @param bodyValue		本文の値
	 * @return	合っている場合 = true
	 */
	private static boolean matches (String headerValue, String bodyValue) {

		if (headerValue.equals(bodyValue)) {
			return true;
		}

		try {
			return new java.math.BigDecimal(headerValue)
				.compareTo(new java.math.BigDecimal(bodyValue)) == 0;
		} catch (NumberFormatException ignore) {
			return false;
		}

	}

	/**
	 * 対象の名前（{@code params.name} か {@code params.uri}）
	 *
	 * @param body	本文
	 * @return	名前
	 */
	public static String nameOf (Data body) {

		Data params = body.getDataOptional("params");

		String name = params.getStringOptional("name");

		return name.isEmpty() ? params.getStringOptional("uri") : name;

	}

	/**
	 * Base64 で包まれていれば解く
	 *
	 * <p>
	 * ヘッダに置けない値（日本語、前後の空白、改行）は
	 * {@code =?base64?....?=} の形で送られてくる。
	 * <b>解かずに比べると、日本語のツール名がすべて食い違い扱いになる。</b>
	 * </p>
	 *
	 * @param value	値
	 * @return	解いたもの
	 */
	public static String decode (String value) {

		if (value == null) {
			return null;
		}

		if (!value.startsWith(McpProtocol.BASE64_PREFIX) || !value.endsWith(McpProtocol.BASE64_SUFFIX)) {
			return value;
		}

		String encoded = value.substring(
			McpProtocol.BASE64_PREFIX.length()
			, value.length() - McpProtocol.BASE64_SUFFIX.length());

		try {
			return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			// 解けないものは、そのまま比べて食い違いにする
			return value;
		}

	}

	/**
	 * ヘッダを引く
	 *
	 * @param context	コンテキスト
	 * @param name		名前
	 * @return	値。無ければ null
	 */
	private static String header (WebContext context, String name) {

		String value = context.request().header().getStringOptional(name.toLowerCase(Locale.ROOT));

		return value.isEmpty() ? null : value;

	}

	/**
	 * 許すオリジンか（仕様 Security &amp; Endpoint。MUST）
	 *
	 * @param origin	オリジン。無ければ null
	 * @param allowed	許すオリジン
	 * @return	許す場合 = true
	 */
	public static boolean isAllowedOrigin (String origin, List<String> allowed) {

		/*
		 * Origin が付いていなければ通す。
		 * ブラウザ以外（CLI・エージェント）は付けてこない。
		 * 付いている場合だけ、DNS リバインディング対策として確かめる。
		 */
		if (origin == null || origin.isEmpty()) {
			return true;
		}

		return allowed.contains(origin);

	}

}
