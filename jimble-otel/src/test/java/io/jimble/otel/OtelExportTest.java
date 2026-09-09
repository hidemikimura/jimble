package io.jimble.otel;

import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OTLP へ本当に届くか（要件 NF-O-05）
 *
 * <p>
 * <b>コンパイルが通るだけでは足りない。</b>このモジュールは OpenTelemetry の依存を
 * だいぶ削ってある（{@code opentelemetry-sdk-metrics} と {@code opentelemetry-sdk-logs} を外し、
 * 送信器を okhttp から JDK の HttpClient に差し替えて 4.2MB → 0.88MB にした）。
 * <b>削りすぎていれば、送ろうとした瞬間に {@code NoClassDefFoundError} になる</b>——
 * そしてそれはコンパイルでは分からない。
 * </p>
 *
 * <p>
 * ここでは OTLP を受ける口を立てて、<b>実際に届いた中身</b>を見る。
 * </p>
 */
class OtelExportTest {

	/** 受け取ったもの */
	private final List<byte[]> received = new CopyOnWriteArrayList<>();

	/** 受け口 */
	private HttpServer server;

	/** 出す先 */
	private OtelTracer tracer;

	@AfterEach
	void stop () {

		Tracing.off();

		if (tracer != null) {
			tracer.close(3000);
			tracer = null;
		}

		if (server != null) {
			server.stop(0);
			server = null;
		}

		received.clear();

	}

	// region 届く

	@Test
	@DisplayName("スパンが OTLP の口に届く")
	void exported () throws Exception {

		CountDownLatch arrived = start();

		try (Span span = Tracing.start("テストの区間", SpanKind.server)) {
			span.attribute("http.route", "/posts/{id}");
			span.attribute("http.response.status_code", 200);
		}

		tracer.flush(5000);

		assertTrue(arrived.await(10, TimeUnit.SECONDS), "OTLP の口に何も届いていない");

		String body = body();

		assertTrue(body.contains("テストの区間"), "スパンの名前が入っていない");
		assertTrue(body.contains("/posts/{id}"), "付帯情報が入っていない");
		assertTrue(body.contains("jimble-test"), "サービス名が入っていない");

	}

	@Test
	@DisplayName("親子になる")
	void nested () throws Exception {

		CountDownLatch arrived = start();

		try (Span parent = Tracing.start("親", SpanKind.server)) {

			assertNotNull(parent.traceId());

			try (Span child = Tracing.start("子", SpanKind.client)) {

				// 同じトレースの中で、別の区間になる
				assertEquals(parent.traceId(), child.traceId());
				assertFalse(parent.spanId().equals(child.spanId()));

			}

		}

		tracer.flush(5000);

		assertTrue(arrived.await(10, TimeUnit.SECONDS));

		String body = body();

		assertTrue(body.contains("親") && body.contains("子"), body);

	}

	@Test
	@DisplayName("外から来たトレースに繋がる")
	void remoteParent () throws Exception {

		start();

		String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
		String traceparent = "00-%s-00f067aa0ba902b7-01".formatted(traceId);

		try (Span span = Tracing.startServer("受けた", traceparent)) {

			// <b>相手のトレース ID をそのまま引き継ぐ</b>。これが繋がりの正体である
			assertEquals(traceId, span.traceId());

			// 自分の区間の ID は別
			assertFalse("00f067aa0ba902b7".equals(span.spanId()));

		}

	}

	@Test
	@DisplayName("壊れた traceparent は黙って捨てる")
	void brokenTraceparent () throws Exception {

		start();

		/*
		 * <b>相手が送ってきたものが壊れていただけで、こちらを落とさない。</b>
		 * 新しいトレースとして始める
		 */
		for (String broken : new String[]{
			"", "  ", "こわれている", "00-xxx-yyy-01"
			, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7"          // 足りない
			, "00-00000000000000000000000000000000-00f067aa0ba902b7-01"       // 全部 0 は無効
		}) {

			try (Span span = Tracing.startServer("壊れたのを受けた", broken)) {
				assertNotNull(span.traceId(), broken);
				assertFalse("4bf92f3577b34da6a3ce929d0e0e4736".equals(span.traceId()), broken);
			}

		}

	}

