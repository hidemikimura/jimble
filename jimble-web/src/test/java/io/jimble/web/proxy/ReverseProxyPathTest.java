package io.jimble.web.proxy;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 転送先へのパスを、デコードしたまま書かない（D-203）
 *
 * <p>
 * ワイルドカードの値はパーセントデコード済みで、かつてはそのまま要求行に書いていた。
 * </p>
 * <ul>
 *   <li>{@code %0d%0a} が改行になり、<b>転送先への2本目のリクエストを差し込めた</b>（2.2.0 から。
 *       HTTP/1.1 をソケットで直に話すようにしたため）</li>
 *   <li>{@code %2e%2e} が {@code ..} になり、<b>ベース URL のパスの外へ出られた</b></li>
 * </ul>
 *
 * <p>
 * <b>転送先は生のソケット</b>にして、届いたバイト列をそのまま見る（HTTP のサーバーにすると、
 * 解釈したあとの形しか見えない）。
 * </p>
 */
class ReverseProxyPathTest {

	private static ServerSocket upstream;
	private static JimbleServer server;

	/** 転送先に届いたもの（1接続ぶんのヘッダまで） */
	private static final List<String> received = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void startAll () throws Exception {

		upstream = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

		Thread.ofVirtual().start(() -> {
			while (!upstream.isClosed()) {
				try (Socket socket = upstream.accept()) {
					received.add(readHead(socket.getInputStream()));
					socket.getOutputStream().write(
						"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes(StandardCharsets.US_ASCII));
				} catch (Exception ignore) {
					// 閉じたら終わり
				}
			}
		});

		String base = "http://127.0.0.1:" + upstream.getLocalPort() + "/base";

		server = JimbleServer.start(new JimbleApp() {
			{
				install(() -> ReverseProxy.mount("/api", base));
			}
		}, 0);

	}

	@AfterAll
	static void stopAll () throws Exception {

		if (server != null) {
			server.stop();
		}

		if (upstream != null) {
			upstream.close();
		}

	}

	@BeforeEach
	void clear () {

		received.clear();

	}

	/**
	 * 空行まで読む。<b>そのあとに続くものも少し待って読む</b>（差し込まれた2本目を見るため）
	 */
	private static String readHead (InputStream in) throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];

		while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
			int read = in.read(buffer);
			if (read < 0) {
				break;
			}
			out.write(buffer, 0, read);
		}

		return out.toString(StandardCharsets.ISO_8859_1);

	}

	/**
	 * 生のリクエストを送って、状態コードを返す
	 */
	private static int send (String requestTarget) throws Exception {

		try (Socket socket = new Socket("127.0.0.1", server.port())) {
			socket.getOutputStream().write(("GET " + requestTarget + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
				.getBytes(StandardCharsets.US_ASCII));
			String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
			return Integer.parseInt(response.substring(9, 12));
		}

	}

	@Test
	@DisplayName("%0d%0a は改行にせず、エンコードしたまま送る（2本目のリクエストを差し込めない）")
	void noRequestInjection () throws Exception {

		int status = send("/api/x%0d%0aX-Injected:%20yes%0d%0a%0d%0aGET%20/admin%20HTTP/1.1%0d%0aHost:%20internal%0d%0a%0d%0a");

		assertEquals(200, status);
		assertEquals(1, received.size());

		String head = received.getFirst();
		String requestLine = head.substring(0, head.indexOf("\r\n"));

		assertTrue(requestLine.startsWith("GET /base/x%0D%0AX-Injected:%20yes%0D%0A"), requestLine);
		assertTrue(requestLine.endsWith(" HTTP/1.1"), requestLine);
		assertFalse(head.contains("\r\nX-Injected:"), "ヘッダを差し込めています:\n" + head);
		assertFalse(head.contains("GET /admin"), "2本目のリクエストを差し込めています:\n" + head);

	}

	@Test
	@DisplayName("%2e%2e（..）を含むパスは 400。ベース URL のパスの外へ出られない")
	void noEncodedTraversal () throws Exception {

		assertEquals(400, send("/api/%2e%2e/secret"));
		assertEquals(400, send("/api/a/%2E%2E/%2e%2e/secret"));
		assertEquals(400, send("/api/./x"));
		assertTrue(received.isEmpty(), "転送しています: " + received);

	}

	@Test
	@DisplayName("生の .. も転送しない")
	void noRawTraversal () throws Exception {

		int status = send("/api/../admin");

		// Helidon が畳んで /admin にするなら 404。どちらにしても転送先へは行かない
		assertTrue(status == 400 || status == 404, "status=" + status);
		assertTrue(received.isEmpty(), "転送しています: " + received);

	}

	@Test
	@DisplayName("ふつうのパスは、エンコードし直して届く（空白・日本語・クエリ）")
	void normalPath () throws Exception {

		assertEquals(200, send("/api/a%20b/%E3%81%82/c.txt?q=1&r=%20"));

		String head = received.getFirst();
		assertEquals("GET /base/a%20b/%E3%81%82/c.txt?q=1&r=%20 HTTP/1.1", head.substring(0, head.indexOf("\r\n")));

	}

	@Test
	@DisplayName("encodePath：セグメントごとにエンコードし、. と .. は断る")
	void encodePath () {

		assertEquals("/a/b", ReverseProxy.encodePath("a/b"));
		assertEquals("/a%20b/%0D%0A", ReverseProxy.encodePath("a b/\r\n"));
		assertEquals("/a%3Fb%23c", ReverseProxy.encodePath("a?b#c"));
		assertEquals("/~user/a:b@c", ReverseProxy.encodePath("~user/a:b@c"));
		assertNull(ReverseProxy.encodePath("a/../b"));
		assertNull(ReverseProxy.encodePath("."));

	}

}
