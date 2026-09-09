package io.jimble.web.server;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * アプリが書いたエラー応答を、既定が踏まないこと（要件 F-C-17 / D-11）
 *
 * <p>
 * <b>組み立てただけで送っていない</b>場合を見る。
 * {@code send(...)} まで呼んでいれば「送信済み」で止まるので、
 * <b>危ないのはこちら</b>——{@code json(...)} や {@code text(...)} で
 * 中身を用意して、送信は枠組みに任せる書き方である
 * （{@code docs/site/ja/errors.md} が勧めている形でもある）。
 * </p>
 */
class ErrorHookWinsTest {

	/** error で組み立てるだけのアプリ */
	static final class HookApp extends JimbleApp {

		{
			/*
			 * <b>send() を呼ばない。</b>用意するだけで、
			 * 送るのは Stage に任せる
			 */
			error((context, cause, statusCode) ->
				context.response().json("mine", statusCode));

			get("/hello", context -> context.response().send("hello"));
		}

	}

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new HookApp(), 0);
		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

	}

	/**
	 * 送る
	 *
	 * @param path		パス
	 * @param accept	Accept
	 * @return	応答
	 * @throws Exception 失敗した場合
	 */
	private static HttpResponse<String> get (String path, String accept) throws Exception {

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + server.port() + path))
			.header("Accept", accept)
			.timeout(Duration.ofSeconds(10))
			.GET()
			.build();

		return client.send(request, HttpResponse.BodyHandlers.ofString());

	}

	@Test
	@DisplayName("error で組み立てただけの本文を、既定が踏まない")
	void hookWinsOverDefault () throws Exception {

		HttpResponse<String> response = get("/nope", "application/json");

		assertEquals(404, response.statusCode());

		Data body = Data.fromJsonString(response.body());

		assertEquals(404, body.getInt("mine"));

		/*
		 * <b>既定が上書きしていないこと。</b>ここが混ざると、
		 * アプリが決めた形と枠組みの形が<b>1つの本文に同居する</b>
		 */
		assertFalse(body.containsKey("error"), response.body());

	}

	@Test
	@DisplayName("テキストを求められていても、アプリの形のまま返す")
	void hookWinsForText () throws Exception {

		HttpResponse<String> response = get("/nope", "text/html");

		assertEquals(404, response.statusCode());
		assertEquals(404, Data.fromJsonString(response.body()).getInt("mine"));

	}

}
