package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 正規の URL へ寄せる（要件 D-166）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>スラッシュは前から無視している</b>（要件 F-R-24）ので、
 * {@code /a} も {@code /a/} も {@code //a} も 200 を返していた——
 * <b>同じ内容が複数の URL にある</b>状態である。
 * </p>
 *
 * <p>
 * <b>アプリからは何も見えない。</b>ルーターは正しく当てているし、例外も出ない。
 * 困るのは<b>外側</b>である——キャッシュは別物として持ち、
 * 検索エンジンは重複と見なし、<b>パス文字列で判定する ACL は片方だけ守る</b>。
 * </p>
 */
class CanonicalRedirectTest {

	/** 寄せ先を確かめるためのアプリ */
	static final class App extends JimbleApp {

		{
			get("/Users/{id}", context -> context.response().send(
				"id=" + context.request().path()));

			get("/hello", context -> context.response().send("hello"));

			post("/hello", context -> context.response().send("posted"));
		}

	}

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	/**
	 * リダイレクトを追わずに1回叩く
	 *
	 * @param port		ポート
	 * @param method	メソッド
	 * @param path		パス
	 * @return	応答
	 * @throws Exception	例外
	 */
	private static HttpResponse<String> call (int port, String method, String path) throws Exception {

		HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

		HttpRequest.Builder builder = HttpRequest.newBuilder(
			URI.create("http://localhost:" + port + path));

		if ("POST".equals(method)) {
			builder.POST(HttpRequest.BodyPublishers.ofString(""));
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

	}

	@Test
	@DisplayName("D-166 既定では寄せない（今までどおり両方 200）")
	void withoutTheOptionBothStillAnswer () throws Exception {

		conf("");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			assertEquals(200, call(server.port(), "GET", "/hello").statusCode());
			assertEquals(200, call(server.port(), "GET", "/hello/").statusCode());
			assertEquals(200, call(server.port(), "GET", "//hello").statusCode());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-166 スラッシュが余っていたら 301 で寄せる")
	void extraSlashesRedirect () throws Exception {

		conf("router { redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			// 正規の形はそのまま通る
			assertEquals(200, call(server.port(), "GET", "/hello").statusCode());

			for (String path : new String[] { "/hello/", "//hello", "/hello//", "//hello//" }) {

				HttpResponse<String> response = call(server.port(), "GET", path);

				assertEquals(301, response.statusCode(), path);
				assertEquals("/hello", response.headers().firstValue("Location").orElse(""), path);

			}

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-166 クエリは付けたまま寄せる")
	void theQueryStringSurvives () throws Exception {

		/*
		 * <b>落とすと、寄せた先で条件が消える。</b>
		 * 一覧の絞り込みが<b>黙って全件</b>になる。
		 */
		conf("router { redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> response = call(server.port(), "GET", "/hello/?q=1&r=2");

			assertEquals(301, response.statusCode());
			assertEquals("/hello?q=1&r=2", response.headers().firstValue("Location").orElse(""));

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-166 POST は寄せない（本文が消えるため）")
	void postIsNotRedirected () throws Exception {

		/*
		 * <b>301 を返すと、ブラウザは本文を落として GET にし直す。</b>
		 * 送ったつもりの登録が消えるので、<b>寄せずにそのまま受ける</b>。
		 */
		conf("router { redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> response = call(server.port(), "POST", "/hello/");

			assertEquals(200, response.statusCode());
			assertEquals("posted", response.body());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-166 ignore_case なら、綴りもルートに書いたほうへ寄せる")
	void theSpellingIsAlsoCanonicalised () throws Exception {

		conf("router { ignore_case = true, redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> response = call(server.port(), "GET", "/USERS/AbC");

			assertEquals(301, response.statusCode());

			/*
			 * <b>{@code Users} は寄せ、{@code AbC} は変えない。</b>
			 * 値まで小文字にすると、大文字を含む ID が壊れる。
			 */
			assertEquals("/Users/AbC", response.headers().firstValue("Location").orElse(""));

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-166 寄せないときも、アプリが見るパスはルーターと揃っている")
	void theApplicationSeesTheSamePathAsTheRouter () throws Exception {

		/*
		 * <b>ここが食い違うと、{@code before} フックだけがすり抜ける。</b>
		 * ルーターは {@code //Users//AbC} を {@code /Users/{id}} に当てるのに、
		 * パス文字列で判定するフックには<b>スラッシュだらけの文字列</b>が渡る。
		 */
		conf("");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			assertEquals("id=/Users/AbC", call(server.port(), "GET", "//Users//AbC//").body());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("当たらないパスは、スラッシュだけ直して寄せる")
	void anUnmatchedPathStillGetsItsSlashesFixed () throws Exception {

		conf("router { redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> response = call(server.port(), "GET", "//nowhere//");

			assertEquals(301, response.statusCode());
			assertEquals("/nowhere", response.headers().firstValue("Location").orElse(""));

			// 寄せた先はちゃんと 404
			assertEquals(404, call(server.port(), "GET", "/nowhere").statusCode());

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("寄せる先が同じなら、301 を返さない（無限ループにしない）")
	void theCanonicalFormIsNotRedirectedAgain () throws Exception {

		conf("router { ignore_case = true, redirect_to_canonical = true }");

		JimbleServer server = JimbleServer.start(new App(), 0);

		try {

			HttpResponse<String> response = call(server.port(), "GET", "/Users/AbC");

			assertEquals(200, response.statusCode()
				, "正規の形なのに寄せています（ブラウザが往復し続けます）");

			assertTrue(response.body().contains("/Users/AbC"), response.body());

		} finally {
			server.stop();
		}

	}

	// region ここで固定していないこと

	/*
	 * - <b>301 を 308 にするかどうか</b>は決めていない。
	 *   308 なら {@code POST} も本文を保ったまま寄せられるが、
	 *   <b>古いブラウザや中間のプロキシが落とすことがある</b>ので、
	 *   いまは {@code POST} を寄せないほうを選んでいる
	 * - <b>ブラウザが 301 を覚えること</b>も見ていない。
	 *   <b>寄せ先を間違えたまま出すと取り返しが付きにくい</b>ので、
	 *   寄せるのはスラッシュとルートに書いた綴りだけに限っている
	 */

	// endregion

}
