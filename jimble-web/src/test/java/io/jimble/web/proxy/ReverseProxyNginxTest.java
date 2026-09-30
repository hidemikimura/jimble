package io.jimble.web.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nginx の proxy_set_header / proxy_hide_header / proxy_redirect / proxy_cookie_* に当たるもの（要件 F-R-14）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * {@code ReverseProxy} は JDK の {@code HttpClient} で転送していたので、<b>{@code Host} を決められなかった</b>
 * （制限ヘッダ）。転送先が Host で振り分けるアプリ（ドメインでショップを決める、など）を後ろに置けない。
 * いまは HTTP/1.1 をソケットで直に話し、nginx と同じことができる。
 * </p>
 *
 * <p>
 * あわせて、<b>黙って壊れていた2つ</b>を固定する——(1) 転送先が gzip で返すと、{@code Content-Encoding} を落としたうえで
 * もう一度 gzip をかけ、ブラウザに解けない本文を返していた。(2) 転送先の {@code Set-Cookie} が2つ以上あると、最後の1つしか届かなかった。
 * </p>
 */
class ReverseProxyNginxTest {

	/* 転送先 */
	private static HttpServer upstream;

	/* jimble */
	private static JimbleServer server;

	/* 転送先の URL */
	private static String base;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startAll () throws Exception {

		upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		upstream.createContext("/", ReverseProxyNginxTest::handleUpstream);
		upstream.start();

		base = "http://127.0.0.1:" + upstream.getAddress().getPort();

		server = JimbleServer.start(new JimbleApp() {
			{
				// 何も設定しない（既定）
				install(() -> ReverseProxy.mount("/plain", base));

				// nginx の設定を盛り込んだもの
				install(() -> ReverseProxy.mount("/shop", new ReverseProxy(base)
					.preserveHost()
					.setHeader("X-App", "front")
					.setHeader("X-Path", context -> context.request().path())
					.setHeader("X-Empty", context -> null)
					.removeHeader("X-Secret")
					.hideResponseHeader("X-Powered-By")
					.redirect("http://shop.internal/", "/shop/")
					.cookieDomain("backend.internal", "example.com")
					.cookiePath("/", "/shop/")));

				// Location の書き換えを切ったもの
				install(() -> ReverseProxy.mount("/raw", new ReverseProxy(base).noRedirectRewrite()));

				// Host を固定の値にするもの
				install(() -> ReverseProxy.mount("/fixed", new ReverseProxy(base).setHeader("Host", "tenant1.example.com")));
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

	// region 転送先

	private static void handleUpstream (HttpExchange exchange) {

		try (exchange) {

			String path = exchange.getRequestURI().getPath();

			switch (path) {

				case "/abs-redirect" -> {
					exchange.getResponseHeaders().add("Location", base + "/moved?x=1");
					exchange.sendResponseHeaders(302, -1);
				}

				case "/internal-redirect" -> {
					exchange.getResponseHeaders().add("Location", "http://shop.internal/cart");
					exchange.sendResponseHeaders(302, -1);
				}

				case "/refresh" -> {
					exchange.getResponseHeaders().add("Refresh", "0; url=" + base + "/next");
					exchange.sendResponseHeaders(204, -1);
				}

				case "/cookies" -> {
					exchange.getResponseHeaders().add("Set-Cookie", "a=1; Domain=backend.internal; Path=/; HttpOnly");
					exchange.getResponseHeaders().add("Set-Cookie", "b=2; Path=/cart");
					exchange.getResponseHeaders().add("X-Powered-By", "upstream");
					exchange.sendResponseHeaders(204, -1);
				}

				case "/gzip" -> {
					byte[] body = "hello-world-hello-world".getBytes(StandardCharsets.UTF_8);
					String accept = exchange.getRequestHeaders().getFirst("Accept-Encoding");
					if (accept != null && accept.contains("gzip")) {
						ByteArrayOutputStream gz = new ByteArrayOutputStream();
						try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
							out.write(body);
						}
						body = gz.toByteArray();
						exchange.getResponseHeaders().add("Content-Encoding", "gzip");
					}
					exchange.sendResponseHeaders(200, body.length);
					exchange.getResponseBody().write(body);
				}

				default -> echo(exchange);

			}

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	private static void echo (HttpExchange exchange) throws Exception {

		StringBuilder text = new StringBuilder();

		text.append("path=").append(exchange.getRequestURI().getPath()).append("\n");

		for (String name : List.of("Host", "X-App", "X-Path", "X-Empty", "X-Secret", "X-Forwarded-Host", "X-Forwarded-Port")) {
			text.append(name.toLowerCase()).append("=").append(String.valueOf(exchange.getRequestHeaders().getFirst(name))).append("\n");
		}

		List<String> cookies = exchange.getRequestHeaders().get("Cookie");
		text.append("cookie-count=").append(cookies == null ? 0 : cookies.size()).append("\n");
		text.append("cookie-first=").append(cookies == null ? "" : cookies.getFirst()).append("\n");

		byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);

		exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
		exchange.sendResponseHeaders(200, body.length);

		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}

	}

	// endregion

	// region Host とヘッダ（proxy_set_header / proxy_hide_header）

	@Test
	@DisplayName("既定の Host は転送先のもの（元の Host は X-Forwarded-Host と X-Forwarded-Port で渡す）")
	void defaultHostIsTheUpstream () throws Exception {

		Map<String, String> echo = parse(get("/plain/echo").body());

		assertEquals("127.0.0.1:" + upstream.getAddress().getPort(), echo.get("host"));
		assertEquals("127.0.0.1:" + server.port(), echo.get("x-forwarded-host"));
		assertEquals(String.valueOf(server.port()), echo.get("x-forwarded-port"));

	}

	@Test
	@DisplayName("preserveHost() で元の Host をそのまま渡す（proxy_set_header Host $http_host）")
	void preserveHost () throws Exception {

		assertEquals("127.0.0.1:" + server.port(), parse(get("/shop/echo").body()).get("host"));

	}

	@Test
	@DisplayName("setHeader(\"Host\", ...) で Host を決められる")
	void fixedHost () throws Exception {

		assertEquals("tenant1.example.com", parse(get("/fixed/echo").body()).get("host"));

	}

	@Test
	@DisplayName("setHeader で足す（固定の値・リクエストから作る値）。null なら送らない。removeHeader は送らない")
	void setAndRemoveHeaders () throws Exception {

		HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url("/shop/echo")))
			.header("X-Secret", "do-not-forward")
			.header("X-Empty", "from-client")
			.GET().build(), HttpResponse.BodyHandlers.ofString());

		Map<String, String> echo = parse(response.body());

		assertEquals("front", echo.get("x-app"));
		assertEquals("/shop/echo", echo.get("x-path"));
		assertEquals("null", echo.get("x-empty"), "null を返したヘッダを送っています（クライアントの値も消える）");
		assertEquals("null", echo.get("x-secret"), "removeHeader したヘッダを送っています");

	}

