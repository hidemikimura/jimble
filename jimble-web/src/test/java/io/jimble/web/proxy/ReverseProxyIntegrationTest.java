package io.jimble.web.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リバースプロキシの結合テスト（要件 F-R-14 / F-R-21）
 *
 * <p>
 * <b>転送先のサーバーを実際に立てて</b>、jimble 経由で叩く。
 * 移送元の穴（ステータスコードを返していない、hop-by-hop を素通し、
 * X-Forwarded-Proto がプロトコル版、X-Forwarded-For の上書き、タイムアウト無し）は
 * 実際に通信しないと出てこないため。
 * </p>
 */
class ReverseProxyIntegrationTest {

	/* 転送先 */
	private static HttpServer upstream;

	/* jimble */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	/* 誰も待ち受けていないポート */
	private static int deadPort;

	// region 起動と停止

	@BeforeAll
	static void startAll () throws Exception {

		upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		upstream.createContext("/", ReverseProxyIntegrationTest::handleUpstream);
		upstream.start();

		String base = "http://127.0.0.1:" + upstream.getAddress().getPort();

		try (ServerSocket socket = new ServerSocket(0)) {
			deadPort = socket.getLocalPort();
		}

		String dead = "http://127.0.0.1:" + deadPort;

		server = JimbleServer.start(new JimbleApp() {
			{
				get("/plain", context -> context.response().send("jimble"));
				install(() -> ReverseProxy.mount("/api", base));
				install(() -> ReverseProxy.mount("/dead", dead));
			}
		}, 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}

	@AfterAll
	static void stopAll () {

		if (server != null) {
			server.stop();
		}

		if (upstream != null) {
			upstream.stop(0);
		}

	}

	// endregion

	// region 転送先のサーバー

	/**
	 * 転送先の処理
	 *
	 * @param exchange	やり取り
	 */
	private static void handleUpstream (HttpExchange exchange) {

		try (exchange) {

			String path = exchange.getRequestURI().getPath();

			if (path.startsWith("/status/")) {
				sendStatus(exchange, Integer.parseInt(path.substring("/status/".length())));
				return;
			}

			if (path.startsWith("/redirect")) {
				exchange.getResponseHeaders().add("Location", "/moved");
				exchange.sendResponseHeaders(302, -1);
				return;
			}

			if (path.startsWith("/slow")) {
				Thread.sleep(3000);
			}

			echo(exchange);

		} catch (Exception ex) {

			throw new IllegalStateException(ex);

		}

	}

	/**
	 * ステータスだけ返す
	 *
	 * @param exchange		やり取り
	 * @param statusCode	ステータスコード
	 * @throws Exception	送信に失敗した場合
	 */
	private static void sendStatus (HttpExchange exchange, int statusCode) throws Exception {

		if (statusCode == 204 || statusCode == 304) {
			exchange.sendResponseHeaders(statusCode, -1);
			return;
		}

		byte[] body = ("status-" + statusCode).getBytes(StandardCharsets.UTF_8);

		exchange.sendResponseHeaders(statusCode, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}

	}

	/**
	 * 受け取ったものをそのまま返す
	 *
	 * @param exchange	やり取り
	 * @throws Exception	送信に失敗した場合
	 */
	private static void echo (HttpExchange exchange) throws Exception {

		StringBuilder text = new StringBuilder();

		text.append("method=").append(exchange.getRequestMethod()).append("\n");
		text.append("path=").append(exchange.getRequestURI().getPath()).append("\n");
		text.append("query=").append(String.valueOf(exchange.getRequestURI().getQuery())).append("\n");

		for (String name : List.of(
			"X-Forwarded-Proto", "X-Forwarded-Host", "X-Forwarded-For", "X-Real-IP"
			, "Proxy-Authorization", "X-Trace-Id", "Content-Type")) {

			text.append(name.toLowerCase()).append("=")
				.append(String.valueOf(exchange.getRequestHeaders().getFirst(name))).append("\n");

		}

		text.append("body=")
			.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).append("\n");

		byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);

