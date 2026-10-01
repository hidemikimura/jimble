package io.jimble.gradle.run;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 開発用のプロキシがアプリへ流すもの（要件 F-X-02）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * 転送に {@code java.net.http.HttpClient} を使っていたので、<b>{@code Host} がアプリに届かなかった</b>——
 * {@code Host} は JDK の「制限ヘッダ」で、{@code HttpClient} が接続先（{@code 127.0.0.1:アプリのポート}）から付け直す。
 * ホストで振り分けるアプリ・絶対 URL を組むアプリが、<b>開発のときだけ</b>食い違っていた。
 * </p>
 *
 * <p>
 * ブラウザ役は<b>ソケットで直に書く</b>（{@code HttpClient} では、そもそも好きな {@code Host} を送れない）。
 * アプリ役は、受け取った {@code Host}・パス・本文をそのまま返す。
 * </p>
 */
class DevProxyTest {

	/* アプリ役 */
	private HttpServer app;

	/* プロキシ */
	private DevProxy proxy;

	/* プロキシのポート */
	private int proxyPort;

	/* アプリのポート */
	private int appPort;

	@BeforeEach
	void start () throws IOException {

		appPort = freePort();
		proxyPort = freePort();

		app = HttpServer.create(new InetSocketAddress("127.0.0.1", appPort), 0);

		// 受け取ったものを返す
		app.createContext("/echo", exchange -> {
			byte[] body = exchange.getRequestBody().readAllBytes();
			String text = "host=" + exchange.getRequestHeaders().getFirst("Host")
				+ "\nmethod=" + exchange.getRequestMethod()
				+ "\ntarget=" + exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery()
				+ "\ncookie=" + exchange.getRequestHeaders().getFirst("Cookie")
				+ "\nbody=" + new String(body, StandardCharsets.UTF_8);
			byte[] out = text.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Set-Cookie", "a=1");
			exchange.getResponseHeaders().add("Set-Cookie", "b=2");
			exchange.sendResponseHeaders(200, out.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(out);
			}
		});

		// 長さを言わずに返す（chunked）
		app.createContext("/chunked", exchange -> {
			exchange.sendResponseHeaders(200, 0);
			try (OutputStream os = exchange.getResponseBody()) {
				for (int i = 0; i < 3; i++) {
					os.write(("part" + i + ";").getBytes(StandardCharsets.UTF_8));
					os.flush();
				}
			}
		});

		app.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().set("Location", "/somewhere");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});

		app.createContext("/empty", exchange -> {
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});

		app.start();

		proxy = new DevProxy("127.0.0.1", proxyPort, appPort, () -> BuildOutcome.OK, new RunLog());
		proxy.start();

	}

	@AfterEach
	void stop () {

		if (proxy != null) {
			proxy.stop();
		}

		if (app != null) {
			app.stop(0);
		}

	}

	// region Host

	@Test
	@DisplayName("F-X-02 ブラウザが送った Host をそのままアプリへ引き継ぐ")
	void hostIsForwarded () throws IOException {

		String response = send("""
			GET /echo?x=1 HTTP/1.1\r
			Host: tenant1.localhost:9000\r
			Connection: close\r
			\r
			""");

		assertTrue(response.startsWith("HTTP/1.1 200"), response);
		assertTrue(response.contains("host=tenant1.localhost:9000"), "Host が引き継がれていません:\n" + response);
		assertFalse(response.contains("host=127.0.0.1"), response);

	}

	@Test
	@DisplayName("F-X-02 Host の無いリクエスト（HTTP/1.0）には、アプリの待ち受け先を入れる")
	void missingHostGetsTheAppAddress () throws IOException {

		String response = send("""
			GET /echo HTTP/1.0\r
			\r
			""");

		assertTrue(response.contains("host=127.0.0.1:" + appPort), response);

	}

	// endregion

	// region これまでどおり

	@Test
	@DisplayName("F-X-02 パス・クエリ・Cookie はそのまま、応答の Set-Cookie は2つとも返す")
	void pathQueryAndCookies () throws IOException {

		String response = send("""
			GET /echo/a%2Fb?q=%E3%81%82&r=1 HTTP/1.1\r
			Host: localhost:9000\r
			Cookie: session=abc\r
			Connection: close\r
			\r
			""");

		assertTrue(response.contains("target=/echo/a%2Fb?q=%E3%81%82&r=1"), "パスやクエリを書き換えています:\n" + response);
		assertTrue(response.contains("cookie=session=abc"), response);
		// HttpServer はヘッダ名を Set-cookie の形に書き換えて返す（名前の大文字小文字は意味を持たない）
		String lower = response.toLowerCase();
		assertTrue(lower.contains("set-cookie: a=1"), response);
		assertTrue(lower.contains("set-cookie: b=2"), "同じ名前のヘッダの2つ目が落ちています:\n" + response);

	}

	@Test
	@DisplayName("F-X-02 Content-Length つきの本文を流す")
	void bodyWithLength () throws IOException {

		String response = send("""
			POST /echo HTTP/1.1\r
			Host: localhost:9000\r
			Content-Type: text/plain\r
			Content-Length: 11\r
			Connection: close\r
			\r
			hello world""");

		assertTrue(response.contains("method=POST"), response);
		assertTrue(response.contains("body=hello world"), response);

	}

	@Test
	@DisplayName("F-X-02 chunked の本文も流す")
	void chunkedRequestBody () throws IOException {

		String response = send("""
			POST /echo HTTP/1.1\r
			Host: localhost:9000\r
			Transfer-Encoding: chunked\r
			Connection: close\r
			\r
			5\r
			hello\r
			6\r
			 world\r
			0\r
			\r
			""");

		assertTrue(response.contains("body=hello world"), response);

	}

	@Test
	@DisplayName("F-X-02 本文の無い POST も通す（長さ 0 として送る）")
	void postWithoutBody () throws IOException {

		String response = send("""
			POST /echo HTTP/1.1\r
			Host: localhost:9000\r
			Content-Length: 0\r
			Connection: close\r
			\r
			""");

		assertTrue(response.startsWith("HTTP/1.1 200"), response);
		assertTrue(decodedBody(response).endsWith("body="), response);

	}

	@Test
	@DisplayName("F-X-02 アプリが長さを言わずに返した本文（chunked）も、全部届く")
	void chunkedResponse () throws IOException {

		String response = send("""
			GET /chunked HTTP/1.1\r
			Host: localhost:9000\r
			Connection: close\r
			\r
			""");

		assertTrue(decodedBody(response).contains("part0;part1;part2;"), response);

	}

	@Test
	@DisplayName("F-X-02 リダイレクトは追わずにブラウザへ返す")
	void redirectIsNotFollowed () throws IOException {

		String response = send("""
			GET /redirect HTTP/1.1\r
			Host: localhost:9000\r
			Connection: close\r
			\r
			""");

		assertTrue(response.startsWith("HTTP/1.1 302"), response);
		assertTrue(response.contains("Location: /somewhere"), response);

	}

	@Test
	@DisplayName("F-X-02 204 と HEAD は本文なしで返す")
	void bodyless () throws IOException {

		assertTrue(send("""
			GET /empty HTTP/1.1\r
			Host: localhost:9000\r
			Connection: close\r
			\r
			""").startsWith("HTTP/1.1 204"));

		String head = send("""
			HEAD /echo HTTP/1.1\r
			Host: localhost:9000\r
			Connection: close\r
			\r
			""");

		assertTrue(head.startsWith("HTTP/1.1 200"), head);
		assertEquals("", head.substring(head.indexOf("\r\n\r\n") + 4), "HEAD に本文を付けています");

	}

	@Test
	@DisplayName("F-X-02 アプリが止まっていれば 502 の画面")
	void appDownIs502 () throws IOException {

		app.stop(0);
		app = null;

		String response = send("""
			GET /echo HTTP/1.1\r
			Host: localhost:9000\r
			Connection: close\r
			\r
			""");

		assertTrue(response.startsWith("HTTP/1.1 502"), response);

	}

	// endregion

	// region 道具

	/**
	 * プロキシへ生のリクエストを送り、切れるまで読む
	 *
	 * @param request	リクエスト
	 * @return	応答（ヘッダと本文）
	 */
	private String send (String request) throws IOException {

		try (Socket socket = new Socket("127.0.0.1", proxyPort)) {

			socket.setSoTimeout(10_000);
			socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
			socket.getOutputStream().flush();

			InputStream in = socket.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);

			return out.toString(StandardCharsets.UTF_8);

		}

	}

	/**
	 * chunked の本文を解く
	 *
	 * @param response	応答
	 * @return	本文
	 */
	private static String decodedBody (String response) throws IOException {

		int start = response.indexOf("\r\n\r\n") + 4;
		byte[] raw = response.substring(start).getBytes(StandardCharsets.UTF_8);

		if (!response.substring(0, start).toLowerCase().contains("transfer-encoding: chunked")) {
			return new String(raw, StandardCharsets.UTF_8);
		}

		return new String(new AppConnection.ChunkedInputStream(new java.io.ByteArrayInputStream(raw)).readAllBytes()
			, StandardCharsets.UTF_8);

	}

	/**
	 * 空いているポート
	 *
	 * @return	ポート
	 */
	private static int freePort () throws IOException {

		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}

	}

	// endregion

}
