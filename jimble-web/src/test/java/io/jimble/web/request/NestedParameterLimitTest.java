package io.jimble.web.request;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * パラメータの添字で、メモリを使い切らせない（D-207）
 *
 * <p>
 * かつては添字の位置まで null で埋めて伸ばしていたので、{@code a[2000000000]=1}（20 バイトほど）で
 * 20 億個の要素を取りにいった。{@code Csrf.verify} の中でも読むので、<b>認証の前に</b>届いた。
 * </p>
 */
class NestedParameterLimitTest {

	private static WebContext context (Fakes.FakeRequestSource source) {

		return new WebContext(source, new Fakes.FakeResponseSink());

	}

	private static int status (Fakes.FakeRequestSource source) {

		try (WebContext context = context(source)) {
			HttpException ex = assertThrows(HttpException.class, () -> context.request().bodyAll());
			return ex.statusCode();
		}

	}

	@Test
	@DisplayName("大きな添字は、すぐに 400（メモリを取りにいかない）")
	void hugeIndex () {

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			assertEquals(400, status(new Fakes.FakeRequestSource("POST", "/x").form("a[2000000000]", "1")));
			assertEquals(400, status(new Fakes.FakeRequestSource("GET", "/x").query("a[0][2000000000]", "1")));
		});

	}

	@Test
	@DisplayName("int に収まらない添字も 400（かつては NumberFormatException で 500）")
	void overflowIndex () {

		assertEquals(400, status(new Fakes.FakeRequestSource("POST", "/x").form("a[99999999999999999999]", "1")));

	}

	@Test
	@DisplayName("キーを変えて並べても、伸ばせる合計に上限がある")
	void totalGrowth () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/x");
		for (int i = 0; i < 20; i++) {
			source.form("k" + i + "[" + NestedParameterParser.MAX_INDEX + "]", "1");
		}

		assertEquals(400, status(source));

	}

	@Test
	@DisplayName("D-272 深すぎる入れ子のキーは 400（かつては 422 を返すところで StackOverflowError）")
	void tooDeep () {

		String deep = "a" + ".a".repeat(50_000);

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			assertEquals(400, status(new Fakes.FakeRequestSource("POST", "/x").form(deep, "1")));
			assertEquals(400, status(new Fakes.FakeRequestSource("GET", "/x").query("a" + "[a]".repeat(50_000), "1")));
		});

		// 上限ちょうどまでは読める
		String limit = "a" + ".a".repeat(NestedParameterParser.MAX_DEPTH - 1);
		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x").form(limit, "1"))) {
			context.request().bodyAll();
		}

		assertEquals(400, status(new Fakes.FakeRequestSource("POST", "/x").form(limit + ".a", "1")));

	}

	@Test
	@DisplayName("上限の内側なら、今までどおり読める")
	void withinLimit () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("items[1]", "b")
			.form("items[0]", "a")
			.form("big[" + NestedParameterParser.MAX_INDEX + "]", "z"))) {

			assertEquals(List.of("a", "b"), context.request().bodyAll().getObjectListOptional("items", Object.class));
			assertEquals(NestedParameterParser.MAX_INDEX + 1, context.request().bodyAll().getObjectListOptional("big", Object.class).size());

		}

	}

	@Test
	@DisplayName("サーバー越しでも 400 を返す（落ちない）")
	void overHttp () throws Exception {

		JimbleServer server = JimbleServer.start(new JimbleApp() {
			{
				post("/form", context -> context.response().send(context.request().bodyAll().getString("x")));
			}
		}, 0);

		try {

			HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/form"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString("a%5B2000000000%5D=1"))
					.build()
				, HttpResponse.BodyHandlers.ofString());

			assertEquals(400, response.statusCode());

		} finally {
			server.stop();
		}

	}

}