	@Test
	@DisplayName("かかった時間を指定して、さかのぼって記録できる")
	void past () throws Exception {

		CountDownLatch arrived = start();

		// SQL のように「測り終わってから記録する」もの
		try (Span span = Tracing.past("SELECT post", SpanKind.client, 123_000_000L)) {
			span.attribute("db.statement", "SELECT * FROM post WHERE id = ?");
		}

		tracer.flush(5000);

		assertTrue(arrived.await(10, TimeUnit.SECONDS));

		String body = body();

		assertTrue(body.contains("SELECT post"), body);
		assertTrue(body.contains("SELECT * FROM post WHERE id = ?"), "SQL の全文が入っていない");

	}

	// endregion

	// region いまのスパン

	@Test
	@DisplayName("いまのスパンを引ける（ログに出すため）")
	void current () throws Exception {

		start();

		assertNull(Tracing.traceId(), "スパンの外なのに引けている");

		try (Span span = Tracing.start("いま", SpanKind.internal)) {

			assertEquals(span.traceId(), Tracing.traceId());
			assertEquals(span.spanId(), Tracing.spanId());
			assertEquals(span.traceparent(), Tracing.traceparent());

		}

		assertNull(Tracing.traceId(), "抜けたのに残っている");

	}

	@Test
	@DisplayName("引いただけのスパンを閉じても、本物は終わらない")
	void currentIsNotClosed () throws Exception {

		CountDownLatch arrived = start();

		/*
		 * <b>ログを1行出すたびに Tracing.current() が呼ばれる。</b>
		 * それで本物が終わってしまうと、
		 * <b>そのあとに付け直した名前も、足した付帯情報も、全部落ちる</b>
		 * （HTTP のスパンは、ルーティングのあとに名前を付け直している）。
		 */
		try (Span span = Tracing.start("仮の名前", SpanKind.server)) {

			Tracing.current().close();
			Tracing.current().close();

			assertEquals(span.traceId(), Tracing.traceId(), "引いただけで閉じてしまっている");

			span.name("本当の名前");
			span.attribute("http.route", "/posts/{id}");

		}

		tracer.flush(5000);

		assertTrue(arrived.await(10, TimeUnit.SECONDS));

		String body = body();

		assertTrue(body.contains("本当の名前"), "付け直した名前が届いていない: " + body);
		assertTrue(body.contains("/posts/{id}"), "あとから足した付帯情報が届いていない");
		assertFalse(body.contains("仮の名前"), "仮の名前のまま送られている");

	}

	@Test
	@DisplayName("traceparent は W3C の形")
	void traceparentFormat () throws Exception {

		start();

		try (Span span = Tracing.start("形", SpanKind.internal)) {

			String traceparent = span.traceparent();

			assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"), traceparent);

			// 往復する
			try (Span next = Tracing.startServer("次", traceparent)) {
				assertEquals(span.traceId(), next.traceId());
			}

		}

	}

	// endregion

	// region 道具

	/**
	 * OTLP を受ける口を立てて、トレースを出し始める
	 *
	 * @return 1件届いたら開く掛け金
	 * @throws IOException 例外
	 */
	private CountDownLatch start () throws IOException {

		CountDownLatch arrived = new CountDownLatch(1);

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

		server.createContext("/v1/traces", exchange -> {

			try (InputStream in = exchange.getRequestBody()) {
				received.add(in.readAllBytes());
			}

			exchange.sendResponseHeaders(200, 0);
			exchange.close();

			arrived.countDown();

		});

		server.start();

		tracer = JimbleOtel.install("jimble-test", "http://127.0.0.1:" + server.getAddress().getPort());

		return arrived;

	}

	/**
	 * 届いたものを文字列にする
	 *
	 * <p>
	 * protobuf だが、<b>文字列はそのまま UTF-8 で入っている</b>ので、
	 * 名前や付帯情報が入っているかはこれで見分けられる
	 * （protobuf を解く道具を1つ増やすほどのことではない）。
	 * </p>
	 *
	 * @return 届いたもの
	 */
	private String body () {

		StringBuilder sb = new StringBuilder();

		for (byte[] bytes : received) {
			sb.append(new String(bytes, StandardCharsets.UTF_8));
		}

		return sb.toString();

	}

	// endregion

}
