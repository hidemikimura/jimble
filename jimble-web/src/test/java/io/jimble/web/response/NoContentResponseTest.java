package io.jimble.web.response;

import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本文を持てないステータス（204 / 205 / 304）で送る（D-179）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code json(...)} を積んでから {@code code(204).send()} と書くと 500 になっていた。</b>
 * helidon は 204 / 205 / 304 に本文を書こうとすると例外を投げる（JSON のように
 * 出力ストリームで書く道）か、<b>黙って 500 に差し替える</b>（バイト列で書く道）。
 * どちらの道を通るかは送り方で決まるので、アプリからは「たまに 500」に見える。
 * </p>
 *
 * <p>
 * <b>もっと困るのは、何も積んでいなくても落ちていたこと</b>である。
 * {@code send()} は「JSON を求められたら、何も無くても {@code {}} を返す」ので、
 * fetch() のように {@code Accept: application/json} を付けてくる相手に
 * {@code code(204).send()} を返すと、204 に {@code {}} を書こうとして落ちる。
 * ブラウザで試すと通り、画面の JavaScript から呼ぶと 500 になる。
 * </p>
 *
 * <p>
 * <b>ここはモックでは確かめられない</b>——落ちていたのは helidon の中なので、実際に起動して叩く。
 * </p>
 */
class NoContentResponseTest {

	/* 捨てるときに閉じたか */
	private static final AtomicBoolean STREAM_CLOSED = new AtomicBoolean();

	/** 試すためのアプリ */
	static final class App extends JimbleApp {

		{
			// 報告された形そのもの
			get("/json/{code}", c -> {
				c.response().json("id", 1);
				c.response().code(code(c)).send();
			});

			// send() を書かない形（json() だけで送られる）
			get("/json-auto/{code}", c -> c.response().code(code(c)).json("id", 1));

			get("/text/{code}", c -> c.response().code(code(c)).send("hello"));

			get("/empty/{code}", c -> c.response().code(code(c)).send(""));

			get("/stream/{code}", c -> c.response().code(code(c)).send(new ByteArrayInputStream("xx".getBytes()) {
				@Override
				public void close () throws IOException {
					STREAM_CLOSED.set(true);
					super.close();
				}
			}, "text/plain", 2));

			get("/none/{code}", c -> c.response().code(code(c)).send());

			// 無いテンプレート。描けば 500 になる
			get("/view/{code}", c -> c.response().code(code(c)).view("no/such/template.jte"));

			// 送り方ごとに1本ずつ（どれか1つだけ手当てが外れても分かるように）
			get("/way/jsonl", c -> c.response().code(204).jsonL(List.of(io.jimble.util.data.Data.fromJsonString("{\"id\":1}"))));
			get("/way/text-type", c -> c.response().code(204).send("hello", "text/plain"));
			get("/way/text-charset", c -> c.response().code(204).send("hello", StandardCharsets.UTF_8));
			get("/way/text-type-charset", c -> c.response().code(204).send("hello", "text/csv", StandardCharsets.UTF_8));
			get("/way/bytes", c -> c.response().code(204).send("hello".getBytes(StandardCharsets.UTF_8)));
			get("/way/stream", c -> c.response().code(204).send(new ByteArrayInputStream("xx".getBytes())));
			get("/way/file", c -> c.response().code(204).file(FILE.toFile(), "text/plain"));
			get("/way/download", c -> c.response().code(204).download(FILE.toFile(), "a.txt"));

			// ログアウトを 204 で返す形。Set-Cookie は届かなければいけない
			get("/cookie/{code}", c -> {
				c.cookies().put("mark", "1");
				c.response().json("ok", true);
				c.response().code(code(c)).send();
			});
		}

		private static int code (io.jimble.web.context.WebContext c) {
			return Integer.parseInt(c.request().bodyPath().getString("code"));
		}

	}

	private static JimbleServer server;

	/* ファイルで送る道のための中身 */
	private static java.nio.file.Path FILE;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	/* 記録した警告 */
	private final List<String> warns = new CopyOnWriteArrayList<>();

	@BeforeAll
	static void start () throws IOException {

		FILE = java.nio.file.Files.createTempFile("no-content", ".txt");
		java.nio.file.Files.writeString(FILE, "file body");

		server = JimbleServer.start(new App(), 0);

	}

	@AfterAll
	static void stop () throws IOException {

		server.stop();
		java.nio.file.Files.deleteIfExists(FILE);

	}

