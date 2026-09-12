package io.jimble.web.server;

import java.time.Duration;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP サーバーの設定（要件 F-H-01〜F-H-03）
 */
class ServerConfTest {

	@AfterEach
	void reset () {

		System.clearProperty(ServerConf.PROPERTY_PORT);
		Conf.reload();

	}

	/**
	 * その場で設定を差し替える
	 *
	 * @param values	設定
	 */
	private void use (Map<String, Object> values) {

		Conf.replace(ConfigFactory.parseMap(values));

	}

	@Test
	@DisplayName("既定値が入っている")
	void defaults () {

		use(Map.of());

		assertEquals(ServerConf.DEFAULT_PORT, ServerConf.port());
		assertEquals(ServerConf.DEFAULT_MAX_REQUEST_SIZE, ServerConf.maxRequestSize());
		assertEquals(ServerConf.DEFAULT_MAX_HEADER_SIZE, ServerConf.maxHeaderSize());
		assertEquals(ServerConf.DEFAULT_IDLE_TIMEOUT, ServerConf.idleTimeout());
		assertTrue(ServerConf.compression());
		assertTrue(ServerConf.botAccessLog());
		assertFalse(ServerConf.trustProxy(), "既定でプロキシを信じてはいけない");
		assertEquals("", ServerConf.host(), "既定では全部のアドレスで待つ");

	}

	@Test
	@DisplayName("設定で変えられる")
	void fromConf () {

		use(Map.of(
			"server.port", 8080
			, "server.max_request_size", "1024B"
			, "server.max_header_size", "4096B"
			, "server.idle_timeout", "5s"
			, "server.compression", false
			, "server.trust_proxy", true
			, "server.host", "127.0.0.1"
		));

		assertEquals(8080, ServerConf.port());
		assertEquals(1024, ServerConf.maxRequestSize());
		assertEquals(4096, ServerConf.maxHeaderSize());
		assertEquals(Duration.ofSeconds(5), ServerConf.idleTimeout());
		assertFalse(ServerConf.compression());
		assertTrue(ServerConf.trustProxy());
		assertEquals("127.0.0.1", ServerConf.host());

	}

	@Test
	@DisplayName("ポートはシステムプロパティが勝つ")
	void portFromProperty () {

		use(Map.of("server.port", 8080));

		System.setProperty(ServerConf.PROPERTY_PORT, "9999");

		assertEquals(9999, ServerConf.port());

	}

	// region プロキシ（要件 F-H-02）

	/**
	 * プロキシヘッダつきのリクエストを作る
	 *
	 * @return	コンテキスト
	 */
	private WebContext proxied () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/x")
			.header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
			.header("X-Real-IP", "203.0.113.9");

		return new WebContext(source, new Fakes.FakeResponseSink());

	}

	@Test
	@DisplayName("既定ではプロキシヘッダを信じない")
	void doesNotTrustProxyByDefault () {

		/*
		 * X-Forwarded-For はクライアントが名乗るだけの値である。
		 * 信じると、IP でのアクセス制限やレートリミットが効かなくなる。
		 */
		use(Map.of());

		try (WebContext context = proxied()) {
			assertEquals("127.0.0.1", context.request().proxyAddress());
		}

	}

	@Test
	@DisplayName("信じる設定なら X-Forwarded-For の先頭を返す")
	void trustsProxyWhenConfigured () {

		use(Map.of("server.trust_proxy", true));

		try (WebContext context = proxied()) {
			assertEquals("203.0.113.9", context.request().proxyAddress());
		}

	}

	@Test
	@DisplayName("プロキシヘッダが無ければ接続元を返す")
	void fallsBackToRemoteAddress () {

		use(Map.of("server.trust_proxy", true));

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/x"), new Fakes.FakeResponseSink())) {

			assertEquals("127.0.0.1", context.request().proxyAddress());

		}

	}

	// endregion

	// region 実際に効いているか

	@Test
	@DisplayName("本文の上限を超えたリクエストは弾かれる（要件 F-H-01 / NF-S-05）")
	void maxRequestSizeIsEnforced () throws Exception {

		use(Map.of("server.max_request_size", "100B", "upload.max_total_size", "100B"));

		JimbleServer server = JimbleServer.start(new JimbleApp() {
			{
				post("/echo", context -> context.response().send("ok"));
			}
		}, 0);

		try {

			HttpClient client = HttpClient.newHttpClient();

			// 上限内は通る
			HttpResponse<String> small = client.send(HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + server.port() + "/echo"))
				.POST(HttpRequest.BodyPublishers.ofString("x".repeat(50)))
				.build(), HttpResponse.BodyHandlers.ofString());

			assertEquals(200, small.statusCode(), small.body());

			// 超えると弾かれる
			HttpResponse<String> large = client.send(HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + server.port() + "/echo"))
				.POST(HttpRequest.BodyPublishers.ofString("x".repeat(5000)))
				.build(), HttpResponse.BodyHandlers.ofString());

			assertEquals(413, large.statusCode(), "上限が効いていない: " + large.statusCode());

		} finally {

			server.stop();

		}

	}

	@Test
	@DisplayName("D-86 ヘッダの上限が効く")
	void maxHeaderSizeIsEnforced () throws Exception {

		/*
		 * 設定キーは前からあったが helidon に渡していなかった。
		 * 「書いたのに効かない」を回帰で捕まえる。
		 */
		use(Map.of("server.max_header_size", "512B"));

		JimbleServer server = JimbleServer.start(new JimbleApp() {
			{
				get("/hello", context -> context.response().send("ok"));
			}
		}, 0);

		try {

			HttpClient client = HttpClient.newHttpClient();

			URI uri = URI.create("http://127.0.0.1:" + server.port() + "/hello");

			assertEquals(200, client.send(HttpRequest.newBuilder().uri(uri).build()
				, HttpResponse.BodyHandlers.ofString()).statusCode());

			// 上限を超える大きさのヘッダを付ける
			HttpRequest big = HttpRequest.newBuilder()
				.uri(uri)
				.header("X-Big", "x".repeat(4096))
				.build();

			int status = -1;

			try {
				status = client.send(big, HttpResponse.BodyHandlers.ofString()).statusCode();
			} catch (IOException expected) {
				// 上限を超えたので接続ごと切られた。これも「効いている」
				status = -1;
			}

			assertNotEquals(200, status, "ヘッダの上限が効いていない");

		} finally {

			server.stop();

		}

	}

	@Test
	@DisplayName("D-86 待ち受けるアドレスを絞れる（F-H-06）")
	void hostCanBeLimited () throws Exception {

		use(Map.of("server.host", "127.0.0.1"));

		JimbleServer server = JimbleServer.start(new JimbleApp() {
			{
				get("/hello", context -> context.response().send("ok"));
			}
		}, 0);

		try {

			HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder()
					.uri(URI.create("http://127.0.0.1:" + server.port() + "/hello"))
					.build()
				, HttpResponse.BodyHandlers.ofString());

			assertEquals(200, response.statusCode(), "自分のマシンからも繋がらなくなっている");

		} finally {

			server.stop();

		}

	}

	// endregion

}
