package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;
import io.jimble.core.lifecycle.Shutdown;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 止め方（要件 D-91）
 *
 * <p>
 * <b>いきなり止めない。</b>ヘルスチェックを落とし、新規を断ち、
 * 処理中のものを待ってから止める。
 * </p>
 */
class GracefulShutdownTest {

	/* リクエストの中で待たせるための合図 */
	private final CountDownLatch hold = new CountDownLatch(1);

	/* リクエストが入ったことの合図 */
	private final CountDownLatch entered = new CountDownLatch(1);

	@BeforeEach
	void setUp () {

		Conf.reload();
		Shutdown.reset();

	}

	@AfterEach
	void tearDown () {

		hold.countDown();
		Shutdown.reset();
		Conf.reload();

	}

	/**
	 * 待たせるアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp app () {

		return new JimbleApp() {
			{
				get("/fast", context -> context.response().send("ok"));

				get("/slow", context -> {
					entered.countDown();
					hold.await(10, TimeUnit.SECONDS);
					context.response().send("slow");
				});

				get("/health_check", context ->
					context.response().send(Shutdown.isStopping() ? 503 : 200));
			}
		};

	}

	/**
	 * 投げる
	 *
	 * @param server	サーバー
	 * @param path		パス
	 * @return	ステータス
	 */
	private static int get (JimbleServer server, String path) throws Exception {

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + server.port() + path))
				.timeout(Duration.ofSeconds(10))
				.build()
			, HttpResponse.BodyHandlers.ofString());

		return response.statusCode();

	}

	@Test
	@DisplayName("D-91 止め始めるとヘルスチェックだけが落ちる")
	void healthCheckFallsFirst () throws Exception {

		JimbleServer server = JimbleServer.start(app(), 0);

		try {

			assertEquals(200, get(server, "/health_check"));

			Shutdown.markStopping();

			assertEquals(503, get(server, "/health_check"), "ヘルスチェックが落ちていない");

			// 普通のリクエストはまだ通る（ロードバランサが外すのを待つ間）
			assertEquals(200, get(server, "/fast"), "早すぎる。まだ受けるべき");

		} finally {

			Shutdown.reset();
			server.stop();

		}

	}

	@Test
	@DisplayName("D-91 断つと決めたら新しいリクエストは 503")
	void drainingRejects () throws Exception {

		JimbleServer server = JimbleServer.start(app(), 0);

		try {

			Shutdown.markDraining();

			assertEquals(JimbleServer.UNAVAILABLE_STATUS_CODE, get(server, "/fast"));

		} finally {

			Shutdown.reset();
			server.stop();

		}

	}

	@Test
	@DisplayName("D-91 処理中のリクエストが終わるまで待ってから止める")
	void waitsForInFlight () throws Exception {

		JimbleServer server = JimbleServer.start(app(), 0);

		AtomicReference<Integer> status = new AtomicReference<>();

		Thread caller = Thread.ofVirtual().start(() -> {
			try {
				status.set(get(server, "/slow"));
			} catch (Exception ignore) {
			}
		});

		assertTrue(entered.await(5, TimeUnit.SECONDS), "リクエストが入っていない");
		assertEquals(1, server.inFlight());

		// 1秒後に返させる
		Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignore) {
			}
			hold.countDown();
		});

		long start = System.currentTimeMillis();
		server.stop();
		long elapsed = System.currentTimeMillis() - start;

		caller.join();

		assertEquals(200, status.get(), "処理中のリクエストが切られている");
		assertTrue(elapsed >= 400, "待たずに止めた: " + elapsed + "ms");
		assertEquals(0, server.inFlight());

	}

	@Test
	@DisplayName("D-91 待ちきれなければ残ったまま止める（止まらないよりまし）")
	void givesUpAfterTimeout () throws Exception {

		Conf.replace(ConfigFactory
			.parseString(ServerConf.KEY_SHUTDOWN_TIMEOUT + " = 1s")
			.withFallback(Conf.conf().config()));

		JimbleServer server = JimbleServer.start(app(), 0);

		Thread.ofVirtual().start(() -> {
			try {
				get(server, "/slow");
			} catch (Exception ignore) {
			}
		});

		assertTrue(entered.await(5, TimeUnit.SECONDS));

		long start = System.currentTimeMillis();
		server.stop();
		long elapsed = System.currentTimeMillis() - start;

		assertTrue(elapsed >= 900, "待っていない: " + elapsed + "ms");
		assertTrue(elapsed < 5000, "上限を超えて待っている: " + elapsed + "ms");

		hold.countDown();

	}

	@Test
	@DisplayName("D-91 runAll() でも止め始めたことになる")
	void runAllMarksStopping () {

		assertFalse(Shutdown.isStopping());

		Shutdown.runAll();

		assertTrue(Shutdown.isStopping(), "runAll でヘルスチェックが落ちない");

	}

}
