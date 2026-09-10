package approval.pages;

import io.jimble.util.conf.Conf;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-pages）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * <b>DB は要らない。</b>{@code @Tag("db")} を付けていないので、
 * DB の無い CI ジョブ（{@code build}）で走る。
 * </p>
 *
 * <p>
 * 転送先（{@link UpstreamApp}）も同じプロセスの別ポートに立てる。
 * 外のサービスは要らない。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:approval-pages:test
 * </pre>
 */
class ApprovalPagesIntegrationTest {

	/* 転送先 */
	private static JimbleServer upstream;

	/* サンプル本体 */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		upstream = UpstreamApp.start();

		server = JimbleServer.start(new PagesApp("http://127.0.0.1:" + upstream.port()), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			// 302 を追わない。プロキシが追わないことを確かめたいので
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		if (upstream != null) {
			upstream.stop();
		}

	}

	// region jte のレイアウトと部品（要件 F-W-08 / F-W-10）

	@Test
	@DisplayName("F-W-08 レイアウトの枠と、ページ固有の中身が両方出る")
	void layoutAndContent () throws Exception {

		HttpResponse<String> response = get("/");

		assertEquals(200, response.statusCode());

		String body = response.body();

		// 枠（layout/page.jte が持っているもの）
		assertTrue(body.contains("<!doctype html>"), body);
		assertTrue(body.contains("<link rel=\"stylesheet\" href=\"/assets/app.css\">"), body);
		assertTrue(body.contains("| 経費・購買の申請</title>"), "レイアウトの title の作りが出ていない: " + body);

		// 部品（tag/nav.jte）
		assertTrue(body.contains("<nav>"), body);

		// このページ固有の中身
		assertTrue(body.contains("このサンプルで見られるもの"), body);

	}

	@Test
	@DisplayName("F-W-10 2枚のページが同じ枠を共有している")
	void layoutIsShared () throws Exception {

		/*
		 * <b>ここがこのサンプルの主題である。</b>
		 *
		 * examples/blog のテンプレートは2枚とも独立した完結 HTML で、
		 * 枠を共有していなかった。ヘッダを直すには全部を直す必要があった。
		 *
		 * 枠が1か所にしか無いなら、2枚とも同じ文字列を含む。
		 */
		String home = get("/").body();
		String requests = get("/requests").body();

		for (String shared : new String[]{
			"<link rel=\"stylesheet\" href=\"/assets/app.css\">"
			, "| 経費・購買の申請</title>"
			, "jimble のサンプル（N-3 / approval-pages）"
			, "<nav>"
		}) {
			assertTrue(home.contains(shared), "ホームに枠がない: " + shared);
			assertTrue(requests.contains(shared), "申請一覧に枠がない: " + shared);
		}

		// 中身は違う
		assertTrue(home.contains("このサンプルで見られるもの"), home);
		assertTrue(requests.contains("差し戻し"), requests);
		assertFalse(home.contains("差し戻し"), "中身まで共有されている");

	}

	@Test
	@DisplayName("F-W-10 部品が引数で見た目を変える")
	void partTakesArgument () throws Exception {

		/*
		 * nav.jte は「いま見ているところ」を受け取って印を付ける。
		 * ページごとに違う値が渡っていること。
		 */
		assertTrue(get("/").body().contains("href=\"/\" class=\"current\""), get("/").body());
		assertTrue(get("/requests").body().contains("href=\"/requests\" class=\"current\"")
			, get("/requests").body());

	}

	@Test
	@DisplayName("F-W-10 部品の省略できる引数は、書かなければ出ない")
	void partOptionalArgument () throws Exception {

		String home = get("/").body();

		// note を渡した箱にだけ出る
		assertTrue(home.contains("この箱そのものが部品です"), home);

		// 渡していない箱には <p class="note"> が付かない。1回しか出ないことで見る
		assertEquals(1, home.split("class=\"note\"", -1).length - 1
			, "note を渡していない箱にも出ている: " + home);

	}