		// 転送先が付けたヘッダはクライアントまで届くこと
		exchange.getResponseHeaders().add("X-Upstream", "yes");
		exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
		// hop-by-hop はクライアントへ返してはいけない
		exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic");

		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}

	}

	// endregion

	// region クライアント

	/**
	 * jimble に送る
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param body		本文（無ければ null）
	 * @param headers	ヘッダ（名前, 値, 名前, 値 …）
	 * @return	応答
	 * @throws Exception	送信に失敗した場合
	 */
	private static HttpResponse<String> request (String method, String path, String body, String... headers)
		throws Exception {

		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(20))
			.method(method, body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body));

		for (int i = 0; i < headers.length; i += 2) {
			builder.header(headers[i], headers[i + 1]);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

	}

	/**
	 * 転送先が受け取った内容を読む
	 *
	 * @param body	応答の本文
	 * @return	キーと値
	 */
	private static Map<String, String> parse (String body) {

		Map<String, String> result = new LinkedHashMap<>();

		for (String line : body.split("\n")) {
			int equal = line.indexOf('=');
			if (equal > 0) {
				result.put(line.substring(0, equal), line.substring(equal + 1));
			}
		}

		return result;

	}

	// endregion

	// region 転送

	@Test
	@DisplayName("転送先の応答をそのまま返す")
	void forwards () throws Exception {

		HttpResponse<String> response = request("GET", "/api/echo", null);

		assertEquals(200, response.statusCode());
		assertEquals("GET", parse(response.body()).get("method"));
		assertEquals("/echo", parse(response.body()).get("path"));

	}

	@Test
	@DisplayName("転送先のステータスコードを返す")
	void forwardsStatusCode () throws Exception {

		/*
		 * 移送元はヘッダと本文しか写しておらず、
		 * 転送先が 404 でもクライアントには 200 が返っていた。
		 */
		for (int statusCode : List.of(201, 400, 404, 418, 500, 503)) {

			HttpResponse<String> response = request("GET", "/api/status/" + statusCode, null);

			assertEquals(statusCode, response.statusCode(), "status " + statusCode);
			assertEquals("status-" + statusCode, response.body(), "status " + statusCode);

		}

	}

	@Test
	@DisplayName("本文を持てないステータスも返せる")
	void forwardsBodylessStatus () throws Exception {

		assertEquals(204, request("GET", "/api/status/204", null).statusCode());

	}

	@Test
	@DisplayName("クエリ文字列を渡す")
	void forwardsQuery () throws Exception {

		Map<String, String> upstreamSaw = parse(request("GET", "/api/echo?a=1&b=%E3%81%82", null).body());

		assertEquals("a=1&b=あ", upstreamSaw.get("query"));

	}

	@Test
	@DisplayName("入れ子のパスを渡す")
	void forwardsNestedPath () throws Exception {

		assertEquals("/a/b/c", parse(request("GET", "/api/a/b/c", null).body()).get("path"));

	}

	@Test
	@DisplayName("本文を渡す")
	void forwardsBody () throws Exception {

		Map<String, String> upstreamSaw = parse(
			request("POST", "/api/echo", "hello=world", "Content-Type", "application/x-www-form-urlencoded").body());

		assertEquals("POST", upstreamSaw.get("method"));
		assertEquals("hello=world", upstreamSaw.get("body"));
		assertEquals("application/x-www-form-urlencoded", upstreamSaw.get("content-type"));

	}

	@Test
	@DisplayName("全メソッドが1回の登録で通る")
	void forwardsAllMethods () throws Exception {

		// 移送元は同じ登録を 14 回手書きしていた（要件 F-R-21）
		for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {

			HttpResponse<String> response = request(method, "/api/echo", "PUT".equals(method) ? "x" : null);

			assertEquals(200, response.statusCode(), method);
			assertEquals(method, parse(response.body()).get("method"), method);

		}

	}

	@Test
	@DisplayName("転送先のヘッダを返す")
	void forwardsResponseHeaders () throws Exception {

		HttpResponse<String> response = request("GET", "/api/echo", null);

		assertEquals("yes", response.headers().firstValue("X-Upstream").orElse(null));

	}

	@Test
	@DisplayName("転送先のリダイレクトは追わずにそのまま返す")
	void doesNotFollowRedirect () throws Exception {

		HttpResponse<String> response = request("GET", "/api/redirect", null);

		assertEquals(302, response.statusCode());
		assertEquals("/moved", response.headers().firstValue("Location").orElse(null));

	}

	@Test
	@DisplayName("プロキシに関係ないルートは素通し")
	void otherRoutesUntouched () throws Exception {

		assertEquals("jimble", request("GET", "/plain", null).body());

	}

	// endregion

	// region hop-by-hop

	@Test
	@DisplayName("hop-by-hop なリクエストヘッダは転送先へ渡さない")
	void dropsHopByHopRequestHeaders () throws Exception {

		Map<String, String> upstreamSaw = parse(request("GET", "/api/echo", null
			, "Proxy-Authorization", "Basic dGVzdA=="
			, "X-Trace-Id", "abc-123").body());

		assertEquals("null", upstreamSaw.get("proxy-authorization"), "hop-by-hop が転送されている");
		// 関係ないヘッダはちゃんと渡ること
		assertEquals("abc-123", upstreamSaw.get("x-trace-id"));

	}

	@Test
	@DisplayName("hop-by-hop な応答ヘッダはクライアントへ返さない")
	void dropsHopByHopResponseHeaders () throws Exception {

		HttpResponse<String> response = request("GET", "/api/echo", null);

		assertTrue(response.headers().firstValue("Proxy-Authenticate").isEmpty()
			, "hop-by-hop がクライアントへ返っている");

	}

	// endregion

	// region X-Forwarded-*

	@Test
	@DisplayName("X-Forwarded-Proto は scheme（プロトコル版ではない）")
	void forwardedProtoIsScheme () throws Exception {

		// 移送元は getProtocol()（= HTTP/1.1）を入れていた
		Map<String, String> upstreamSaw = parse(request("GET", "/api/echo", null).body());

		assertEquals("http", upstreamSaw.get("x-forwarded-proto"));

	}

	@Test
	@DisplayName("X-Forwarded-Host と X-Real-IP を渡す")
	void forwardedHostAndRealIp () throws Exception {

		Map<String, String> upstreamSaw = parse(request("GET", "/api/echo", null).body());

		assertEquals("127.0.0.1:" + server.port(), upstreamSaw.get("x-forwarded-host"));
		assertNotNull(upstreamSaw.get("x-real-ip"));
		assertFalse("null".equals(upstreamSaw.get("x-real-ip")));

	}

	@Test
	@DisplayName("X-Forwarded-For は上書きせずに後ろへ足す")
	void forwardedForAppends () throws Exception {

		// 移送元は上書きしていたので、多段になると経路が消えていた
		Map<String, String> upstreamSaw = parse(
			request("GET", "/api/echo", null, "X-Forwarded-For", "203.0.113.9").body());

		String forwardedFor = upstreamSaw.get("x-forwarded-for");

		assertTrue(forwardedFor.startsWith("203.0.113.9, "), forwardedFor);
		assertEquals(2, forwardedFor.split(",").length, forwardedFor);

	}

	@Test
	@DisplayName("X-Forwarded-For が無ければ自分のアドレスだけ")
	void forwardedForSingle () throws Exception {

		String forwardedFor = parse(request("GET", "/api/echo", null).body()).get("x-forwarded-for");

		assertNotNull(forwardedFor);
		assertFalse(forwardedFor.contains(","), forwardedFor);

	}

	// endregion

	// region 転送先に届かないとき

	@Test
	@DisplayName("転送先に繋がらなければ 502")
	void badGateway () throws Exception {

		// 移送元は例外がそのまま出て 500 になっていた
		HttpResponse<String> response = request("GET", "/dead/echo", null);

		assertEquals(ReverseProxy.BAD_GATEWAY, response.statusCode());

	}

	@Test
	@DisplayName("応答が遅ければ待ち続けずに 502")
	void timeout () throws Exception {

		// 移送元はタイムアウトが無く、転送先が黙るとスレッドが張り付いた
		ReverseProxy proxy = new ReverseProxy(
			"http://127.0.0.1:" + upstream.getAddress().getPort()
			, Duration.ofSeconds(2)
			, Duration.ofMillis(300));

		JimbleServer slowServer = JimbleServer.start(new JimbleApp() {
			{
				install(() -> ReverseProxy.mount("/slow", context -> proxy));
			}
		}, 0);

		try {

			long start = System.nanoTime();

			HttpResponse<String> response = client.send(HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + slowServer.port() + "/slow/slow"))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());

			long elapsedMs = (System.nanoTime() - start) / 1_000_000;

			assertEquals(ReverseProxy.BAD_GATEWAY, response.statusCode());
			assertTrue(elapsedMs < 2500, "待ち続けている: " + elapsedMs + "ms");

		} finally {

			slowServer.stop();

		}

	}

	// endregion

	// region ベース URL

	@Test
	@DisplayName("ベース URL の末尾の「/」は落とす")
	void trimsTrailingSlash () throws Exception {

		JimbleServer other = JimbleServer.start(new JimbleApp() {
			{
				install(() -> ReverseProxy.mount("/api"
					, "http://127.0.0.1:" + upstream.getAddress().getPort() + "/"));
			}
		}, 0);

		try {

			HttpResponse<String> response = client.send(HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + other.port() + "/api/echo"))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());

			assertEquals("/echo", parse(response.body()).get("path"), "「//echo」になっている");

		} finally {

			other.stop();

		}

	}

	// endregion

}
