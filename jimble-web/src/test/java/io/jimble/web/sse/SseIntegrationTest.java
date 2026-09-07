package io.jimble.web.sse;

import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE を実サーバーで確かめる（要件 F-W-21）
 *
 * <p>
 * <b>組み立てが正しいだけでは足りない。</b>
 * 「1件ずつ、書いたときに届く」ことが SSE の全部なので、
 * <b>到着の間隔まで見る。</b>まとめてバッファされていたら、
 * 形が合っていても使いものにならない。
 * </p>
 */
class SseIntegrationTest {

	/* ストリームが終わったことを待つ */
	static final CountDownLatch finished = new CountDownLatch(1);


	/**
	 * テスト用アプリケーション
	 */
	static final class SseApp extends JimbleApp {

		{

			// 3件を 200ms おきに送って閉じる
			get("/events", context -> {

				try (SseStream sse = context.response().sse()) {

					for (int i = 1; i <= 3; i++) {
						sse.send("tick", new Data().putData("n", i));
						sse.sleep(Duration.ofMillis(200));
					}

				}

			});

			// 種別なし・改行入り
			get("/plain", context -> {

				try (SseStream sse = context.response().sse()) {
					sse.send("1行目\n2行目");
				}

			});

			// キープアライブ
			get("/keepalive", context -> {

				try (SseStream sse = context.response().sse()) {
					sse.keepAlive();
					sse.send("ready", "ok");
				}

			});

			/*
			 * 相手が切っても分からないので、寿命で止まる。
			 * 2秒の上限を明示して、テストが待ちすぎないようにする
			 */
			get("/forever", context -> {

				try (SseStream sse = context.response().sse(Duration.ofSeconds(2), 0)) {

					while (sse.isOpen()) {
						sse.send("tick", "x");
						sse.sleep(Duration.ofMillis(50));
					}

				}

				finished.countDown();

			});

			// 件数の上限で止まる
			get("/capped", context -> {

				try (SseStream sse = context.response().sse(null, 3)) {

					while (sse.isOpen()) {
						sse.send("tick", "x");
					}

				}

			});

		}

	}

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new SseApp(), 0);
		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

	}

	@Test
	@DisplayName("ヘッダが SSE のものになる")
	void headers () throws Exception {

		HttpResponse<String> response = client.send(request("/keepalive")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertEquals(200, response.statusCode());

		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream")
			, response.headers().map().toString());

		// 途中で溜め込まれないように
		assertEquals("no-store", response.headers().firstValue("cache-control").orElse(null));
		assertEquals("no", response.headers().firstValue("x-accel-buffering").orElse(null));

	}

	@Test
	@DisplayName("1件ずつ、書いたときに届く")
	void arrivesOneByOne () throws Exception {

		HttpResponse<InputStream> response =
			client.send(request("/events"), HttpResponse.BodyHandlers.ofInputStream());

		List<Long> arrivals = new ArrayList<>();
		List<String> lines = new ArrayList<>();

		long start = System.nanoTime();

		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("data:")) {
					arrivals.add((System.nanoTime() - start) / 1_000_000);
					lines.add(line);
				}
			}

		}

		assertEquals(3, lines.size(), lines.toString());
		assertEquals("data: {\"n\":1}", lines.get(0));
		assertEquals("data: {\"n\":3}", lines.get(2));

		/*
		 * 200ms おきに書いている。
		 * まとめてバッファされていたら、3件がほぼ同時に届く。
		 * 最初と最後で 300ms 以上は開いているはず（200ms × 2 に余裕を持たせる）。
		 */
		long span = arrivals.get(2) - arrivals.get(0);

		assertTrue(span >= 300, "まとめて届いている（間隔 %dms）: %s".formatted(span, arrivals));

	}

	@Test
	@DisplayName("種別ごとに読める")
	void eventName () throws Exception {

		String body = client.send(request("/events")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		assertTrue(body.contains("event: tick\ndata: {\"n\":1}\n\n"), body);

	}

	@Test
	@DisplayName("本文の改行は行ごとに分かれて届く")
	void multiline () throws Exception {

		String body = client.send(request("/plain")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		assertTrue(body.endsWith("data: 1行目\ndata: 2行目\n\n"), body);

	}

	@Test
	@DisplayName("キープアライブはコメントとして流れる")
	void keepAlive () throws Exception {

		String body = client.send(request("/keepalive")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		// retry: が先に出るので、そのあとに来る
		assertTrue(body.contains(":\n\n"), body);

	}

	@Test
	@DisplayName("時間の上限で止まる")
	void maxDuration () throws Exception {

		/*
		 * 相手が生きていても、上限が来たら閉じる。
		 * SSE は繋ぎ直す前提の仕組みなので、これが正しい形である
		 * （retry: を最初に送ってあるので、クライアントは勝手に戻ってくる）。
		 */
		long start = System.nanoTime();

		String body = client.send(request("/forever")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		long elapsed = (System.nanoTime() - start) / 1_000_000;

		assertTrue(finished.await(5, TimeUnit.SECONDS), "終わっていない");
		assertTrue(elapsed >= 1500, "上限より早く切れている: " + elapsed + "ms");
		assertTrue(elapsed < 10000, "上限で切れていない: " + elapsed + "ms");
		assertTrue(body.contains("event: tick"), body);

	}

	@Test
	@DisplayName("件数の上限で止まる")
	void maxEvents () throws Exception {

		String body = client.send(request("/capped")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		// 3件で打ち切られる
		assertEquals(3, body.split("event: tick").length - 1, body);

	}

	@Test
	@DisplayName("繋ぎ直すまでの時間を最初に伝える")
	void retryHint () throws Exception {

		String body = client.send(request("/keepalive")
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

		/*
		 * 寿命で切ったあと、クライアントはこの時間のあとに勝手に戻ってくる。
		 * これが無いと、上限で切ったきり戻ってこない。
		 */
		assertTrue(body.startsWith("retry: "), body);

	}

	/**
	 * リクエスト
	 *
	 * @param path	パス
	 * @return	リクエスト
	 */
	private static HttpRequest request (String path) {

		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(20))
			.GET()
			.build();

	}

}
