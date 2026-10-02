package io.jimble.web.proxy;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圧縮された要求の本文と、Connection が指名したヘッダ（D-243 / D-244）
 *
 * <ul>
 *   <li>gzip の本文は Helidon が受けたところで解く。かつては圧縮したままの Content-Length を本文の長さにして 502 になり、
 *       Content-Encoding も付けたままだった</li>
 *   <li>Connection が指名したヘッダは、その区間だけのもの。かつては転送先へ渡していた</li>
 * </ul>
 */
class ReverseProxyRequestBodyTest {

	private static ServerSocket upstream;
	private static JimbleServer server;
	private static final CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void start () throws Exception {

		upstream = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

		Thread.ofVirtual().start(() -> {
			while (!upstream.isClosed()) {
				try (Socket socket = upstream.accept()) {
					socket.setSoTimeout(1500);
					InputStream in = socket.getInputStream();
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buffer = new byte[8192];
					try {
						int read;
						while ((read = in.read(buffer)) > 0) {
							out.write(buffer, 0, read);
							if (out.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n0\r\n\r\n")) {
								break;
							}
						}
					} catch (SocketTimeoutException ignore) {
						// 届いたところまで
					}
					received.add(out.toString(StandardCharsets.ISO_8859_1));
					socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes(StandardCharsets.US_ASCII));
				} catch (Exception ignore) {
					// 閉じたら終わり
				}
			}
		});

		server = JimbleServer.start(new JimbleApp() {
			{
				install(() -> ReverseProxy.mount("/api", "http://127.0.0.1:" + upstream.getLocalPort()));
			}
		}, 0);

	}

	@AfterAll
	static void stop () throws Exception {

		server.stop();
		upstream.close();

	}

	@Test
	@DisplayName("gzip の本文は、解いた平文として Content-Encoding を付けずに届く。Connection が指名したヘッダは届かない")
	void gzipBodyAndNominatedHeaders () throws Exception {

		received.clear();

		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
			out.write("hello-plain-body".getBytes(StandardCharsets.US_ASCII));
		}
		byte[] body = gz.toByteArray();

		String status;

		try (Socket client = new Socket("127.0.0.1", server.port())) {
			client.getOutputStream().write(("POST /api/x HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: text/plain\r\n"
				+ "Content-Encoding: gzip\r\nContent-Length: " + body.length + "\r\n"
				+ "Connection: close, X-Hop-Only\r\nX-Hop-Only: secret\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			client.getOutputStream().write(body);
			status = new String(client.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1).substring(9, 12);
		}

		assertEquals("200", status, "圧縮された本文で転送に失敗しています");

		String request = received.getFirst().toLowerCase();

		assertFalse(request.contains("content-encoding:"), "解いた本文に Content-Encoding を付けています:\n" + request);
		assertFalse(request.contains("x-hop-only:"), "Connection が指名したヘッダを転送しています:\n" + request);
		assertTrue(request.contains("hello-plain-body"), request);

	}

}
