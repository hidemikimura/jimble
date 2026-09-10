package approval.ops;

import io.jimble.core.lifecycle.Shutdown;
import io.jimble.core.trace.RecordingTracer;
import io.jimble.core.trace.Tracing;
import io.jimble.util.conf.Conf;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-ops）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * <b>DB は要らない。</b>{@code @Tag("db")} を付けていないので、
 * DB の無い CI ジョブ（{@code build}）で走る。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:approval-ops:test
 * </pre>
 */
class ApprovalOpsIntegrationTest {

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		server = JimbleServer.start(new OpsApp(), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		Shutdown.reset();
		Tracing.off();

	}

	@AfterEach
	void restore () {

		/*
		 * <b>止め始めた印もトレースもプロセス全体で共有している。</b>
		 * 戻さないと、あとのテストが理由なく落ちる
		 */
		Shutdown.reset();
		Tracing.off();

	}

	// region ヘルスチェック（要件 NF-O-03 / D-91）

	@Test
	@DisplayName("NF-O-03 ふだんは 200、HEAD でも答える")
	void healthCheck () throws Exception {

		assertEquals(200, get("/health_check").statusCode());
		assertEquals(200, send("HEAD", "/health_check").statusCode());

	}

	@Test
	@DisplayName("D-91 止め始めたら、ヘルスチェックだけ先に落ちる")
	void healthCheckFallsFirst () throws Exception {

		Shutdown.markStopping();

		assertEquals(503, get("/health_check").statusCode());

		/*
		 * <b>ここが要点である。</b>ヘルスチェックは落ちているのに、
		 * <b>ふつうのリクエストはまだ通る</b>——
		 * そのあいだにロードバランサが新しい人を送らなくなり、
		 * 処理中のものを返しきってから止まれる
		 */
		assertEquals(200, get("/").statusCode(), "処理中のリクエストまで断っています");

	}

	// endregion

	// region メトリクス（要件 NF-O-04）

	@Test
	@DisplayName("NF-O-04 数えた結果が JSON で読める")
	void metrics () throws Exception {

		Metrics.reset();

		/*
		 * <b>reset は登録した gauge ごと消す。</b>登録し直さないと、
		 * このテストのあとは gauge が1つも出なくなる
		 */
		OpsApp.registerGauges();

		get("/");
		get("/");

		String body = get("/metrics").body();

		assertTrue(body.contains("\"counter\""), body);
		assertTrue(body.contains("http.request"), body);

		// 自分で登録した gauge も出る
		assertTrue(body.contains(OpsApp.METRIC_UPTIME), body);

	}

	@Test
	@DisplayName("NF-O-04 レイテンシの名前は生のパスではない")
	void metricsUseRoutePattern () throws Exception {

		Metrics.reset();
		OpsApp.registerGauges();

		get("/limited");

		/*
		 * <b>JSON はスラッシュを {@code \/} と書く。</b>
		 * そのまま {@code contains("http.GET /limited")} と書くと、
		 * <b>出ているのに見つからない</b>
		 */
		String body = get("/metrics").body().replace("\\/", "/");

		/*
		 * <b>生のパスを名前にすると、名前がいくらでも増える。</b>
		 * /aaa /aab … と叩かれるだけで上限に達し、
		 * <b>そのあとは本物のルートも数えられなくなる</b>（D-124）
		 */
		assertTrue(body.contains("http.GET /limited"), body);

	}

	// endregion

	// region トレース（要件 NF-O-05）

	@Test
	@DisplayName("NF-O-05 登録していなければ何も出ない")
	void tracingIsOffByDefault () throws Exception {

		String body = get("/slow").body();

		assertTrue(body.contains("\"enabled\":false"), body);

	}

	@Test
	@DisplayName("NF-O-05 登録すると、リクエストとその中の区間が記録される")
	void tracingRecordsSpans () throws Exception {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		get("/slow");

		/*
		 * <b>リクエストそのものの区間は、ルートの型で名前が付く。</b>
		 * 生のパスにすると、トレースを見る道具の側で種類が無限に増える（D-124）
		 */
		assertNotNull(tracer.find("GET /slow"), tracer.spans().toString());

		// ハンドラの中で開いた区間
		assertNotNull(tracer.find("在庫を確かめる"), tracer.spans().toString());

	}

	// endregion

	// region 流量制限（要件 F-R-15）

	@Test
	@DisplayName("F-R-15 上限を超えたら 429 と Retry-After が返る")
	void rateLimit () throws Exception {

		for (int i = 0; i < OpsApp.LIMIT; i++) {
			assertEquals(200, get("/limited").statusCode(), "%d 回目で断られました".formatted(i + 1));
		}

		HttpResponse<String> over = get("/limited");

		assertEquals(429, over.statusCode(), over.body());

		/*
		 * <b>いつ試せばいいかを返す。</b>返さないと、
		 * 断られた側は<b>すぐ叩き直すしかない</b>
		 */
		assertTrue(over.headers().firstValue("retry-after").isPresent()
			, over.headers().map().toString());

		assertEquals(String.valueOf(OpsApp.LIMIT)
			, over.headers().firstValue("x-ratelimit-limit").orElse(""));

	}

