package io.jimble.otel;

import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 送られるスパンの中身（要件 NF-O-05）
 *
 * <p>
 * {@link OtelExportTest} が「OTLP の口に届くこと」を見ているのに対し、
 * ここは<b>届いたものの形</b>を見る。出す先を自前のものに差し替えて、
 * 名前・種類・親子・かかった時間・失敗の印をそれぞれ確かめる。
 * </p>
 *
 * <h2>ここで固定していないこと</h2>
 * <ul>
 *   <li><b>壊れた {@code traceparent} を自分で弾いていること</b>（弾かなくても
 *       OpenTelemetry 側が「親なし」として扱うので、答えが変わらない。
 *       自前の判定を消しても、このテストは通ってしまう）</li>
 *   <li><b>さかのぼって記録するものを「いまのスパン」にしていないこと</b>
 *       （すぐ閉じるので、置いても次のスパンの親にはならない。
 *       置かないのは SQL 1文ごとの費用を避けるためで、答えは変わらない）</li>
 * </ul>
 */
class OtelSpanShapeTest {

	/** 受け取ったもの */
	private final List<SpanData> exported = new CopyOnWriteArrayList<>();

	/** 出す先 */
	private OtelTracer tracer;

	@BeforeEach
	void install () {

		exported.clear();

		tracer = JimbleOtel.install("jimble-test", new SpanExporter() {

			@Override
			public CompletableResultCode export (Collection<SpanData> spans) {
				exported.addAll(spans);
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode flush () {
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode shutdown () {
				return CompletableResultCode.ofSuccess();
			}

		}, 1.0d);

	}

	@AfterEach
	void uninstall () {

		Tracing.off();

		if (tracer != null) {
			tracer.close(3000);
			tracer = null;
		}

	}

	// region かかった時間

	@Test
	@DisplayName("さかのぼって記録したものは、その時間ぶんの長さになる")
	void pastHasDuration () {

		/*
		 * <b>ここが崩れると、SQL のスパンが全部「長さ 0」になる。</b>
		 * トレースを見ても、どのクエリが遅いのか分からない
		 * ——それはトレースを入れた理由そのものである
		 */
		try (Span span = Tracing.past("SELECT post", SpanKind.client, 123_000_000L)) {
			span.attribute("db.statement", "SELECT * FROM post");
		}

		SpanData data = one("SELECT post");

		long duration = data.getEndEpochNanos() - data.getStartEpochNanos();

		assertEquals(123_000_000L, duration, "かかった時間になっていない: " + duration);

	}

	@Test
	@DisplayName("ふつうに始めたものは、実際にかかった時間になる")
	void normalHasDuration () throws Exception {

		try (Span span = Tracing.start("寝る", SpanKind.internal)) {
			assertNotNull(span.spanId());
			Thread.sleep(15);
		}

		SpanData data = one("寝る");

		long duration = data.getEndEpochNanos() - data.getStartEpochNanos();

		assertTrue(duration >= 15_000_000L, "短すぎる: " + duration);
		assertTrue(duration < 5_000_000_000L, "長すぎる: " + duration);

	}

	// endregion

	// region 親子・種類

	@Test
	@DisplayName("親子になる")
	void parent () {

		try (Span top = Tracing.start("親", SpanKind.server)) {
			try (Span child = Tracing.start("子", SpanKind.client)) {
				assertEquals(top.traceId(), child.traceId());
			}
		}

		SpanData parent = one("親");
		SpanData child = one("子");

		assertEquals(parent.getSpanId(), child.getParentSpanId());
		assertEquals(parent.getTraceId(), child.getTraceId());
		assertTrue(parent.getParentSpanContext().getSpanId().matches("0+"), "親に親が付いている");

	}

	@Test
	@DisplayName("さかのぼって記録したものも、いまのスパンの子になる")
	void pastIsChild () {

		try (Span request = Tracing.start("リクエスト", SpanKind.server)) {
			try (Span sql = Tracing.past("SELECT post", SpanKind.client, 1_000_000L)) {
				assertEquals(request.traceId(), sql.traceId());
			}
		}

		assertEquals(one("リクエスト").getSpanId(), one("SELECT post").getParentSpanId());

	}

	@Test
	@DisplayName("さかのぼって記録したものは、次のスパンの親にならない")
	void pastIsNotCurrent () {

		/*
		 * <b>SQL のスパンの中で SQL は動かない。</b>
		 * それを「いまのスパン」にしてしまうと、次に始まったものが
		 * <b>SQL の子</b>としてぶら下がり、トレースの絵がおかしくなる
		 */
		try (Span request = Tracing.start("リクエスト", SpanKind.server)) {

			try (Span sql = Tracing.past("SELECT post", SpanKind.client, 1_000_000L)) {
				assertEquals(request.traceId(), sql.traceId());
			}

			try (Span next = Tracing.start("次", SpanKind.internal)) {
				assertEquals(request.traceId(), next.traceId());
			}

		}

		assertEquals(one("リクエスト").getSpanId(), one("次").getParentSpanId()
			, "SQL のスパンが親になってしまっている");

	}

	@Test
	@DisplayName("種類がそのまま移る")
	void kinds () {

		for (SpanKind kind : SpanKind.values()) {
			try (Span span = Tracing.start(kind.name(), kind)) {
				assertNotNull(span.spanId());
			}
		}

		assertEquals(io.opentelemetry.api.trace.SpanKind.SERVER, one("server").getKind());
		assertEquals(io.opentelemetry.api.trace.SpanKind.CLIENT, one("client").getKind());
		assertEquals(io.opentelemetry.api.trace.SpanKind.PRODUCER, one("producer").getKind());
		assertEquals(io.opentelemetry.api.trace.SpanKind.CONSUMER, one("consumer").getKind());
		assertEquals(io.opentelemetry.api.trace.SpanKind.INTERNAL, one("internal").getKind());

	}

	// endregion

	// region 名前・付帯情報・失敗

	@Test
	@DisplayName("名前を付け直せる")
	void rename () {

		try (Span span = Tracing.start("仮", SpanKind.server)) {
			span.name("GET /posts/{id}");
		}

		assertNotNull(one("GET /posts/{id}"));

	}

	@Test
	@DisplayName("付帯情報が入る（null は入れない）")
	void attributes () {

		try (Span span = Tracing.start("付帯", SpanKind.server)) {
			span.attribute("http.route", "/posts/{id}");
			span.attribute("http.response.status_code", 200);
			span.attribute("http.route.null", null);
		}

		SpanData data = one("付帯");

		assertEquals("/posts/{id}", data.getAttributes().get(AttributeKey.stringKey("http.route")));
		assertEquals(200L, data.getAttributes().get(AttributeKey.longKey("http.response.status_code")));
		assertEquals(2, data.getAttributes().size(), data.getAttributes().toString());

	}

	@Test
	@DisplayName("失敗の印と例外が残る")
	void error () {

		try (Span span = Tracing.start("失敗", SpanKind.server)) {
			span.error(new IllegalStateException("わざと落とす"));
		}

		SpanData data = one("失敗");

		assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
		assertEquals(1, data.getEvents().size(), "例外が残っていない");

	}

	@Test
	@DisplayName("外から来たトレースの続きになる")
	void remoteParent () {

		String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
		String spanId = "00f067aa0ba902b7";

		try (Span span = Tracing.startServer("受けた", "00-%s-%s-01".formatted(traceId, spanId))) {
			assertNotNull(span.spanId());
		}

		SpanData data = one("受けた");

		assertEquals(traceId, data.getTraceId());
		assertEquals(spanId, data.getParentSpanId(), "相手のスパンの子になっていない");
		assertTrue(data.getParentSpanContext().isRemote(), "外から来たものとして扱われていない");

	}

	@Test
	@DisplayName("壊れた traceparent は新しいトレースとして始める")
	void brokenTraceparent () {

		for (String broken : new String[]{
			"こわれている"
			, "00-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx-00f067aa0ba902b7-01"
			, "00-00000000000000000000000000000000-00f067aa0ba902b7-01"
			, "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"
		}) {

			exported.clear();

			try (Span span = Tracing.start(broken, SpanKind.server, broken, 0)) {
				assertNotNull(span.spanId());
			}

			tracer.flush(3000);

			SpanData data = one(broken);

			assertNotEquals("4bf92f3577b34da6a3ce929d0e0e4736", data.getTraceId(), broken);
			assertTrue(data.getParentSpanId().matches("0+"), "壊れた親が付いている: " + broken);

		}

	}

	// endregion

	// region 道具

	/**
	 * 名前で1つ引く
	 *
	 * @param name 名前
	 * @return 送られたスパン
	 */
	private SpanData one (String name) {

		tracer.flush(3000);

		List<SpanData> found = exported.stream().filter(s -> name.equals(s.getName())).toList();

		assertEquals(1, found.size(), "「%s」が %d 件（%s）".formatted(name, found.size()
			, exported.stream().map(SpanData::getName).toList()));

		return found.get(0);

	}

	// endregion

}
