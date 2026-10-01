package io.jimble.gradle.run;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * ここは HTTP のレベルで扱う。受け口の接続は {@link HttpServer} に任せる。
 * </p>
 *
 * <p>
 * <b>アプリへは {@link AppConnection} で送る</b>（{@code java.net.http.HttpClient} ではない）。
 * {@code HttpClient} は {@code Host} を付け直すので、<b>ブラウザが叩いたホストがアプリに届かなかった</b>。
 * いまは {@code Host} をそのまま引き継ぐ。
 * </p>
 */
final class DevProxy {

	/** 本文を送らないメソッド */
	private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "DELETE", "OPTIONS", "TRACE");

	/** 作り直しに時間がかかるので、応答待ちは長めに取る */
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

	/** 繋ぐまでの待ち */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** アプリの待ち受け先のホスト */
	private static final String APP_HOST = "127.0.0.1";

	/* 受け口 */
	private final HttpServer server;

	/* アプリのポート */
	private final int appPort;

	/* 受け口のスレッド */
	private final ExecutorService executor;

	/* アプリの待ち受け先 */
	private final String appBaseUrl;

	/**
	 * コンストラクタ
	 *
	 * @param host			待ち受けるアドレス（既定 127.0.0.1。D-228）
	 * @param port			受けるポート
	 * @param appPort		アプリのポート
	 * @param beforeRequest	転送の前に呼ぶもの。作り直しが要ればここで行い、結果を返す
	 * @param log			ログ
	 * @throws IOException	受け口を作れなかった場合
	 */
	DevProxy (String host, int port, int appPort, Supplier<BuildOutcome> beforeRequest, RunLog log) throws IOException {

		this.appPort = appPort;
		this.appBaseUrl = "http://" + APP_HOST + ":" + appPort;

		this.executor = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "jimble-run-proxy");
			thread.setDaemon(true);
			return thread;
		});

		// 既定ではこのマシンからしか開けない（かつてはすべての NIC で待ち受けていた。D-228）
		this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
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

		/*
		 * <b>閉じるのは catch のあと。</b>try (exchange) { ... } catch にすると、
		 * catch に入る前に exchange が閉じられ、<b>502 / 500 の画面が1度も届かなかった</b>
		 * （アプリが止まっていると、ブラウザには空の応答だけが返っていた）。
		 */
		try {
			handleOpen(exchange, beforeRequest, log);
		} finally {
			exchange.close();
		}

	}

	/**
	 * 1件受ける（exchange はまだ開いている）
	 *
	 * @param exchange		やりとり
	 * @param beforeRequest	転送の前に呼ぶもの
	 * @param log			ログ
	 */
	private void handleOpen (HttpExchange exchange, Supplier<BuildOutcome> beforeRequest, RunLog log) {

		try {

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
		Headers requestHeaders = exchange.getRequestHeaders();

		Map<String, List<String>> headers = new LinkedHashMap<>();

		for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {

			if (HopByHopHeaders.isForwardable(entry.getKey())) {
				headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
			}

		}

		/*
		 * <b>Host はブラウザが送ってきたものを引き継ぐ</b>（HopByHopHeaders は Host を落とさない）。
		 * 無いのは HTTP/1.0 くらいで、そのときだけアプリの待ち受け先を入れる（HTTP/1.1 では必須）。
		 */
		if (requestHeaders.getFirst("Host") == null) {
			headers.put("Host", List.of(APP_HOST + ":" + appPort));
		}

		InputStream body = null;
		long contentLength = -1;

		if (!BODYLESS_METHODS.contains(method)) {

			body = exchange.getRequestBody();

			String length = requestHeaders.getFirst("Content-Length");
			String transferEncoding = requestHeaders.getFirst("Transfer-Encoding");

			if (length != null) {
				contentLength = Long.parseLong(length.trim());
			} else if (transferEncoding == null) {
				// 長さも chunked も無い = 本文なし。POST に長さが無いと断るアプリもあるので 0 と書く
				contentLength = 0;
			}

		}

		try (AppConnection response = AppConnection.send(APP_HOST, appPort, CONNECT_TIMEOUT, REQUEST_TIMEOUT
				, method, target(exchange), headers, body, contentLength)) {
			sendResponse(exchange, response);
		}

	}

	/**
	 * 応答を返す
	 *
	 * @param exchange	やりとり
	 * @param response	アプリの応答
	 * @throws IOException	送信に失敗した場合
	 */
	private static void sendResponse (HttpExchange exchange, AppConnection response) throws IOException {

		Headers headers = exchange.getResponseHeaders();

		for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())) {
				continue;
			}

			headers.put(entry.getKey(), entry.getValue());

		}

		int code = response.status();

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
	 * 転送先（パスとクエリ。受け取ったバイトのまま）
	 *
	 * @param exchange	やりとり
	 * @return	転送先
	 */
	private static String target (HttpExchange exchange) {

		URI uri = exchange.getRequestURI();

		String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
		String query = uri.getRawQuery();

		return path + (query == null || query.isEmpty() ? "" : "?" + query);

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
