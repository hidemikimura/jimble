package io.jimble.web.captcha;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.json.Dson;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTCHA：問題を取り、解いて、送る（helidon を起動して確かめる）
 */
class AltchaIntegrationTest {

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	/* サーバー */
	private static JimbleServer server;

	@BeforeAll
	static void startServer () {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString("""
			altcha { secret = "0123456789abcdef0123456789abcdef", cost = 10, counter_max = 40 }
			""").withFallback(Conf.conf().config()));

		JimbleApp app = new JimbleApp() {
			{
				// docs:begin altcha-routes
				get("/altcha/challenge", Altcha::challenge);

				path("/contact", () -> {
					before(Altcha::verify);
					get("", context -> context.response().text("form"));
					post("", context -> context.response().text("ok"));
				});
				// docs:end
			}
		};

		server = JimbleServer.start(app, 0);

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		Conf.reload();

	}

	@Test
	@DisplayName("問題は JSON で、キャッシュさせない。項目名は変えずに返す（expiresAt など）")
	void challenge () throws Exception {

		HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(url("/altcha/challenge")).build(), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
		assertEquals("no-store", response.headers().firstValue("cache-control").orElse(""));

		Map<?, ?> parameters = (Map<?, ?>) Dson.decodes(response.body(), Map.class).get("parameters");
		assertTrue(parameters.containsKey("expiresAt") && parameters.containsKey("keySignature"), response.body());

	}

	@Test
	@DisplayName("フォームで送った答えは 1 回だけ通る。2 回目は 403")
	void formOnce () throws Exception {

		String payload = solvedPayload();

		assertEquals(200, postForm(payload).statusCode());

		HttpResponse<String> again = postForm(payload);
		assertEquals(403, again.statusCode(), again.body());

	}

	@Test
	@DisplayName("答えが無ければ 403。GET は素通し")
	void missing () throws Exception {

		assertEquals(403, postForm("").statusCode());
		assertEquals(200, HTTP.send(HttpRequest.newBuilder(url("/contact")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

	}

	@Test
	@DisplayName("JSON の本文の altcha でも、X-Altcha ヘッダでも送れる")
	void jsonAndHeader () throws Exception {

		HttpResponse<String> json = HTTP.send(HttpRequest.newBuilder(url("/contact"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"altcha\":\"" + solvedPayload() + "\"}"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, json.statusCode(), json.body());

		HttpResponse<String> header = HTTP.send(HttpRequest.newBuilder(url("/contact"))
			.header(Altcha.HEADER_NAME, solvedPayload())
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.ofString());

		assertEquals(200, header.statusCode(), header.body());

	}

	@Test
	@DisplayName("check は結果を返し、断り方はアプリが決められる（2 回目は REPLAYED）")
	void check () throws Exception {

		String payload = solvedPayload();

		assertEquals(AltchaResult.VERIFIED, Altcha.check(payload));
		assertEquals(AltchaResult.REPLAYED, Altcha.check(payload));

	}

	@Test
	@DisplayName("鍵が書いていない・短いと、使ったところで止める")
	void secretRequired () {

		Map<String, String> secrets = Map.of("", "0 文字", "short", "5 文字");

		secrets.forEach((secret, length) -> {
			Conf.replace(ConfigFactory.parseString("altcha.secret = \"%s\"".formatted(secret)).withFallback(Conf.conf().config()));
			IllegalStateException thrown = assertThrows(IllegalStateException.class, Altcha::createChallenge);
			assertTrue(thrown.getMessage().contains("altcha.secret") && thrown.getMessage().contains(length), thrown.getMessage());
		});

		Conf.replace(ConfigFactory.parseString("altcha.secret = \"0123456789abcdef0123456789abcdef\"").withFallback(Conf.conf().config()));

	}

	// region ヘルパー

	/* 問題を取って、ブラウザと同じに解く */
	private static String solvedPayload () throws Exception {

		HttpResponse<String> challenge = HTTP.send(HttpRequest.newBuilder(url("/altcha/challenge")).build(), HttpResponse.BodyHandlers.ofString());

		return AltchaPowTest.encode(AltchaPowTest.solution(challenge.body()));

	}

	private static HttpResponse<String> postForm (String payload) throws Exception {

		return HTTP.send(HttpRequest.newBuilder(url("/contact"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString("name=a&altcha=" + URLEncoder.encode(payload, StandardCharsets.UTF_8)))
			.build(), HttpResponse.BodyHandlers.ofString());

	}

	private static URI url (String path) {

		return URI.create("http://localhost:%d%s".formatted(server.port(), path));

	}

	// endregion

}
