package io.jimble.web.server;

import io.jimble.web.http.HttpException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 実際に helidon を起動して疎通させる結合テスト
 */
class JimbleServerIntegrationTest {

	/**
	 * テスト用アプリケーション
	 */
	static final class SampleApp extends JimbleApp {

		{
			error((context, cause, statusCode) ->
				context.response().code(statusCode).send("error:" + statusCode));

			get("/hello", context -> context.response().send("hello"));

			head("/health_check", context -> context.response().send());

			get("/users/{id}", context ->
				context.response().send("user=" + context.route().variables().get("id")));

			get("/search/{keyword}", context ->
				context.response().send("keyword=" + context.route().variables().get("keyword")));

			get("/files/*", context ->
				context.response().send("file=" + context.route().variables().wildcard()));

			post("/save", () -> context -> context.response().send("saved"));

			// ストリームで返すルート。helidon の isSent() は
			// outputStream() で書いた場合 false のままなので、
			// 後段が「まだ返していない」と誤判定しないかを見る
			get("/stream", context ->
				context.response().send(new ByteArrayInputStream("streamed".getBytes(StandardCharsets.UTF_8))));

			// JSON（Content-Type が付くか。要件 D-73）
			get("/json", context -> context.response().json("ok", true));

			// 自分で Content-Type を決めたら上書きしない
			get("/json-custom", context -> {
				context.response().setResponseHeader("Content-Type", "application/vnd.jimble+json");
				context.response().json("ok", true);
			});

			get("/boom", context -> {
				throw new IllegalStateException("boom");
			});

			get("/forbidden", context -> {
				throw new HttpException(403, "だめ");
			});
		}

	}

	/* サーバー */
	private static JimbleServer server;

	/* HTTPクライアント */
	private static HttpClient client;

	// docs:begin test-server
	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new SampleApp(), 0);
		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

	}

	/**
	 * リクエストを送る
	 */
	private static HttpResponse<String> request (String method, String path) throws Exception {

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + server.port() + path))
			.method(method, HttpRequest.BodyPublishers.noBody())
			.timeout(Duration.ofSeconds(10))
			.build();

		return client.send(request, HttpResponse.BodyHandlers.ofString());

	}
	// docs:end

	@Test
	@DisplayName("ストリームで返しても後段が重ねて返さない")
	void streamed () throws Exception {

		/*
		 * helidon の isSent() は send(...) 系でしか true にならない。
		 * outputStream() で書き出した場合は false のままなので、
		 * ディスパッチャが「ルートが何も返していない」と判断して
		 * 204 やエラーを重ねて返そうとし、
		 * 「Cannot set response header after requesting output stream」で落ちていた。
		 */
		HttpResponse<String> response = request("GET", "/stream");

		assertEquals(200, response.statusCode());
		assertEquals("streamed", response.body());

	}

	@Test
	@DisplayName("D-73 JSON を返したら Content-Type も JSON になる")
	void jsonContentType () throws Exception {

		HttpResponse<String> response = request("GET", "/json");

		assertEquals(200, response.statusCode());
		assertEquals("{\"ok\":true}", response.body());

		/*
		 * ブラウザの fetch().json() は Content-Type を見ないので
		 * 付いていなくても動いてしまう。付いていないと困るのは
		 * curl | jq、プロキシ、Accept で振り分ける中継、他言語のクライアント。
		 */
		assertEquals("application/json; charset=UTF-8"
			, response.headers().firstValue("content-type").orElse(""));

	}

	@Test
	@DisplayName("D-73 自分で決めた Content-Type は上書きしない")
	void jsonContentTypeNotOverwritten () throws Exception {

		HttpResponse<String> response = request("GET", "/json-custom");

		assertEquals(200, response.statusCode());
		assertEquals("application/vnd.jimble+json"
			, response.headers().firstValue("content-type").orElse(""));

	}

	@Test
	@DisplayName("GET /hello が動く")
	void hello () throws Exception {

		HttpResponse<String> response = request("GET", "/hello");

		assertEquals(200, response.statusCode());
		assertEquals("hello", response.body());

	}

	@Test
	@DisplayName("HEAD が動く")
	void head () throws Exception {

		assertEquals(204, request("HEAD", "/health_check").statusCode());

	}

	@Test
	@DisplayName("パスパラメータが取れる")
	void pathVariable () throws Exception {

		assertEquals("user=42", request("GET", "/users/42").body());

	}

	@Test
	@DisplayName("T-17 %2F を含むパスパラメータが1つの値として取れる")
	void encodedSlash () throws Exception {

		HttpResponse<String> response = request("GET", "/search/a%2Fb");

		assertEquals(200, response.statusCode(), "デコード済みパスで分割していたら 404 になる");
		assertEquals("keyword=a/b", response.body());

	}

	@Test
	@DisplayName("T-19 日本語のパスパラメータが取れる")
	void japanesePathVariable () throws Exception {

		HttpResponse<String> response = request("GET", "/search/%E3%81%A6%E3%81%99%E3%81%A8");

		assertEquals("keyword=てすと", response.body());

	}

	@Test
	@DisplayName("T-18 末尾スラッシュがあっても同じルートにマッチする")
	void trailingSlash () throws Exception {

		assertEquals("hello", request("GET", "/hello/").body());

	}

	@Test
	@DisplayName("ワイルドカードの残りが取れる")
	void wildcard () throws Exception {

		assertEquals("file=css/main.css", request("GET", "/files/css/main.css").body());

	}

	@Test
	@DisplayName("Executor 形式のルートが動く")
	void executorRoute () throws Exception {

		assertEquals("saved", request("POST", "/save").body());

	}

	@Test
	@DisplayName("T-14 未マッチは 404 でエラーハンドラに届く")
	void notFound () throws Exception {

		HttpResponse<String> response = request("GET", "/nope");

		assertEquals(404, response.statusCode());
		assertEquals("error:404", response.body());

	}

	@Test
	@DisplayName("未処理例外は 500")
	void serverError () throws Exception {

		HttpResponse<String> response = request("GET", "/boom");

		assertEquals(500, response.statusCode());
		assertEquals("error:500", response.body());

	}

	@Test
	@DisplayName("T-13 HttpException のコードが反映される")
	void httpException () throws Exception {

		HttpResponse<String> response = request("GET", "/forbidden");

		assertEquals(403, response.statusCode());
		assertEquals("error:403", response.body());

	}

	@Test
	@DisplayName("メソッドが違えば 405。何なら通るかを Allow で言う")
	void wrongMethod () throws Exception {

		HttpResponse<String> response = request("POST", "/hello");

		/*
		 * <b>404 にしない。</b>404 は「そんなものは無い」、405 は「あるが、その呼び方ではない」。
		 * 一緒にすると、{@code get} と書くべきところを {@code post} と書いただけの間違いが
		 * 「パスが違う」に見える
		 */
		assertEquals(405, response.statusCode());

		// Allow を付けるのは仕様（RFC 9110）の MUST。無いと何を試せばよいか分からない
		assertEquals("GET", response.headers().firstValue("allow").orElse(null));

	}

	@Test
	@DisplayName("そんなパスが無ければ、いままでどおり 404")
	void unknownPath () throws Exception {

		HttpResponse<String> response = request("GET", "/nope");

		assertEquals(404, response.statusCode());
		assertTrue(response.headers().firstValue("allow").isEmpty(), "Allow が付いている");

	}

	@Test
	@DisplayName("起動時にポートが取れる")
	void portIsAvailable () {

		assertTrue(server.port() > 0);

	}

}
