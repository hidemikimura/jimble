package io.jimble.web.call;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.RequestSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 内部呼び出しのリクエスト（要件 F-W-27）
 *
 * <p>
 * <b>すでに実装してある API のハンドラを、HTTP を通さずにそのまま呼ぶ</b>ための組み立て。
 * MCP のツールから API を流用するのが最初の用途である（要件 F-MCP-15）。
 * </p>
 *
 * <pre>
 * CallResponse response = context.dispatcher().call(context
 *     , CallRequest.of("GET", "/api/posts").query("page", "2"));
 * </pre>
 *
 * <p>
 * <b>外側のリクエストの続きとして走る。</b>
 * ヘッダ・Cookie・セッションは外側のものをそのまま使う。
 * そうしないと、認証を {@code before} に置いている API が
 * 内部から呼んだときだけ 401 になる。
 * 個別に変えたいヘッダは {@link #header(String, String)} で上書きする。
 * </p>
 */
public final class CallRequest {

	/* HTTPメソッド */
	private final String method;

	/* パス（パーセントエンコード済み） */
	private final String path;

	/* ヘッダ（キーは小文字） */
	private final Map<String, String> headers = new LinkedHashMap<>();

	/* クエリパラメータ */
	private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

	/* フォームパラメータ */
	private final Map<String, List<String>> formParams = new LinkedHashMap<>();

	/* Cookie */
	private final Map<String, String> cookies = new LinkedHashMap<>();

	/* 本文 */
	private String bodyText = "";

	/**
	 * コンストラクタ
	 *
	 * @param method	HTTPメソッド
	 * @param path		パス
	 */
	private CallRequest (String method, String path) {

		this.method = Objects.requireNonNull(method, "method").toUpperCase(Locale.ROOT);
		this.path = Objects.requireNonNull(path, "path");

	}

	/**
	 * 組み立てを始める
	 *
	 * @param method	HTTPメソッド
	 * @param path		パス（{@code /api/posts/12} のように確定したもの）
	 * @return	自分
	 */
	public static CallRequest of (String method, String path) {

		return new CallRequest(method, path);

	}

	// region 組み立てる

	/**
	 * クエリパラメータを足す
	 *
	 * @param name	名前
	 * @param value	値
	 * @return	自分
	 */
	public CallRequest query (String name, String value) {

		queryParams.computeIfAbsent(name, key -> new ArrayList<>()).add(value);

		return this;

	}

	/**
	 * フォームパラメータを足す
	 *
	 * @param name	名前
	 * @param value	値
	 * @return	自分
	 */
	public CallRequest form (String name, String value) {

		formParams.computeIfAbsent(name, key -> new ArrayList<>()).add(value);

		return this;

	}

	/**
	 * ヘッダを設定する
	 *
	 * @param name	名前
	 * @param value	値
	 * @return	自分
	 */
	public CallRequest header (String name, String value) {

		headers.put(name.toLowerCase(Locale.ROOT), value);

		return this;

	}

	/**
	 * Cookie を足す
	 *
	 * @param name	名前
	 * @param value	値
	 * @return	自分
	 */
	public CallRequest cookie (String name, String value) {

		cookies.put(name, value);

		return this;

	}

	/**
	 * JSON の本文を設定する
	 *
	 * @param json	JSON
	 * @return	自分
	 */
	public CallRequest json (Data json) {

		return body("application/json", json == null ? "{}" : json.getJsonString());

	}

	/**
	 * 本文を設定する
	 *
	 * @param contentType	型
	 * @param text			本文
	 * @return	自分
	 */
	public CallRequest body (String contentType, String text) {

		this.bodyText = text == null ? "" : text;

		return header("content-type", contentType);

	}

	// endregion

	/**
	 * HTTPメソッド
	 *
	 * @return	HTTPメソッド
	 */
	public String method () {

		return method;

	}

	/**
	 * パス
	 *
	 * @return	パス
	 */
	public String path () {

		return path;

	}

	/**
	 * 入力口にする
	 *
	 * <p>フレームワーク内部から呼ぶ。</p>
	 *
	 * @param outer	外側のコンテキスト
	 * @return	入力口
	 */
	public RequestSource toSource (WebContext outer) {

		Objects.requireNonNull(outer, "outer");

		Map<String, String> mergedHeaders = new LinkedHashMap<>(outer.request().source().headers());
		Map<String, String> mergedCookies = new LinkedHashMap<>(outer.request().source().cookies());

		/*
		 * 外側の本文の情報は引き継がない。
		 * MCP の JSON-RPC の Content-Length のまま内側へ渡すと、
		 * 本文の長さが食い違う。
		 */
		mergedHeaders.remove("content-type");
		mergedHeaders.remove("content-length");
		mergedHeaders.remove("transfer-encoding");

		mergedHeaders.putAll(headers);
		mergedCookies.putAll(cookies);

		byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);

		if (body.length > 0) {
			mergedHeaders.put("content-length", String.valueOf(body.length));
		}

		return new CallRequestSource(this, outer, mergedHeaders, mergedCookies, queryParams, formParams, bodyText);

	}

	/**
	 * 外側の無い入力口を作る
	 *
	 * <p>
	 * <b>HTTP を通らない入口のためのものである。</b>
	 * MCP を stdio で受けるとき（要件 F-MCP-12）、リクエストは標準入力から来るので
	 * 引き継ぐ外側が無い。ヘッダも Cookie も、この組み立てに書いたものだけになる。
	 * </p>
	 *
	 * <p>フレームワーク内部から呼ぶ。</p>
	 *
	 * @return	入力口
	 */
	public RequestSource toRootSource () {

		Map<String, String> requestHeaders = new LinkedHashMap<>(headers);

		byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);

		if (body.length > 0) {
			requestHeaders.put("content-length", String.valueOf(body.length));
		}

		return new CallRequestSource(this, null, requestHeaders
			, new LinkedHashMap<>(cookies), queryParams, formParams, bodyText);

	}

}