	@Test
	@DisplayName("hideResponseHeader で返さない（proxy_hide_header）")
	void hideResponseHeader () throws Exception {

		assertTrue(get("/shop/cookies").headers().firstValue("X-Powered-By").isEmpty());
		assertEquals("upstream", get("/plain/cookies").headers().firstValue("X-Powered-By").orElse(null));

	}

	@Test
	@DisplayName("2行の Cookie は2行のまま渡す（\", \" で繋いで1行にしない）")
	void cookieLinesStayApart () throws Exception {

		/*
		 * ソケットで直に書く。JDK の HttpClient は、同じ名前の Cookie を送る前に1行にまとめてしまう
		 * （それではこの試験にならない）
		 */
		String response;

		try (java.net.Socket socket = new java.net.Socket("127.0.0.1", server.port())) {
			socket.getOutputStream().write(("GET /plain/echo HTTP/1.1\r\nHost: 127.0.0.1\r\n"
				+ "Cookie: a=1\r\nCookie: b=2\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}

		Map<String, String> echo = parse(response.substring(response.indexOf("\r\n\r\n") + 4));

		assertEquals("2", echo.get("cookie-count"), "2行の Cookie を1行に繋いで送っています: " + echo);
		assertEquals("a=1", echo.get("cookie-first"), echo.toString());

	}

	// endregion

	// region Location / Set-Cookie（proxy_redirect / proxy_cookie_*）

	@Test
	@DisplayName("既定で、転送先の URL で始まる Location をブラウザから見えるパスに書き換える（proxy_redirect default）")
	void defaultRedirectRewrite () throws Exception {

		assertEquals("/plain/moved?x=1", get("/plain/abs-redirect").headers().firstValue("Location").orElse(null));

	}

	@Test
	@DisplayName("noRedirectRewrite() ならそのまま返す（proxy_redirect off）")
	void noRedirectRewrite () throws Exception {

		assertEquals(base + "/moved?x=1", get("/raw/abs-redirect").headers().firstValue("Location").orElse(null));

	}

	@Test
	@DisplayName("redirect(元, 先) で足した規則で書き換える")
	void customRedirect () throws Exception {

		assertEquals("/shop/cart", get("/shop/internal-redirect").headers().firstValue("Location").orElse(null));

	}

	@Test
	@DisplayName("Refresh の url も書き換える")
	void refreshRewrite () throws Exception {

		assertEquals("0; url=/plain/next", get("/plain/refresh").headers().firstValue("Refresh").orElse(null));

	}

	@Test
	@DisplayName("Set-Cookie は2つとも届く。cookieDomain / cookiePath で書き換える（proxy_cookie_domain / proxy_cookie_path）")
	void setCookies () throws Exception {

		List<String> plain = get("/plain/cookies").headers().allValues("Set-Cookie");
		assertEquals(2, plain.size(), "Set-Cookie が1つしか届いていません（上書きしている）: " + plain);

		List<String> shop = get("/shop/cookies").headers().allValues("Set-Cookie");
		assertEquals(List.of("a=1; Domain=example.com; Path=/shop/; HttpOnly", "b=2; Path=/shop/cart"), shop);

	}

	// endregion

	// region 圧縮

	@Test
	@DisplayName("転送先が gzip で返した本文を、もう一度 gzip しない（Content-Encoding をそのまま渡す）")
	void gzipIsNotDoubled () throws Exception {

		HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url("/plain/gzip")))
			.header("Accept-Encoding", "gzip")
			.GET().build(), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(List.of("gzip"), response.headers().allValues("Content-Encoding"));

		String decoded;
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
			decoded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertEquals("hello-world-hello-world", decoded, "1回解いても元に戻りません（二重に圧縮している）");

	}

	// endregion

	// region 組み立て

	@Test
	@DisplayName("使い始めたあとは設定を変えられない。http / https 以外の転送先は断る")
	void configurationIsFixedOnceUsed () {

		ReverseProxy proxy = new ReverseProxy(base);
		proxy.markUsedForTest();

		assertThrows(IllegalStateException.class, proxy::preserveHost);
		assertThrows(IllegalStateException.class, () -> proxy.setHeader("X", "y"));
		assertThrows(IllegalArgumentException.class, () -> new ReverseProxy("ftp://example.com"));
		assertThrows(IllegalArgumentException.class, () -> new ReverseProxy(base).setHeader("Bad Name", "x"));

	}

	// endregion

	// region 道具

	private static String url (String path) {

		return "http://127.0.0.1:" + server.port() + path;

	}

	private static HttpResponse<String> get (String path) throws Exception {

		return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().timeout(Duration.ofSeconds(10)).build()
			, HttpResponse.BodyHandlers.ofString());

	}

	private static Map<String, String> parse (String body) {

		Map<String, String> map = new LinkedHashMap<>();

		for (String line : body.split("\n")) {
			int eq = line.indexOf('=');
			if (eq > 0) {
				map.put(line.substring(0, eq), line.substring(eq + 1));
			}
		}

		return map;

	}

	// endregion

}
