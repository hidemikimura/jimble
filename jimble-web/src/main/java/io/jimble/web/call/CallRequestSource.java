package io.jimble.web.call;

import io.jimble.util.convertor.UploadFile;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.RequestSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部呼び出しの入力口
 *
 * <p>
 * HTTP サーバーを通さずに {@link io.jimble.web.request.Request} を組み立てる。
 * 接続に関わるところ（スキーム・ホスト・接続元）は<b>外側のリクエストのものを名乗る</b>。
 * IP による流量制限や監査ログが、内部から呼んだときだけ別の値になるのを避けるためである。
 * </p>
 */
final class CallRequestSource implements RequestSource {

	/* もとの組み立て */
	private final CallRequest request;

	/* 外側のコンテキスト。無ければ null */
	private final WebContext outer;

	/* ヘッダ */
	private final Map<String, String> headers;

	/* Cookie */
	private final Map<String, String> cookies;

	/* クエリパラメータ */
	private final Map<String, List<String>> queryParams;

	/* フォームパラメータ */
	private final Map<String, List<String>> formParams;

	/* 本文 */
	private final String bodyText;

	/* クエリ文字列 */
	private final String query;

	/* デコード済みのパス */
	private final String decoded;

	/**
	 * コンストラクタ
	 *
	 * @param request		もとの組み立て
	 * @param outer			外側のコンテキスト
	 * @param headers		ヘッダ
	 * @param cookies		Cookie
	 * @param queryParams	クエリパラメータ
	 * @param formParams	フォームパラメータ
	 * @param bodyText		本文
	 */
	CallRequestSource (
		CallRequest request
		, WebContext outer
		, Map<String, String> headers
		, Map<String, String> cookies
		, Map<String, List<String>> queryParams
		, Map<String, List<String>> formParams
		, String bodyText
	) {

		this.request = request;
		this.outer = outer;
		this.headers = headers;
		this.cookies = cookies;
		this.queryParams = new LinkedHashMap<>(queryParams);
		this.formParams = new LinkedHashMap<>(formParams);
		this.bodyText = bodyText;
		this.query = buildQuery(this.queryParams);
		this.decoded = decodePath(request.path());

	}

	/**
	 * パスをセグメントごとにデコードする
	 *
	 * @param path	生のパス
	 * @return	デコード済みのパス
	 */
	private static String decodePath (String path) {

		String[] segments = path.split("/", -1);

		for (int i = 0; i < segments.length; i++) {
			segments[i] = URLDecoder.decode(segments[i], StandardCharsets.UTF_8);
		}

		return String.join("/", segments);

	}

	/**
	 * クエリ文字列を組み立てる
	 *
	 * @param params	クエリパラメータ
	 * @return	クエリ文字列
	 */
	private static String buildQuery (Map<String, List<String>> params) {

		StringBuilder builder = new StringBuilder();

		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			for (String value : entry.getValue()) {
				if (!builder.isEmpty()) {
					builder.append('&');
				}
				builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
				builder.append('=');
				builder.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
			}
		}

		return builder.toString();

	}

	@Override
	public String method () {

		return request.method();

	}

	@Override
	public String rawPath () {

		return request.path();

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * セグメントごとにデコードして返す（要件 F-R-23）。
	 * <b>ここを生のまま返すと、内部呼び出しのときだけ
	 * アクセスログや 404 のメッセージにパーセントエンコードが出る。</b>
	 * </p>
	 */
	@Override
	public String path () {

		return decoded;

	}

	@Override
	public String url () {

		String base = "%s://%s%s".formatted(scheme(), host(), request.path());

		return query.isEmpty() ? base : base + "?" + query;

	}

	@Override
	public String protocol () {

		return outer == null ? "HTTP/1.1" : outer.request().source().protocol();

	}

	@Override
	public String scheme () {

		return outer == null ? "http" : outer.request().source().scheme();

	}

	@Override
	public String host () {

		return outer == null ? "localhost" : outer.request().source().host();

	}

	@Override
	public int port () {

		return outer == null ? 0 : outer.request().source().port();

	}

	@Override
	public String remoteAddress () {

		return outer == null ? "127.0.0.1" : outer.request().source().remoteAddress();

	}

	@Override
	public String query () {

		return query;

	}

	@Override
	public Map<String, String> headers () {

		return headers;

	}

	@Override
	public Map<String, String> cookies () {

		return cookies;

	}

	@Override
	public Map<String, List<String>> queryParams () {

		return queryParams;

	}

	@Override
	public Map<String, List<String>> formParams () {

		return formParams;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 内部呼び出しでファイルは渡せない。
	 * <b>アップロードを受ける API は内部から呼ばない</b>（一時ファイルの持ち主が曖昧になる）。
	 * </p>
	 */
	@Override
	public List<UploadFile> files () {

		return List.of();

	}

	@Override
	public String bodyText (Charset charset) {

		return bodyText;

	}

	@Override
	public InputStream bodyStream () {

		return new ByteArrayInputStream(bodyText.getBytes(StandardCharsets.UTF_8));

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>一時ファイルを作らないので、消すものも無い。</p>
	 */
	@Override
	public void cleanup () {

	}

}
