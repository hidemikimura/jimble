package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.helidon.common.socket.SocketOptions;
import io.helidon.webserver.WebServerConfig;
import io.jimble.util.conf.Conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受け付けた接続の TCP_NODELAY（server.tcp_no_delay。AQSELL-98）
 *
 * <p>
 * <b>付けないと、分けて書く応答（chunked）が同じ接続の 2 回目から 1 回ごとに約 40ms 遅れる。</b>
 * 続く小さな書き込みが、前に送った分の ACK を待ち（Nagle）、それが相手の遅延 ACK と重なるためである。
 * aqSell では HTML のページの中央値が 44〜51ms → 3〜9ms になった。
 * </p>
 *
 * <p>
 * <b>時間で見るテストは Linux でしか遅れを再現しない</b>（CI は ubuntu）。
 * macOS の loopback では、TCP_NODELAY を外しても遅れが出ない。
 * そのため helidon に渡った値でも確かめている。
 * </p>
 */
class TcpNoDelayTest {

	/** 遅れていないとみなす上限（遅れると約 40ms） */
	private static final long NOT_DELAYED_MILLIS = 30;

	/** 同じ接続で続けて取る回数 */
	private static final int REQUESTS = 3;

	/** 2 回に分けて書くアプリ */
	static final class ChunkedApp extends JimbleApp {

		{
			get("/chunked", context -> {
				OutputStream out = context.response().outputStream();
				out.write("<html>".getBytes(StandardCharsets.UTF_8));
				out.flush();
				out.write("</html>".getBytes(StandardCharsets.UTF_8));
				out.flush();
			});
		}

	}

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	/** 何も渡さないときに helidon がサーバーの接続に使う値 */
	private static SocketOptions helidonDefault () {

		return WebServerConfig.builder().buildPrototype().connectionOptions();

	}

	@Test
	@DisplayName("書かなければ true、false と書けば false")
	void readsTheSetting () {

		conf("");
		assertTrue(ServerConf.tcpNoDelay());

		conf("server { tcp_no_delay = false }");
		assertFalse(ServerConf.tcpNoDelay());

	}

	@Test
	@DisplayName("helidon の既定に、TCP_NODELAY だけを替えたものを渡す")
	void onlyTcpNoDelayDiffersFromHelidon () {

		conf("");

		JimbleServer server = JimbleServer.start(new ChunkedApp(), 0);

		try {

			SocketOptions options = server.effectiveConfig().connectionOptions();

			assertTrue(options.tcpNoDelay(), "tcp_no_delay を渡していません");
			assertFalse(helidonDefault().tcpNoDelay(), "helidon の既定が変わりました。既定を true にする理由を見直してください");
			/*
			 * <b>TCP_NODELAY のほかは helidon の既定のまま。</b>
			 * SocketOptions.builder() から作り直すと、keep-alive やタイムアウトが食い違いうる
			 */
			assertEquals(SocketOptions.builder().from(helidonDefault()).tcpNoDelay(true).build(), options);

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("false と書けば、helidon の既定とまったく同じ")
	void falseIsHelidonsDefault () {

		conf("server { tcp_no_delay = false }");

		JimbleServer server = JimbleServer.start(new ChunkedApp(), 0);

		try {
			assertEquals(helidonDefault(), server.effectiveConfig().connectionOptions());
		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("分けて書く応答を同じ接続で続けて取っても、2 回目から遅れない")
	void chunkedResponsesAreNotDelayed () throws Exception {

		conf("");

		JimbleServer server = JimbleServer.start(new ChunkedApp(), 0);

		try (Socket socket = new Socket("localhost", server.port())) {

			List<Long> millis = new ArrayList<>();
			for (int i = 0; i < REQUESTS; i++) {
				long start = System.nanoTime();
				assertEquals("<html></html>", getChunked(socket));
				millis.add((System.nanoTime() - start) / 1_000_000);
			}

			// 1 回目は接続の立ち上がりを含むので、2 回目から見る
			for (int i = 1; i < REQUESTS; i++) {
				assertTrue(millis.get(i) < NOT_DELAYED_MILLIS, "%d 回目が遅れています（ms）: %s".formatted(i + 1, millis));
			}

		} finally {
			server.stop();
		}

	}

	/**
	 * 同じ接続で GET /chunked を送り、chunked の本文を最後まで読む
	 */
	private static String getChunked (Socket socket) throws Exception {

		OutputStream out = socket.getOutputStream();
		out.write("GET /chunked HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
		out.flush();

		InputStream in = socket.getInputStream();
		String head = readLine(in);
		assertTrue(head.startsWith("HTTP/1.1 200"), head);
		boolean chunked = false;
		for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
			chunked |= line.equalsIgnoreCase("transfer-encoding: chunked");
		}
		assertTrue(chunked, "chunked で返っていません");

		ByteArrayOutputStream body = new ByteArrayOutputStream();
		for (int size = Integer.parseInt(readLine(in).strip(), 16); size > 0; size = Integer.parseInt(readLine(in).strip(), 16)) {
			body.write(in.readNBytes(size));
			readLine(in);
		}
		readLine(in);
		return body.toString(StandardCharsets.UTF_8);

	}

	private static String readLine (InputStream in) throws Exception {

		StringBuilder sb = new StringBuilder();
		for (int c = in.read(); c != '\n'; c = in.read()) {
			if (c < 0) {
				throw new IllegalStateException("接続が切れました: " + sb);
			}
			if (c != '\r') {
				sb.append((char) c);
			}
		}
		return sb.toString();

	}

}
