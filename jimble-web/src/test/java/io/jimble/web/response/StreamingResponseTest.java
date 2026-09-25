package io.jimble.web.response;

import io.jimble.util.log.Log;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流して返す（{@code send(InputStream)} / {@code stream(...)} / {@code outputStream()}）（D-181）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code send(InputStream)} は、流し込んだ中身を最後まで溜めてから送っていた。</b>
 * 中で {@code transferTo} するだけで一度も flush しないので、helidon の出力は
 * バッファが一杯になるか閉じるまで出ていかない。ファイルなら困らないが、
 * <b>届いたそばから返したい中身</b>（別のサーバーのストリーミング応答をそのまま中継する、
 * 時間のかかる処理の途中経過を出す）は、最後の1バイトが来るまで相手に何も届かない。
 * </p>
 *
 * <p>
 * <b>{@code outputStream()} は、それまでに決めたことを送らずに書き始めていた。</b>
 * {@code code(201)} の状態コード・{@code cookies()} に積んだ Cookie・既定の Cache-Control（no-store）が
 * どれも付かず、<b>状態コードはいつも 200</b> だった。{@code sse()} はここを通るので、
 * SSE の前に積んだ Cookie（セッションの延長など）も届いていなかった。
 * </p>
 *
 * <p>
 * どちらも helidon の中の振る舞いなので、実際に起動して叩く。
 * </p>
 */
class StreamingResponseTest {

	/** 最初のかたまりを送ってから次を送るまでの間 */
	private static final long GAP_MILLIS = 1500;

	/** 試すためのアプリ */
	static final class App extends JimbleApp {

		{
			// 届いたそばから返す（中継の形）
			get("/send/slow", c -> c.response().send(slow(), "text/plain"));
			get("/send/slow-bare", c -> c.response().send(slow()));
			get("/stream/slow", c -> c.response().stream(slow(), "text/plain"));

			// ファイルのように手元にそろっているものは、これまでどおり全部届く
			get("/send/big", c -> c.response().send(new ByteArrayInputStream(BIG), "application/octet-stream", BIG.length));

			// 状態コード・Cookie・既定の Cache-Control
			get("/out/created", c -> {
				c.cookies().put("mark", "1");
				c.response().code(201).setResponseHeader("Content-Type", "text/plain");
				try (OutputStream out = c.response().outputStream()) {
					out.write("made".getBytes(StandardCharsets.UTF_8));
				}
			});

			get("/out/cache", c -> {
				c.response().setResponseHeader("Cache-Control", "max-age=60");
				try (OutputStream out = c.response().outputStream()) {
					out.write("x".getBytes(StandardCharsets.UTF_8));
				}
			});

			// 本文を持てない状態コードで書こうとしたら、捨てて WARN（D-179 と同じ扱い）
			get("/out/no-content", c -> {
				c.response().code(204);
				try (OutputStream out = c.response().outputStream()) {
					out.write("lost".getBytes(StandardCharsets.UTF_8));
				}
			});

			get("/sse/cookie", c -> {
				c.cookies().put("mark", "2");
				try (var sse = c.response().sse(Duration.ofSeconds(5), 1)) {
					sse.send("tick", new io.jimble.util.data.Data().putData("n", 1));
				}
			});
		}

	}

	/* 大きな中身 */
	private static final byte[] BIG = new byte[3 * 1024 * 1024];

	static {
		for (int i = 0; i < BIG.length; i++) {
			BIG[i] = (byte) (i * 31);
		}
	}

	/**
	 * 1行目を送り、間を空けて2行目を送る入力
	 *
	 * @return	入力
	 * @throws IOException	例外
	 */
	private static InputStream slow () throws IOException {

		PipedInputStream in = new PipedInputStream(64 * 1024);
		PipedOutputStream out = new PipedOutputStream(in);

		Thread.ofVirtual().start(() -> {
			try (out) {
				out.write("first\n".getBytes(StandardCharsets.UTF_8));
				out.flush();
				Thread.sleep(GAP_MILLIS);
				out.write("second\n".getBytes(StandardCharsets.UTF_8));
			} catch (Exception ignore) {
				// 相手が切っただけ
			}
		});

		return in;

	}

	private static JimbleServer server;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	/* 記録した警告 */
	private final List<String> warns = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void start () {

		server = JimbleServer.start(new App(), 0);

	}

	@AfterAll
	static void stop () {

		server.stop();

	}

