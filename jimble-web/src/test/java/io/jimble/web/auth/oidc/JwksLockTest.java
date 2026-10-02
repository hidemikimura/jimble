package io.jimble.web.auth.oidc;

import io.jimble.util.conf.Conf;

import com.sun.net.httpserver.HttpServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWKS を引いている間も、ほかのプロバイダの鍵は待たずに読める（D-256）
 */
class JwksLockTest {

	private Config original;

	private HttpServer server;

	@AfterEach
	void tearDown () {

		if (server != null) {
			server.stop(0);
		}

		OidcProvider.reset();
		Jwks.reset();

		if (original != null) {
			Conf.replace(original);
		}

	}

	@Test
	@DisplayName("あるプロバイダの JWKS が遅くても、ほかのプロバイダの鍵（キャッシュ済み）はすぐ返る")
	void slowFetchDoesNotBlockOthers () throws Exception {

		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		// 呼ばれたら、離すまで返さない JWKS
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jwks", exchange -> {
			entered.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		Conf.reload();
		original = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("""
			auth.oidc.slow {
				issuer                 = "https://slow.example.com"
				client_id              = "c"
				client_secret          = "s"
				redirect_uri           = "https://app.example.com/cb"
				authorization_endpoint = "https://slow.example.com/authorize"
				token_endpoint         = "https://slow.example.com/token"
				jwks_uri               = "http://127.0.0.1:%d/jwks"
			}
			auth.oidc.fast {
				issuer                 = "https://fast.example.com"
				client_id              = "c"
				client_secret          = "s"
				redirect_uri           = "https://app.example.com/cb"
				authorization_endpoint = "https://fast.example.com/authorize"
				token_endpoint         = "https://fast.example.com/token"
				jwks_uri               = "https://fast.example.com/jwks"
			}
			""".formatted(server.getAddress().getPort())).withFallback(original));

		Jwks.put("fast", "k1", new Jwks.Key(KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic(), "RS256"));

		OidcProvider slow = OidcProvider.of("slow");
		OidcProvider fast = OidcProvider.of("fast");

		Thread fetching = Thread.ofVirtual().start(() -> {
			try {
				Jwks.find(slow, "unknown");
			} catch (RuntimeException ignore) {
				// 鍵が無いので失敗してよい
			}
		});

		try {

			assertTrue(entered.await(5, TimeUnit.SECONDS), "JWKS を引きにいっていない");

			long started = System.nanoTime();
			assertNotNull(Jwks.find(fast, "k1"));
			long millis = (System.nanoTime() - started) / 1_000_000;

			assertTrue(millis < 1000, "ほかのプロバイダの引き直しを待った: " + millis + "ms");

		} finally {
			release.countDown();
			fetching.join(10_000);
		}

	}

}
