package io.jimble.web.proxy;

import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Controller;
import io.jimble.web.router.Handler;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * リバースプロキシ（要件 F-R-14）
 *
 * <p>
 * 受けたリクエストをそのまま別のサーバーへ流し、応答をそのまま返す。
 * </p>
 *
 * <pre>
 * // /api/** を http://backend:8080/** に流す
 * install(() -&gt; ReverseProxy.mount("/api", "http://backend:8080"));
 * </pre>
 *
 * <p>
 * <b>全メソッドを1回で登録する</b>（要件 F-R-21）。移送元は 14 回手書きしていた。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>ステータスコードを返していなかった。</b>移送元はヘッダと本文だけを写しており、
 *       <b>転送先が 404 でも 500 でもクライアントには 200 が返っていた。</b>
 *       これがいちばん影響が大きい</li>
 *   <li><b>hop-by-hop ヘッダをそのまま転送していた</b>（{@link HopByHopHeaders} 参照）</li>
 *   <li><b>{@code X-Forwarded-Proto} に {@code HTTP/1.1} を入れていた。</b>
 *       {@code ctx.getProtocol()} はプロトコルのバージョンであって scheme ではない。
 *       転送先が「HTTPS かどうか」を判定できない</li>
 *   <li><b>{@code X-Forwarded-For} を上書きしていた。</b>多段になったとき経路が消える。
 *       既存の値があれば後ろに足す</li>
 *   <li><b>タイムアウトが無かった。</b>転送先が応答しないとスレッドが張り付く</li>
 * </ol>
 */
public final class ReverseProxy implements Handler {

