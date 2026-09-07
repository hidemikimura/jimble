package io.jimble.gradle.run;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 開発用のプロキシ（要件 F-X-02）
 *
 * <p>
 * ブラウザが叩くポートで受け、<b>必要なら先に作り直して入れ替えてから</b>
 * アプリへ転送する。リクエストが来るまで何もしないので、
 * <b>連続して保存しても再起動は1回</b>で済み、それでいて
 * 「リロードしたときには必ず最新」になる。
 * </p>
 *
 * <p>
 * <b>移送元は生の TCP を素通ししていた。</b>HTTP を解さないので、
 * 再起動時に Keep-Alive の接続を切るために連番を進める仕掛けが要り、
 * <b>接続ごとに 100ms ポーリングのスレッドを2本</b>立てて後始末していた。
 * ここは HTTP のレベルで扱う。接続の管理は {@link HttpServer} と
 * {@link HttpClient} に任せる。
 * </p>
 */
final class DevProxy {

	/** 本文を送らないメソッド */
	private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "DELETE", "OPTIONS", "TRACE");

	/** 作り直しに時間がかかるので、応答待ちは長めに取る */
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

	/* 受け口 */
	private final HttpServer server;

	/* 転送に使うもの */
	private final HttpClient client;

	/* 受け口のスレッド */
	private final ExecutorService executor;

	/* アプリの待ち受け先 */
	private final String appBaseUrl;

	/**
	 * コンストラクタ
	 *
	 * @param port			受けるポート
	 * @param appPort		アプリのポート
	 * @param beforeRequest	転送の前に呼ぶもの。作り直しが要ればここで行い、結果を返す
	 * @param log			ログ
	 * @throws IOException	受け口を作れなかった場合
	 */
	DevProxy (int port, int appPort, Supplier<BuildOutcome> beforeRequest, RunLog log) throws IOException {

		this.appBaseUrl = "http://127.0.0.1:" + appPort;

		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			// アプリが返すリダイレクトはブラウザに返す。勝手に追わない
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

		this.executor = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "jimble-run-proxy");
			thread.setDaemon(true);
			return thread;
		});

		this.server = HttpServer.create(new InetSocketAddress(port), 0);
		this.server.setExecutor(executor);
		this.server.createContext("/", exchange -> handle(exchange, beforeRequest, log));

	}

	/**
	 * 始める
	 */
	void start () {

		server.start();

	}

	/**
	 * 止める
	 */
	void stop () {

		server.stop(0);
		executor.shutdownNow();

	}

	/**
	 * 1件受ける
	 *
	 * @param exchange		やりとり
	 * @param beforeRequest	転送の前に呼ぶもの
	 * @param log			ログ
	 */
	private void handle (HttpExchange exchange, Supplier<BuildOutcome> beforeRequest, RunLog log) {

		try (exchange) {

			BuildOutcome outcome = beforeRequest.get();

			if (!outcome.success()) {
				sendHtml(exchange, 503, ErrorPage.html("作り直しに失敗しました", outcome.text()));
				return;
			}

			forward(exchange);

		} catch (IOException ex) {

			log.error("転送に失敗しました: " + ex.getMessage());

			try {
				sendHtml(exchange, 502, ErrorPage.html(
					"アプリに繋がりません"
					, "%s へ転送できませんでした。%n%n%s".formatted(appBaseUrl, ex)));
			} catch (IOException ignore) {
				// もう送っているので何もできない
			}

		} catch (InterruptedException ex) {

			Thread.currentThread().interrupt();

		} catch (RuntimeException | Error ex) {

			/*
			 * ここで投げると HttpServer が黙って接続を切る。
			 * ブラウザ側には「接続が切れた」としか出ず、原因を探しようがない。
			 */
			log.error("転送の途中で落ちました: " + ex);

			try {
				sendHtml(exchange, 500, ErrorPage.html("jimbleRun の中で例外が出ました", String.valueOf(ex)));
			} catch (IOException ignore) {
				// もう送っているので何もできない
			}

		}

	}

	/**
	 * アプリへ流す
	 *
	 * @param exchange	やりとり
	 * @throws IOException			転送に失敗した場合
	 * @throws InterruptedException	待っている間に止められた場合
	 */
	private void forward (HttpExchange exchange) throws IOException, InterruptedException {

		String method = exchange.getRequestMethod();

		HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri(exchange))
			.timeout(REQUEST_TIMEOUT)
			.method(method, bodyPublisher(exchange, method));

		for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())) {
				continue;
			}

			for (String value : entry.getValue()) {
				try {
					builder.header(entry.getKey(), value);
				} catch (IllegalArgumentException ignore) {
					// JDK が拒否するヘッダ。転送しないだけでよい
				}
			}

		}

		HttpResponse<InputStream> response =
			client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

		sendResponse(exchange, response);

	}

	/**
	 * 応答を返す
	 *
	 * @param exchange	やりとり
	 * @param response	アプリの応答
	 * @throws IOException	送信に失敗した場合
	 */
	private static void sendResponse (HttpExchange exchange, HttpResponse<InputStream> response) throws IOException {

		Headers headers = exchange.getResponseHeaders();

		for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())) {
				continue;
			}

			headers.put(entry.getKey(), entry.getValue());

		}

		int code = response.statusCode();

		/*
		 * 本文を持てないステータスに 0 を渡すと HttpServer が
		 * 長さ不明の本文を送ろうとして食い違う。-1 は「本文なし」である。
		 */
		if (isBodyless(code) || "HEAD".equals(exchange.getRequestMethod())) {
			response.body().close();
			exchange.sendResponseHeaders(code, -1);
			return;
		}

		// 0 = 長さは分からない（chunked で流す）
		exchange.sendResponseHeaders(code, 0);

		try (InputStream body = response.body();
			 OutputStream out = exchange.getResponseBody()) {
			body.transferTo(out);
		}

	}

	/**
	 * 本文
	 *
	 * @param exchange	やりとり
	 * @param method	メソッド
	 * @return	本文
	 */
	private static HttpRequest.BodyPublisher bodyPublisher (HttpExchange exchange, String method) {

		if (BODYLESS_METHODS.contains(method)) {
			return HttpRequest.BodyPublishers.noBody();
		}

		// 読み切らずに流す
		return HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody);

	}

	/**
	 * 転送先
	 *
	 * @param exchange	やりとり
	 * @return	転送先
	 */
	private URI targetUri (HttpExchange exchange) {

		URI uri = exchange.getRequestURI();

		String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
		String query = uri.getRawQuery();

		return URI.create(appBaseUrl + path + (query == null || query.isEmpty() ? "" : "?" + query));

	}

	/**
	 * 本文を持てないステータスか
	 *
	 * @param code	ステータスコード
	 * @return	持てない場合 = true
	 */
	private static boolean isBodyless (int code) {

		return code < 200 || code == 204 || code == 304;

	}

	/**
	 * HTML を返す
	 *
	 * @param exchange	やりとり
	 * @param code		ステータスコード
	 * @param html		HTML
	 * @throws IOException	送信に失敗した場合
	 */
	private static void sendHtml (HttpExchange exchange, int code, String html) throws IOException {

		byte[] body = html.getBytes(StandardCharsets.UTF_8);

		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(code, body.length);

		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}

	}

}