	@BeforeEach
	void captureLog () {

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				warns.add(message);
			}
		});

	}

	@AfterEach
	void restoreLog () {

		Log.resetSink();

	}

	private static HttpRequest request (String path) {

		return HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
			.timeout(Duration.ofSeconds(10))
			.build();

	}

	/**
	 * 1行目が届くまでの時間を測る
	 *
	 * @param path	パス
	 */
	private static void assertFirstLineArrivesBeforeTheEnd (String path) throws Exception {

		long started = System.nanoTime();
		HttpResponse<InputStream> response = CLIENT.send(request(path), HttpResponse.BodyHandlers.ofInputStream());

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

			String first = reader.readLine();
			long firstMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

			assertEquals("first", first);
			assertTrue(firstMillis < GAP_MILLIS - 300
				, path + ": 1行目が " + firstMillis + "ms 後に届きました（最後まで溜めてから送っています）");

			assertEquals("second", reader.readLine());

		}

		assertEquals(200, response.statusCode());

	}

	@Test
	@DisplayName("send(InputStream, 型) は届いたそばから返す")
	void sendStreamsAsItArrives () throws Exception {

		assertFirstLineArrivesBeforeTheEnd("/send/slow");

	}

	@Test
	@DisplayName("send(InputStream) も届いたそばから返す")
	void sendBareStreamsAsItArrives () throws Exception {

		assertFirstLineArrivesBeforeTheEnd("/send/slow-bare");

	}

	@Test
	@DisplayName("stream(...) も届いたそばから返す")
	void streamStreamsAsItArrives () throws Exception {

		assertFirstLineArrivesBeforeTheEnd("/stream/slow");

	}

	@Test
	@DisplayName("手元にそろっている大きな中身は、欠けずに全部届く")
	void bigBodyArrivesWhole () throws Exception {

		HttpResponse<byte[]> response = CLIENT.send(request("/send/big"), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(200, response.statusCode());
		assertEquals(String.valueOf(BIG.length), response.headers().firstValue("content-length").orElse(""));
		assertArrayEquals(BIG, response.body(), "中身が " + response.body().length + " バイト届きました");

	}

	@Test
	@DisplayName("outputStream() は code() の状態コード・Cookie・既定の Cache-Control を付けて書き始める")
	void outputStreamAppliesStatusAndHeaders () throws Exception {

		HttpResponse<String> response = CLIENT.send(request("/out/created"), HttpResponse.BodyHandlers.ofString());

		assertEquals(201, response.statusCode(), "code(201) が反映されていません");
		assertEquals("made", response.body());
		assertTrue(response.headers().allValues("set-cookie").stream().anyMatch(v -> v.startsWith("mark=1"))
			, "Cookie が届いていません: " + response.headers().map());
		assertEquals("no-store", response.headers().firstValue("cache-control").orElse(""));

	}

	@Test
	@DisplayName("outputStream() でも、自分で決めた Cache-Control は上書きしない")
	void outputStreamKeepsOwnCacheControl () throws Exception {

		HttpResponse<String> response = CLIENT.send(request("/out/cache"), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertEquals("max-age=60", response.headers().firstValue("cache-control").orElse(""));

	}

	@Test
	@DisplayName("本文を持てない状態コードで outputStream() に書いたら、捨てて WARN を出す（500 にしない）")
	void outputStreamWithNoContent () throws Exception {

		HttpResponse<String> response = CLIENT.send(request("/out/no-content"), HttpResponse.BodyHandlers.ofString());

		assertEquals(204, response.statusCode());
		assertEquals("", response.body());
		// 応答はヘッダを送った時点で返るので、書いて捨てる WARN はそのあとに出る。少し待つ
		long deadline = System.currentTimeMillis() + 3000;
		while (warns.stream().noneMatch(w -> w.contains("204")) && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertTrue(warns.stream().anyMatch(w -> w.contains("204")), "捨てたことが WARN に出ていません: " + warns);
		assertTrue(warns.stream().noneMatch(w -> w.contains("lost")), "捨てた中身をログに出しています: " + warns);

	}

	@Test
	@DisplayName("sse() の前に積んだ Cookie も届く")
	void sseSendsCookies () throws Exception {

		HttpResponse<String> response = CLIENT.send(request("/sse/cookie"), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("event: tick"), response.body());
		assertTrue(response.headers().allValues("set-cookie").stream().anyMatch(v -> v.startsWith("mark=2"))
			, "SSE で Cookie が届いていません: " + response.headers().map());

	}

}