	@BeforeEach
	void captureLog () {

		STREAM_CLOSED.set(false);

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
	 * 叩く
	 *
	 * @param path		パス
	 * @param accept	Accept ヘッダ
	 * @return	応答
	 * @throws Exception	例外
	 */
	private static HttpResponse<String> get (String path, String accept) throws Exception {

		return CLIENT.send(
			HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
				.header("Accept", accept)
				.timeout(Duration.ofSeconds(5))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * 本文が付いていないことを確かめる
	 *
	 * @param response	応答
	 * @param status	期待するステータス
	 */
	private static void assertNoBody (HttpResponse<String> response, int status) {

		assertEquals(status, response.statusCode(), "ステータスが変わっています（本文を書こうとして 500 になっていないか）: " + response.body());
		assertEquals("", response.body());
		assertTrue(response.headers().firstValue("content-type").isEmpty()
			, "本文が無いのに Content-Type が付いています: " + response.headers().map());

	}

	@ParameterizedTest
	@ValueSource(ints = { 204, 205, 304 })
	@DisplayName("json() を積んでから code(204).send() しても、500 にならず本文なしで返る")
	void jsonIsDroppedForBodilessStatus (int status) throws Exception {

		assertNoBody(get("/json/" + status, "*/*"), status);

		assertTrue(warns.stream().anyMatch(w -> w.contains(String.valueOf(status)) && w.contains("JSON") && w.contains("/json/" + status))
			, "捨てたことがログに出ていません: " + warns);

	}

	@Test
	@DisplayName("send() を書かない形（json() だけ）でも同じ")
	void jsonWithoutSendIsDroppedToo () throws Exception {

		assertNoBody(get("/json-auto/204", "*/*"), 204);

	}

	@ParameterizedTest
	@ValueSource(ints = { 204, 205, 304 })
	@DisplayName("何も積んでいなければ、Accept: application/json でも 500 にならない（警告も出さない）")
	void acceptJsonAloneDoesNotBreakNoContent (int status) throws Exception {

		assertNoBody(get("/none/" + status, "application/json"), status);

		assertTrue(warns.isEmpty(), "何も捨てていないのに警告が出ています: " + warns);

	}

	@ParameterizedTest
	@ValueSource(strings = { "jsonl", "text-type", "text-charset", "text-type-charset", "bytes", "stream", "file", "download" })
	@DisplayName("どの送り方でも、500 にならず本文なしで返り、捨てたことが出る")
	void everyWayOfSendingIsCovered (String way) throws Exception {

		assertNoBody(get("/way/" + way, "*/*"), 204);

		assertTrue(warns.stream().anyMatch(w -> w.contains("204") && w.contains("/way/" + way))
			, "捨てたことがログに出ていません: " + warns);

	}

	@Test
	@DisplayName("文字列も捨てる（Content-Type も付けない）")
	void textIsDropped () throws Exception {

		assertNoBody(get("/text/204", "*/*"), 204);

		assertTrue(warns.stream().anyMatch(w -> w.contains("文字列")), warns.toString());

	}

	@Test
	@DisplayName("空の本文は捨てるものが無いので、警告しない")
	void emptyBodyIsNotWarned () throws Exception {

		assertNoBody(get("/empty/204", "*/*"), 204);

		assertTrue(warns.isEmpty(), "空なのに警告が出ています: " + warns);

	}

	@Test
	@DisplayName("ストリームも捨てる。捨てるときも閉じる")
	void streamIsDroppedAndClosed () throws Exception {

		assertNoBody(get("/stream/204", "*/*"), 204);

		assertTrue(STREAM_CLOSED.get(), "捨てたストリームを閉じていません（ファイルなら開きっぱなしになる）");
		assertTrue(warns.stream().anyMatch(w -> w.contains("ストリーム")), warns.toString());

	}

	@Test
	@DisplayName("捨てるだけのテンプレートは描かない（描けば 500）")
	void templateIsNotRendered () throws Exception {

		assertNoBody(get("/view/204", "text/html"), 204);

		assertTrue(warns.stream().anyMatch(w -> w.contains("テンプレート")), warns.toString());

	}

	@Test
	@DisplayName("204 でも Set-Cookie は届く")
	void cookiesStillGoOutWithNoContent () throws Exception {

		HttpResponse<String> response = get("/cookie/204", "*/*");

		assertNoBody(response, 204);
		assertTrue(response.headers().allValues("set-cookie").stream().anyMatch(v -> v.startsWith("mark="))
			, "Set-Cookie が落ちています: " + response.headers().map());

	}

	@Test
	@DisplayName("本文を持てるステータスでは、これまでどおり本文を返す")
	void bodyIsKeptForOtherStatuses () throws Exception {

		HttpResponse<String> response = get("/json/200", "*/*");

		assertEquals(200, response.statusCode());
		assertEquals("{\"id\":1}", response.body());

		HttpResponse<String> created = get("/text/201", "*/*");

		assertEquals(201, created.statusCode());
		assertEquals("hello", created.body());

		assertTrue(warns.isEmpty(), warns.toString());

	}

	@Test
	@DisplayName("本文を持てないのは 204 / 205 / 304 だけ")
	void bodilessStatuses () {

		assertTrue(Response.isBodilessStatus(204));
		assertTrue(Response.isBodilessStatus(205));
		assertTrue(Response.isBodilessStatus(304));

		for (int code : new int[] { 200, 201, 202, 206, 301, 302, 400, 404, 500 }) {
			assertFalse(Response.isBodilessStatus(code), String.valueOf(code));
		}

	}

}
