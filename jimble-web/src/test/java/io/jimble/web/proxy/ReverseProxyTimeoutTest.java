package io.jimble.web.proxy;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 転送先が少しずつしか返さない・受け取らないときの待ちの上限（D-258）
 *
 * <p>
 * かつての {@code request_timeout} は1回の読み込みごとの待ちだけだったので、
 * 上限より短い間隔で1バイトずつ返されると、いつまでも終わらなかった。
 * </p>
 */
class ReverseProxyTimeoutTest {

	/* 1回の読み込みの上限より短い間隔 */
	private static final long TRICKLE_MILLIS = 300;

	private final List<AutoCloseable> closing = new ArrayList<>();

	@AfterEach
	void tearDown () throws Exception {

		for (AutoCloseable c : closing) {
			c.close();
		}

	}

	/**
	 * 1回だけ受ける転送先（受けたら handler に渡す）
	 */
	private int upstream (SocketHandler handler) throws IOException {

		ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		closing.add(server);

		Thread.ofVirtual().start(() -> {
			try (Socket socket = server.accept()) {
				handler.handle(socket);
			} catch (Exception ignore) {
				// 切られるのを確かめるので、書けなくなってよい
			}
		});

		return server.getLocalPort();

	}

	interface SocketHandler {
		void handle (Socket socket) throws Exception;
	}

	private JimbleServer proxy (ReverseProxy reverseProxy) {

		JimbleServer server = JimbleServer.start(new JimbleApp() {
			{
				install(() -> ReverseProxy.mount("/p", reverseProxy));
			}
		}, 0);

		closing.add(server::stop);

		return server;

	}

	/** リクエストの頭（空行まで）を読み捨てる */
	private static void readHead (InputStream in) throws IOException {

		int matched = 0;
		byte[] end = { '\r', '\n', '\r', '\n' };

		for (int c; matched < 4 && (c = in.read()) >= 0; ) {
			matched = c == end[matched] ? matched + 1 : (c == '\r' ? 1 : 0);
		}

	}

	private static HttpResponse<String> call (JimbleServer server, HttpRequest.Builder builder) throws Exception {

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			return client.send(builder.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
		}

	}

	private static URI uri (JimbleServer server) {

		return URI.create("http://127.0.0.1:" + server.port() + "/p/x");

	}

	@Test
	@DisplayName("ヘッダを少しずつしか返さない転送先は、request_timeout で切って 502")
	void trickledHeadersAreCut () throws Exception {

		int port = upstream(socket -> {
			readHead(socket.getInputStream());
			OutputStream out = socket.getOutputStream();
			out.write("HTTP/1.1 200 OK\r\nX-Slow: ".getBytes(StandardCharsets.ISO_8859_1));
			out.flush();
			// 1回の読み込みの上限（1s）より短い間隔で、いつまでも1バイトずつ
			for (int i = 0; i < 200; i++) {
				Thread.sleep(TRICKLE_MILLIS);
				out.write('a');
				out.flush();
			}
		});

		JimbleServer server = proxy(new ReverseProxy("http://127.0.0.1:" + port, Duration.ofSeconds(5), Duration.ofSeconds(1)));

		long started = System.nanoTime();
		HttpResponse<String> response = call(server, HttpRequest.newBuilder(uri(server)).GET());
		long millis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(502, response.statusCode());
		assertTrue(millis < 5000, "切るまでに時間がかかりすぎた: " + millis + "ms");

	}

	@Test
	@DisplayName("本文は、既定では全体で切らない（ダウンロードや SSE を通す）")
	void bodyIsNotCutByDefault () throws Exception {

		int port = upstream(socket -> {
			readHead(socket.getInputStream());
			OutputStream out = socket.getOutputStream();
			out.write("HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
			out.flush();
			// 合わせて 2.4 秒。1回の読み込みの上限（1s）は超えない
			for (int i = 0; i < 8; i++) {
				Thread.sleep(TRICKLE_MILLIS);
				out.write('b');
				out.flush();
			}
		});

		JimbleServer server = proxy(new ReverseProxy("http://127.0.0.1:" + port, Duration.ofSeconds(5), Duration.ofSeconds(1)));

		HttpResponse<String> response = call(server, HttpRequest.newBuilder(uri(server)).GET());

		assertEquals(200, response.statusCode());
		assertEquals("bbbbbbbb", response.body());

	}

	@Test
	@DisplayName("bodyTimeout を決めると、本文が終わらない転送先を切る")
	void bodyTimeoutCuts () throws Exception {

		int port = upstream(socket -> {
			readHead(socket.getInputStream());
			OutputStream out = socket.getOutputStream();
			out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
			out.flush();
			for (int i = 0; i < 1000; i++) {
				Thread.sleep(TRICKLE_MILLIS);
				out.write('c');
				out.flush();
			}
		});

		JimbleServer server = proxy(new ReverseProxy("http://127.0.0.1:" + port, Duration.ofSeconds(5), Duration.ofSeconds(1))
			.bodyTimeout(Duration.ofSeconds(2)));

		long started = System.nanoTime();

		try {
			HttpResponse<String> response = call(server, HttpRequest.newBuilder(uri(server)).GET());
			assertTrue(response.body().length() < 1000, "本文を最後まで返した");
		} catch (IOException expected) {
			// 送り始めたあとに切れるので、クライアントには途中で切れた応答として見える
		}

		long millis = (System.nanoTime() - started) / 1_000_000;
		assertTrue(millis < 8000, "切るまでに時間がかかりすぎた: " + millis + "ms");

	}

	@Test
	@DisplayName("本文を受け取らない転送先は、送るのが request_timeout のあいだ進まなければ切って 502")
	void stalledUploadIsCut () throws Exception {

		int port = upstream(socket -> {
			readHead(socket.getInputStream());
			// 本文を読まないまま待つ
			Thread.sleep(20_000);
		});

		JimbleServer server = proxy(new ReverseProxy("http://127.0.0.1:" + port, Duration.ofSeconds(5), Duration.ofSeconds(1)));

		byte[] body = new byte[8 * 1024 * 1024];

		long started = System.nanoTime();
		HttpResponse<String> response = call(server, HttpRequest.newBuilder(uri(server))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body)));
		long millis = (System.nanoTime() - started) / 1_000_000;

		assertEquals(502, response.statusCode());
		assertTrue(millis < 10000, "切るまでに時間がかかりすぎた: " + millis + "ms");

	}

}
