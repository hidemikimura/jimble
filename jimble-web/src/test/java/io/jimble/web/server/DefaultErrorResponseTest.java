package io.jimble.web.server;

import io.jimble.util.data.Data;
import io.jimble.web.http.HttpException;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 既定のエラー応答（要件 F-C-17 / D-11）
 *
 * <p>
 * <b>{@code error(...)} を1つも書いていないアプリ</b>で確かめる。
 * 書いてあるアプリでは、そちらが勝つので既定は出てこない。
 * </p>
 *
 * <p>
 * 前はここが<b>空だった</b>。しかも JSON を求められたときだけ {@code {}} が返っていて、
 * <b>「中身の無い成功」と見分けが付かない</b>形だった。
 * </p>
 */
class DefaultErrorResponseTest {

	/** error を1つも書いていないアプリ */
	static final class BareApp extends JimbleApp {

		{
			get("/hello", context -> context.response().send("hello"));

			get("/boom", context -> {
				throw new IllegalStateException("内部の秘密の事情 / select * from users");
			});

			get("/forbidden", context -> {
				throw new HttpException(403, "だめ");
			});

			// 表に無いコード
			get("/odd", context -> {
				throw new HttpException(499, "変わったコード");
			});
		}

	}

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new BareApp(), 0);
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
	 * @param method	メソッド
	 * @param path		パス
	 * @param accept	Accept。付けないなら null
	 * @return	応答
	 * @throws Exception 失敗した場合
	 */
	private static HttpResponse<String> request (String method, String path, String accept) throws Exception {

		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + server.port() + path))
			.method(method, HttpRequest.BodyPublishers.noBody())
			.timeout(Duration.ofSeconds(10));

		if (accept != null) {
			builder.header("Accept", accept);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

	}

	// region JSON

	@Test
	@DisplayName("JSON を求められたら、決まった形の JSON を返す")
	void json () throws Exception {

		HttpResponse<String> response = request("GET", "/nope", "application/json");

		assertEquals(404, response.statusCode());

		assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json")
			, response.headers().toString());

		Data body = Data.fromJsonString(response.body());

		assertEquals(404, body.getData("error").getInt("status"));
		assertEquals("Not Found", body.getData("error").getString("message"));

	}

	@Test
	@DisplayName("空の {} は返さない")
	void notEmptyObject () throws Exception {

		/*
		 * <b>前はこれが {} だった。</b>
		 * 「中身の無い成功」と見分けが付かないので、無いより悪い
		 */
		assertFalse("{}".equals(request("GET", "/nope", "application/json").body()));

	}

	@Test
	@DisplayName("q 値つきでも JSON で返る")
	void jsonWithQuality () throws Exception {

		HttpResponse<String> response = request("GET", "/nope", "application/json;q=0.9");

		assertEquals("Not Found"
			, Data.fromJsonString(response.body()).getData("error").getString("message"));

	}

	// endregion

	// region テキスト

	@Test
	@DisplayName("JSON を求められていなければ、短いテキストを返す")
	void text () throws Exception {

		HttpResponse<String> response = request("GET", "/nope", "text/html");

		assertEquals(404, response.statusCode());
		assertEquals("404 Not Found", response.body());

		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/plain")
			, response.headers().toString());

	}

	@Test
	@DisplayName("*/*（何でもいい）もテキスト")
	void anything () throws Exception {

		assertEquals("404 Not Found", request("GET", "/nope", "*/*").body());

	}

	// endregion

	// region 中身

	@Test
	@DisplayName("500 でも内部の事情を出さない")
	void noInternals () throws Exception {

		for (String accept : new String[]{"application/json", "text/html"}) {

			HttpResponse<String> response = request("GET", "/boom", accept);

			assertEquals(500, response.statusCode());

			/*
			 * <b>例外のメッセージも SQL も出さない</b>（要件 NF-S-06）。
			 * ここに原因を書くと、<b>本番かどうかの判断を1か所忘れただけで漏れる</b>。
			 * 原因はログに残っている
			 */
			assertFalse(response.body().contains("秘密"), response.body());
			assertFalse(response.body().contains("select"), response.body());
			assertFalse(response.body().contains("IllegalStateException"), response.body());

			assertTrue(response.body().contains("Internal Server Error"), response.body());

		}

	}

	@Test
	@DisplayName("HttpException のメッセージも出さない（コードだけ使う）")
	void httpExceptionMessage () throws Exception {

		HttpResponse<String> response = request("GET", "/forbidden", "text/html");

		assertEquals(403, response.statusCode());
		assertEquals("403 Forbidden", response.body());

	}

	@Test
	@DisplayName("表に無いコードでも、種別だけは言う")
	void unknownStatusCode () throws Exception {

		HttpResponse<String> response = request("GET", "/odd", "text/html");

		assertEquals(499, response.statusCode());

		/*
		 * <b>空にしない。</b>"499 " だけが返ると、
		 * <b>何も分からないのに何かある</b>形になる
		 */
		assertEquals("499 Error", response.body());

	}

	// endregion

	// region 405

	@Test
	@DisplayName("メソッド違いは 405 で、Allow が付く")
	void methodNotAllowed () throws Exception {

		HttpResponse<String> response = request("POST", "/hello", "application/json");

		assertEquals(405, response.statusCode());
		assertEquals("GET", response.headers().firstValue("allow").orElse(null));

		assertEquals("Method Not Allowed"
			, Data.fromJsonString(response.body()).getData("error").getString("message"));

	}

	// endregion

	// region 正常な応答は変えない

	@Test
	@DisplayName("成功したときの応答は今までどおり")
	void success () throws Exception {

		HttpResponse<String> response = request("GET", "/hello", "application/json");

		assertEquals(200, response.statusCode());
		assertEquals("hello", response.body());

	}

	// endregion

}