	@Test
	@DisplayName("F-R-15 制限は付けたルートだけに効く")
	void rateLimitIsPerRoute () throws Exception {

		// 上のテストで使い切っていても、別のルートは通る
		for (int i = 0; i < OpsApp.LIMIT + 2; i++) {
			assertEquals(200, get("/health_check").statusCode());
		}

	}

	// endregion

	// region Bot（要件 F-W-14 / F-W-16）

	@Test
	@DisplayName("F-W-16 UserAgent から端末と OS が読める")
	void userAgent () throws Exception {

		String body = send("GET", "/", "User-Agent"
			, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15").body();

		assertTrue(body.contains("Mobile"), body);
		assertTrue(body.contains("\"bot\":false"), body);

	}

	@Test
	@DisplayName("F-W-14 ボットは 403（人は通る）")
	void botIsBlocked () throws Exception {

		assertEquals(403
			, send("GET", "/strict", "User-Agent", "Googlebot/2.1").statusCode());

		assertEquals(200
			, send("GET", "/strict", "User-Agent"
				, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
					+ " (KHTML, like Gecko) Chrome/140.0 Safari/537.36").statusCode());

	}

	@Test
	@DisplayName("F-W-14 弾くのは宣言したところだけ")
	void botIsAllowedElsewhere () throws Exception {

		HttpResponse<String> response = send("GET", "/", "User-Agent", "Googlebot/2.1");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"bot\":true"), response.body());

	}

	// endregion

	// region 内部呼び出し（要件 F-W-27）

	@Test
	@DisplayName("F-W-27 HTTP を通さずに別のルートを呼べる")
	void internalCall () throws Exception {

		String body = get("/internal").body();

		assertTrue(body.contains("\"called\":200"), body);
		assertTrue(body.contains("\"has_counter\":true"), body);

	}

	@Test
	@DisplayName("F-W-27 内部呼び出しは1リクエストとして二重に数えない")
	void internalCallIsNotCountedTwice () throws Exception {

		Metrics.reset();
		OpsApp.registerGauges();

		get("/internal");

		String body = get("/metrics").body();

		/*
		 * <b>1 である。</b>
		 *
		 * {@code /internal} の1回だけが数えられている。中で呼んだ {@code /metrics} は
		 * <b>内部呼び出しなので数えない</b>——数えると1リクエストが2回になる。
		 *
		 * いま叩いている {@code /metrics} 自身も入らない。
		 * <b>メトリクスを記録するのはリクエストを閉じるときで、
		 * 中身を返すのはその前</b>だからである
		 */
		assertTrue(body.contains("\"http.request\":1"), body);

	}

	// endregion

	// region その他

	@Test
	@DisplayName("F-X-07 動的な応答には既定で Cache-Control: no-store が付く")
	void noStoreByDefault () throws Exception {

		/*
		 * <b>ここを固定しているのはこのサンプルだけである。</b>
		 * 付かないこと（静的配信）のテストはあったが、
		 * <b>付くことのテストはどこにも無かった</b>
		 */
		assertEquals("no-store"
			, get("/").headers().firstValue("cache-control").orElse(null));

	}

	@Test
	@DisplayName("F-R-13 到達不能なルートがあれば起動しない設定になっている")
	void strictRoutes () {

		/*
		 * <b>設定が効いていることだけを見る。</b>
		 * 実際に落とすには到達不能なルートを置く必要があるが、
		 * <b>置いたらこのサンプル自身が起動しなくなる</b>ので、ここでは設定だけ確かめる。
		 * 落ちることは UnreachableRoutesTest が見ている
		 */
		assertTrue(io.jimble.web.server.ServerConf.strictRoutes()
			, "strict_routes が効いていません");

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>OTLP で実際に送るところは見ていない。</b>collector が要る。
	 *   送れることは jimble-otel の OtelExportTest が見ている
	 * - <b>流量制限の redis / db ストア</b>は見ていない（このサンプルは memory）
	 * - <b>アクセスログが access と access.bot に分かれること</b>は見ていない。
	 *   ロガー名を見るには出力を捕まえる必要があり、それは AccessLogTest の仕事
	 */

	// endregion

	/**
	 * GET する
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return send("GET", path);

	}

	/**
	 * 叩く
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param headers	ヘッダ（名前・値の並び）
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> send (String method, String path, String...headers)
		throws Exception {

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.method(method, HttpRequest.BodyPublishers.noBody());

		for (int index = 0; index + 1 < headers.length; index += 2) {
			builder.header(headers[index], headers[index + 1]);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

}
