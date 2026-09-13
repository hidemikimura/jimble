package io.jimble.web.http;

import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多値のまま受け取る（{@code headerValues} / {@code cookieValues}）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * {@code headers()} は<b>同じ名前が2行で来たら1本に繋いでしまう</b>し、
 * {@code cookies()} は<b>先頭1本だけ採って残りを捨てる</b>。
 * どちらも 1.0 で型を変えられないので、<b>捨てる前を見る口を足した</b>。
 * </p>
 *
 * <p>
 * <b>いちばん困るのは Cookie のほう</b>である。別のパスやドメインに同じ名前で
 * 置かれると同じ名前が2本来るが、<b>どちらが先頭になるかは決まっていない</b>——
 * セッション ID でこれが起きると<b>ログインが不定期に外れる</b>。
 * 例外は出ないし、次のリクエストでは直っていることがある。
 * </p>
 */
class RequestValuesTest {

	/** 受け取った多値を返すアプリ */
	static final class App extends JimbleApp {

		{
			get("/headers", context -> context.response().send(
				"joined=" + context.request().source().headers().get("accept")
					+ " / values=" + context.request().source().headerValues().get("accept")));

			get("/cookies", context -> context.response().send(
				"first=" + context.request().source().cookies().get("sid")
					+ " / values=" + context.request().source().cookieValues().get("sid")
					// Cookies は触られるまで作らないので、ここで触って警告を通す
					+ " / raw=" + context.cookies().raw("sid")));
		}

	}

	/* 記録した警告 */
	private final List<String> warns = new CopyOnWriteArrayList<>();

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
		Conf.reload();

	}

	/**
	 * ヘッダを添えて1回叩く
	 *
	 * @param port		ポート
	 * @param path		パス
	 * @param headers	ヘッダ（名前, 値, 名前, 値, ...）
	 * @return	本文
	 * @throws Exception	例外
	 */
	private static String call (int port, String path, String...headers) throws Exception {

		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

		HttpRequest.Builder builder = HttpRequest.newBuilder(
			URI.create("http://localhost:" + port + path));

		for (int i = 0; i < headers.length; i += 2) {
			builder.header(headers[i], headers[i + 1]);
		}

		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

		return response.body();

	}

	@Test
	@DisplayName("同じヘッダが2行で来たら、繋ぐ前の2つが取れる")
	void headerValuesKeepsBothLines () throws Exception {

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			String body = call(server.port(), "/headers", "Accept", "text/html, text/plain", "Accept", "application/json");

			// 今までどおり ", " で繋いだものも取れる
			assertTrue(body.contains("joined=text/html, text/plain, application/json"), body);

			// 繋ぐ前は「2行だった」ことが分かる
			assertTrue(body.contains("values=[text/html, text/plain, application/json]"), body);

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("同じ名前の Cookie が2本来たら、捨てたほうも取れる")
	void cookieValuesKeepsBothCookies () throws Exception {

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			String body = call(server.port(), "/cookies", "Cookie", "sid=first; sid=second");

			assertTrue(body.contains("first=first"), body);
			assertTrue(body.contains("values=[first, second]"), body);

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("同じ名前の Cookie が2本来たら、黙って捨てずに警告する")
	void duplicateCookieIsWarned () throws Exception {

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			call(server.port(), "/cookies", "Cookie", "sid=first; sid=second");

			assertTrue(warns.stream().anyMatch(message -> message.contains("sid") && message.contains("2"))
				, "同じ名前の Cookie が2本来たのに警告が出ていません: " + warns);

			// 値はログに出さない（セッション ID が残る）
			assertFalse(warns.stream().anyMatch(message -> message.contains("first"))
				, "Cookie の値がログに出ています: " + warns);

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("1本しか来なければ警告しない")
	void singleCookieIsNotWarned () throws Exception {

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			call(server.port(), "/cookies", "Cookie", "sid=only");

			assertTrue(warns.stream().noneMatch(message -> message.contains("sid"))
				, "1本しか来ていないのに警告が出ています: " + warns);

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("繋いだ値しか持たない実装は、1行だったことにする（嘘の分割をしない）")
	void defaultDerivesFromTheSingleValueMap () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/")
			.header("Accept", "text/html, text/plain")
			.cookie("sid", "one");

		Map<String, List<String>> headerValues = source.headerValues();
		Map<String, List<String>> cookieValues = source.cookieValues();

		// "," で割ってはいけない。割ると Accept: text/html;q=0.9 が壊れる
		assertEquals(List.of("text/html, text/plain"), headerValues.get("accept"));
		assertEquals(List.of("one"), cookieValues.get("sid"));

	}

}