	/** 本文を送らないメソッド */
	private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "DELETE", "OPTIONS", "TRACE");

	/** 転送先が応答しないときのステータスコード */
	public static final int BAD_GATEWAY = 502;

	/* 転送先のベース URL */
	private final String forwardBaseUrl;

	/* HTTP クライアント */
	private final HttpClient client;

	/* 応答待ちの上限 */
	private final Duration requestTimeout;

	/**
	 * コンストラクタ
	 *
	 * @param forwardBaseUrl	転送先のベース URL（例 {@code http://backend:8080}）
	 */
	public ReverseProxy (String forwardBaseUrl) {

		this(forwardBaseUrl, ProxyConf.connectTimeout(), ProxyConf.requestTimeout());

	}

	/**
	 * コンストラクタ
	 *
	 * @param forwardBaseUrl	転送先のベース URL
	 * @param connectTimeout	接続の上限
	 * @param requestTimeout	応答待ちの上限
	 */
	public ReverseProxy (String forwardBaseUrl, Duration connectTimeout, Duration requestTimeout) {

		this.forwardBaseUrl = trimTrailingSlash(forwardBaseUrl);
		this.requestTimeout = requestTimeout;
		this.client = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			// 転送先のリダイレクトはクライアントに返す。勝手に追わない
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		try {

			HttpRequest request = buildRequest(context);

			HttpResponse<InputStream> response =
				client.send(request, HttpResponse.BodyHandlers.ofInputStream());

			sendResponse(context, response);

		} catch (Exception ex) {

			Log.warn("リバースプロキシに失敗しました: %s / %s".formatted(forwardBaseUrl, ex.getMessage()));
			context.response().send(BAD_GATEWAY);

		}

	}

	// region リクエスト

	/**
	 * 転送するリクエストを組み立てる
	 *
	 * @param context	コンテキスト
	 * @return	リクエスト
	 */
	private HttpRequest buildRequest (WebContext context) {

		String method = context.request().method();

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(forwardUrl(context)))
			.timeout(requestTimeout)
			.method(method, bodyPublisher(context, method));

		forwardRequestHeaders(context, builder);
		addForwardedHeaders(context, builder);

		return builder.build();

	}

	/**
	 * 転送先の URL
	 *
	 * @param context	コンテキスト
	 * @return	URL
	 */
	private String forwardUrl (WebContext context) {

		String wildcard = context.route() == null ? "" : context.route().variables().wildcard();
		String path = wildcard == null || wildcard.isEmpty() ? "" : "/" + wildcard;

		String query = context.request().query();

		return forwardBaseUrl + path + (query == null || query.isEmpty() ? "" : "?" + query);

	}

	/**
	 * 本文
	 *
	 * @param context	コンテキスト
	 * @param method	メソッド
	 * @return	本文
	 */
	private HttpRequest.BodyPublisher bodyPublisher (WebContext context, String method) {

		if (BODYLESS_METHODS.contains(method)) {
			return HttpRequest.BodyPublishers.noBody();
		}

		// 本文を読み切らずに流す
		return HttpRequest.BodyPublishers.ofInputStream(() -> context.request().source().bodyStream());

	}

	/**
	 * リクエストヘッダを転送する
	 *
	 * @param context	コンテキスト
	 * @param builder	リクエスト
	 */
	private void forwardRequestHeaders (WebContext context, HttpRequest.Builder builder) {

		for (Map.Entry<String, String> entry : context.request().source().headers().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())) {
				continue;
			}

			try {
				builder.header(entry.getKey(), entry.getValue());
			} catch (IllegalArgumentException ignore) {
				// JDK が拒否するヘッダ（Host など）。転送しないだけでよい
			}

		}

	}

	/**
	 * X-Forwarded-* を足す
	 *
	 * @param context	コンテキスト
	 * @param builder	リクエスト
	 */
	private void addForwardedHeaders (WebContext context, HttpRequest.Builder builder) {

		/*
		 * setHeader で「置き換える」。header だと値が追加されるので、
		 * クライアントが送ってきた X-Forwarded-For がそのまま残り、
		 * 転送先は先頭の値（＝クライアントの自己申告）を読んでしまう。
		 */

		// 移送元は getProtocol()（= HTTP/1.1）を入れていた
		builder.setHeader("X-Forwarded-Proto", context.request().scheme());

		// ポートまで含めたいので Host ヘッダを使う
		String host = context.request().header().getStringOptional("host");
		builder.setHeader("X-Forwarded-Host", host.isEmpty() ? context.request().host() : host);

		builder.setHeader("X-Real-IP", context.request().address());

		// 多段のときに経路が消えないよう、既存の値の後ろに足す
		String existing = context.request().header().getStringOptional("x-forwarded-for");
		String forwardedFor = existing.isEmpty()
			? context.request().address()
			: existing + ", " + context.request().address();

		builder.setHeader("X-Forwarded-For", forwardedFor);

	}

	// endregion

	// region レスポンス

	/**
	 * 応答を返す
	 *
	 * @param context	コンテキスト
	 * @param response	転送先の応答
	 * @throws Exception	送信に失敗した場合
	 */
	private void sendResponse (WebContext context, HttpResponse<InputStream> response) throws Exception {

		// 移送元はここが抜けていて、転送先が何を返しても 200 になっていた
		context.response().code(response.statusCode());

		for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())) {
				continue;
			}

			for (String value : entry.getValue()) {
				context.response().setResponseHeader(entry.getKey(), value);
			}

		}

		// 本文を持てないステータスに本文を書くと、サーバー実装によっては例外になる
		if (isBodyless(response.statusCode())) {
			response.body().close();
			context.response().send(response.statusCode());
			return;
		}

		try (InputStream body = response.body()) {
			context.response().send(body);
		}

	}

	/**
	 * 本文を持てないステータスか
	 *
	 * @param statusCode	ステータスコード
	 * @return	持てない場合 = true
	 */
	private static boolean isBodyless (int statusCode) {

		return statusCode < 200 || statusCode == 204 || statusCode == 304;

	}

	// endregion

	/**
	 * 末尾の「/」を落とす
	 *
	 * @param url	URL
	 * @return	落としたもの
	 */
	private static String trimTrailingSlash (String url) {

		if (url == null || url.isEmpty()) {
			return "";
		}

		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

	}

	// region ルートへの組み込み

	/**
	 * ルートに組み込む
	 *
	 * @param routePath			ルートのパス（例 {@code /api}）
	 * @param forwardBaseUrl	転送先のベース URL
	 * @return	コントローラ
	 */
	public static Controller mount (String routePath, String forwardBaseUrl) {

		// 1つだけ作って使い回す。HttpClient は接続を持つので毎回作ってはいけない
		ReverseProxy proxy = new ReverseProxy(forwardBaseUrl);

		return mount(routePath, context -> proxy);

	}

	/**
	 * ルートに組み込む
	 *
	 * @param routePath	ルートのパス
	 * @param factory	リクエストから転送先を決める。
	 *					<b>リクエストごとに呼ばれる</b>ので、毎回 {@code new} してはいけない
	 *					（{@link HttpClient} が接続を持つ）。作り置きしたものを返すこと
	 * @return	コントローラ
	 */
	public static Controller mount (String routePath, Function<WebContext, ReverseProxy> factory) {

		return new ReverseProxyController(routePath, factory);

	}

	// endregion

}
