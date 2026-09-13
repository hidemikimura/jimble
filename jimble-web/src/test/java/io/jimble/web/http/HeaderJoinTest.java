package io.jimble.web.http;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同じヘッダが2行で来たときの繋ぎ方（D-173。要件 F-C-03）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>全部 {@code ";"} で繋いでいた。</b>HTTP で複数値を1行にまとめる区切りは
 * <b>{@code ", "}</b>（RFC 9110）で、{@code ";"} はその中の<b>パラメータの区切り</b>である
 * （{@code Accept: text/html;q=0.9} の {@code ;}）。
 * </p>
 *
 * <p>
 * だから {@code Accept: a, b} と {@code Accept: c} が2行で来ると
 * <b>{@code a, b;c}</b> になり、<b>{@code c} が {@code b} のパラメータとして読まれる</b>。
 * 例外は出ない。変わるのは<b>選ばれる表現だけ</b>である。
 * </p>
 */
class HeaderJoinTest {

	/** サーバー */
	private static JimbleServer server;

	/** ポート */
	private static int port;

	/** 起こす */
	@BeforeAll
	static void start () {

		server = JimbleServer.start(new JimbleApp() {
			{
				get("/echo", context -> context.response().send(
					context.request().header().getStringOptional("x-many")));

				get("/accept", context -> context.response().send(
					context.request().header().getStringOptional("accept")));
			}
		}, 0);

		port = server.port();

	}

	/** 止める */
	@AfterAll
	static void stop () {

		if (server != null) {
			server.stop();
		}

	}

	/** 2行で来たヘッダを叩く */
	private static String get (String path, String name, String... values) throws Exception {

		HttpRequest.Builder builder = HttpRequest.newBuilder(
			URI.create("http://127.0.0.1:" + port + path));

		for (String value : values) {
			builder.header(name, value);
		}

		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(builder.build(), HttpResponse.BodyHandlers.ofString());

		return response.body();

	}

	/** 複数値は ", " で繋がること */
	@Test
	@DisplayName("同じヘッダが2行で来たら \", \" で繋ぐ")
	void multipleValuesAreJoinedWithComma () throws Exception {

		String joined = get("/echo", "X-Many", "one", "two");

		assertEquals("one, two", joined, "区切りが \", \" でない");
		assertFalse(joined.contains(";"), "\";\" で繋いでいる: " + joined);

	}

	/**
	 * Accept が壊れないこと
	 *
	 * <p><b>これが実害の出る形である。</b></p>
	 */
	@Test
	@DisplayName("Accept が2行で来ても、パラメータとして読まれない")
	void acceptIsNotCorrupted () throws Exception {

		String joined = get("/accept", "Accept", "text/html", "application/json");

		assertEquals("text/html, application/json", joined, joined);
		assertFalse(joined.contains("html;application"), "後ろの型が q= のように読まれる形になっている: " + joined);

	}

	/** 1行のときは、そのままであること */
	@Test
	@DisplayName("1行なら、そのまま")
	void singleValueIsUnchanged () throws Exception {

		assertEquals("one", get("/echo", "X-Many", "one"));

	}

	/** Cookie は "; " で繋ぐこと */
	@Test
	@DisplayName("Cookie は \"; \" で繋ぐ（中身の区切りに合わせる）")
	void cookieUsesSemicolon () throws Exception {

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
				.header("Cookie", "a=1")
				.header("Cookie", "b=2")
				.build()
			, HttpResponse.BodyHandlers.ofString());

		assertTrue(response.statusCode() < 500, "落ちている: " + response.statusCode());

	}

}