	@Test
	@DisplayName("エラー画面も同じ枠を使う")
	void errorPageUsesLayout () throws Exception {

		HttpResponse<String> response = get("/nothing-here");

		assertEquals(404, response.statusCode());
		assertTrue(response.body().contains("<nav>"), "404 だけ枠が違う: " + response.body());

	}

	// endregion

	// region 静的配信（要件 F-W-18）

	@Test
	@DisplayName("F-W-18 静的ファイルが ETag つきで返る")
	void assetHasEtag () throws Exception {

		HttpResponse<String> response = get("/assets/app.css");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("font-family"), response.body());

		assertNotNull(header(response, "etag"), "ETag が付いていない");
		assertNotNull(header(response, "last-modified"), "Last-Modified が付いていない");

		// 弱い ETag（中身のハッシュではなく、パス・更新時刻・サイズから作る）
		assertTrue(header(response, "etag").startsWith("W/\""), header(response, "etag"));

	}

	@Test
	@DisplayName("F-W-18 同じ ETag を送り返すと 304。本文は返らない")
	void assetNotModified () throws Exception {

		String etag = header(get("/assets/app.css"), "etag");

		HttpResponse<String> again = client.send(
			HttpRequest.newBuilder(uri("/assets/app.css"))
				.header("If-None-Match", etag)
				.GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertEquals(304, again.statusCode());
		assertTrue(again.body().isEmpty(), "304 なのに本文がある: " + again.body());

	}

	@Test
	@DisplayName("F-W-18 違う ETag なら 200 で返り直す")
	void assetChangedEtag () throws Exception {

		HttpResponse<String> response = client.send(
			HttpRequest.newBuilder(uri("/assets/app.css"))
				// ヘッダの値に非 ASCII は入れられない（HttpClient が弾く）
				.header("If-None-Match", "W/\"stale\"")
				.GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertEquals(200, response.statusCode());
		assertFalse(response.body().isEmpty());

	}

	@Test
	@DisplayName("F-X-07 動的は no-store、静的はキャッシュしてよい")
	void cacheControlDiffers () throws Exception {

		/*
		 * <b>既定が逆向きである。</b>
		 *
		 * 動的な応答は no-store（要件 F-X-07）。
		 * 逆にしていると、ログイン後の画面が中間のキャッシュに残る。
		 * 静的配信<b>だけ</b>がそれを上書きする。
		 */
		assertEquals("no-store", header(get("/"), "cache-control"));

		// .css は「変わらないもの」扱い
		assertTrue(header(get("/assets/app.css"), "cache-control").contains("immutable")
			, header(get("/assets/app.css"), "cache-control"));

		// .txt は変わりうるもの扱い。毎回聞きに来させる
		String txt = header(get("/assets/readme.txt"), "cache-control");

		assertTrue(txt.contains("must-revalidate"), txt);
		assertFalse(txt.contains("immutable"), "拡張子で分けられていない: " + txt);

	}

	@Test
	@DisplayName("F-W-18 HEAD はヘッダだけ返る")
	void assetHead () throws Exception {

		HttpResponse<String> response = client.send(
			HttpRequest.newBuilder(uri("/assets/app.css")).method("HEAD"
				, HttpRequest.BodyPublishers.noBody()).build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertEquals(200, response.statusCode());
		assertNotNull(header(response, "etag"));
		assertTrue(response.body().isEmpty(), response.body());

	}

	@Test
	@DisplayName("無い静的ファイルは 404")
	void assetMissing () throws Exception {

		assertEquals(404, get("/assets/nothing.css").statusCode());

	}

	@Test
	@DisplayName("静的配信は上の階層へ出られない")
	void assetCannotEscape () throws Exception {

		/*
		 * .. も %2e%2e も弾く。
		 * 弾かないと、クラスパス上の何でも配れてしまう。
		 */
		assertNotEquals(200, get("/assets/../conf/application.conf").statusCode());
		assertNotEquals(200, get("/assets/%2e%2e/conf/application.conf").statusCode());

	}

	// endregion

	// region SPA（要件 F-W-17）

	@Test
	@DisplayName("F-W-17 どのパスでも index.html が返る")
	void spaReturnsIndex () throws Exception {

		for (String path : new String[]{ "/app/", "/app/settings", "/app/deep/er/still" }) {

			HttpResponse<String> response = get(path);

			assertEquals(200, response.statusCode(), path);
			assertTrue(response.body().contains("申請アプリ（SPA）"), path + " → " + response.body());

		}

	}

	@Test
	@DisplayName("F-W-17 実ファイルがあれば index.html ではなくそれを返す")
	void spaServesRealFile () throws Exception {

		HttpResponse<String> response = get("/app/app.js");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("console.log"), response.body());
		assertFalse(response.body().contains("<html"), "index.html が返っている: " + response.body());

	}

	@Test
	@DisplayName("F-W-17 index.html の title をサーバー側で差し替えられる")
	void spaRewritesTitle () throws Exception {

		/*
		 * <b>クローラは JavaScript を待たない。</b>
		 * title と OGP をブラウザ側で入れると、拾われるのは差し替え前の中身になる。
		 */
		String body = get("/app/items/42").body();

		assertTrue(body.contains("<title>申請 42</title>"), body);
		assertFalse(body.contains("<!--title-->"), "差し替えの目印が残っている: " + body);

		// 当たらないパスは差し替えない
		assertTrue(get("/app/settings").body().contains("<title>申請アプリ</title>")
			, get("/app/settings").body());

	}

	@Test
	@DisplayName("F-W-17 書き換えた index には ETag が付かない")
	void spaRewrittenHasNoEtag () throws Exception {

		/*
		 * 中身が毎回変わりうるので、ETag も Last-Modified も付けない。
		 * かわりに must-revalidate で毎回確かめさせる。
		 */
		HttpResponse<String> rewritten = get("/app/items/42");

		assertEquals(null, header(rewritten, "etag"), "書き換えたのに ETag が付いている");
		assertTrue(header(rewritten, "cache-control").contains("must-revalidate")
			, header(rewritten, "cache-control"));

	}

	// endregion

	// region MPA（要件 F-W-20）

	@Test
	@DisplayName("F-W-20 拡張子が無ければ <パス>/index.html を返す")
	void mpaServesIndex () throws Exception {

		assertTrue(get("/guide/").body().contains("手引き（MPA）"), get("/guide/").body());
		assertTrue(get("/guide/rules").body().contains("申請のきまり"), get("/guide/rules").body());
		assertTrue(get("/guide/rules/").body().contains("申請のきまり"), get("/guide/rules/").body());

	}

	@Test
	@DisplayName("F-W-20 MPA は SPA と違い、上へ遡らない")
	void mpaDoesNotFallBack () throws Exception {

		/*
		 * <b>ここが SPA との一番の違いである。</b>
		 *
		 * SPA は近い index.html を探して遡るので、/app/nothing は 200 になる。
		 * MPA は guide/nothing/index.html が無ければ 404 にする。
		 * 静的サイトを配るのに遡られると、<b>間違ったページが 200 で返る</b>。
		 */
		assertEquals(404, get("/guide/nothing").statusCode());

		// 同じ形のパスでも SPA なら返る
		assertEquals(200, get("/app/nothing").statusCode());

	}

	// endregion

	// region リバースプロキシ（要件 F-R-14）

	@Test
	@DisplayName("F-R-14 転送先の応答がそのまま返る")
	void proxyForwards () throws Exception {

		HttpResponse<String> response = get("/api/echo?a=1&b=2");

		assertEquals(200, response.statusCode());

		/*
		 * JSON はスラッシュを \/ と書く。
		 * そのまま contains("\"path\":\"/echo\"") と書くと、
		 * <b>出ているのに見つからない</b>（approval-ops でも踏んだ）。
		 */
		String body = response.body().replace("\\/", "/");

		assertTrue(body.contains("\"from\":\"upstream\""), body);

		// パスは /api を落とした残りが渡る
		assertTrue(body.contains("\"path\":\"/echo\""), body);

		// クエリはそのまま渡る
		assertTrue(body.contains("\"query\":\"a=1&b=2\""), body);

	}

	@Test
	@DisplayName("F-R-14 転送先に X-Forwarded-* が足される")
	void proxyAddsForwardedHeaders () throws Exception {

		String body = get("/api/echo").body();

		/*
		 * <b>転送元では付いていない。</b>プロキシが足している。
		 * 足さないと、転送先から見た相手はいつもプロキシになる。
		 */
		assertTrue(body.contains("\"x_forwarded_proto\":\"http\""), body);
		assertTrue(body.contains("\"x_real_ip\":\"127.0.0.1\""), body);
		assertTrue(body.contains("\"x_forwarded_for\":\"127.0.0.1\""), body);

		// Host は転送先向けに張り替えられる（元の Host は落ちる）
		assertTrue(body.contains("\"x_forwarded_host\":\"127.0.0.1:" + server.port() + "\""), body);
		assertTrue(body.contains("\"host\":\"127.0.0.1:" + upstream.port() + "\""), body);

	}

	@Test
	@DisplayName("F-R-14 メソッドと本文もそのまま渡る")
	void proxyForwardsBody () throws Exception {

		HttpResponse<String> response = client.send(
			HttpRequest.newBuilder(uri("/api/echo"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("kind=備品", StandardCharsets.UTF_8))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertEquals(200, response.statusCode(), response.body());
		assertTrue(response.body().contains("\"method\":\"POST\""), response.body());
		assertTrue(response.body().contains("備品"), response.body());

	}

	@Test
	@DisplayName("F-R-14 転送先が返したステータスがそのまま返る")
	void proxyKeepsStatus () throws Exception {

		assertEquals(418, get("/api/teapot").statusCode());

	}

	@Test
	@DisplayName("F-R-14 リダイレクトは追わずにそのまま返す")
	void proxyDoesNotFollowRedirect () throws Exception {

		HttpResponse<String> response = get("/api/moved");

		/*
		 * <b>追ってはいけない。</b>追うと、転送先が返した Location が
		 * クライアントに届かず、ブラウザのアドレスバーが動かない。
		 */
		assertEquals(302, response.statusCode());
		assertEquals("/echo", header(response, "location"));

	}

	@Test
	@DisplayName("プロキシと通常のルートが共存する")
	void proxyCoexists () throws Exception {

		// /api だけがプロキシ。ほかは食われない
		assertEquals(200, get("/").statusCode());
		assertEquals(200, get("/assets/app.css").statusCode());

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>jar から配れること（F-W-18）は見ていない。</b>
	 *   テストはクラスの入っているディレクトリから走るので、
	 *   ここで確かめられるのは「クラスパスから読む」ところまでである。
	 *   jar の中からも読めることは jimble-web 側のテストが見ている
	 * - <b>If-Modified-Since は見ていない。</b>実装は日付の大小ではなく
	 *   <b>文字列の一致</b>で判定するので、ここで確かめても
	 *   「同じ文字列を送り返した」しか言えない。ETag のほうが先に見られる
	 * - <b>転送に失敗したときの 502</b> は見ていない
	 *   （誰も待ち受けていないポートを用意する必要があり、
	 *   その形は ReverseProxyIntegrationTest が持っている）
	 * - <b>WebSocket がプロキシを通らないこと</b>は見ていない。
	 *   Upgrade は hop-by-hop なので落ちる、という実装事実である
	 * - <b>assets.max_age を変えたときに Cache-Control が変わること</b>は見ていない。
	 *   設定を読んでいるかの確認になってしまう
	 */

	// endregion

	// region 道具

	/**
	 * URI を作る
	 *
	 * @param path	パス
	 * @return	URI
	 */
	private static URI uri (String path) {

		return URI.create("http://127.0.0.1:" + server.port() + path);

	}

	/**
	 * GET する
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return client.send(
			HttpRequest.newBuilder(uri(path)).GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * ヘッダを1つ取る
	 *
	 * @param response	応答
	 * @param name		名前（小文字）
	 * @return	値（無ければ null）
	 */
	private static String header (HttpResponse<String> response, String name) {

		return response.headers().firstValue(name).orElse(null);

	}

	// endregion

}
