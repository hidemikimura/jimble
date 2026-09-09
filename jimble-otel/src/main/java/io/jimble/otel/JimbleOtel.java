package io.jimble.otel;

import io.jimble.core.trace.Tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;

/**
 * OpenTelemetry へトレースを出す（要件 NF-O-05）
 *
 * <p>
 * アプリの起動時に1行書く。<b>フレームワークが勝手に始めることはしない</b>
 * （起動時に何かを走査しない＝NF-P-03、黙って外へ繋がない＝原則5）。
 * </p>
 *
 * <pre>{@code
 * public static void main (String[] args) {
 *
 *     JimbleOtel.install("my-app", "http://localhost:4318");
 *
 *     new JimbleServer(...).start();
 *
 * }
 * }</pre>
 *
 * <p>
 * これを呼んだあとは、HTTP リクエスト・SQL・MQ・バッチに自動でスパンが付く。
 * アプリの中を細かく見たいところは自分で足せる。
 * </p>
 *
 * <pre>{@code
 * try (Span span = Tracing.start("画像の変換", SpanKind.internal)) {
 *     span.attribute("file", name);
 *     convert(file);
 * }
 * }</pre>
 *
 * <h2>依存</h2>
 * <p>
 * このモジュールを足すと、実行時クラスパスが<b>約 0.9MB</b> 増える。
 * 足さないアプリは1 byte も増えない（{@code jimble-core} が持っているのは口だけである）。
 * </p>
 *
 * <h2>送り先</h2>
 * <p>
 * OTLP の HTTP（{@code /v1/traces}）へ protobuf で送る。
 * 相手は OpenTelemetry Collector でも、Jaeger でも、Grafana Tempo でも、
 * OTLP を受けるものなら何でもよい。
 * </p>
 */
public final class JimbleOtel {

	/** 送る間隔の既定 */
	private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

	/** 終わるときに送り切るのを待つ上限（ミリ秒） */
	private static final long SHUTDOWN_TIMEOUT_MILLIS = 10_000;

	/** 計測している側の名前（トレースを見る道具に出る） */
	private static final String INSTRUMENTATION = "io.jimble";

	/** 動いているもの（止めるときに使う） */
	private static volatile OtelTracer running;

	private JimbleOtel () {
	}

	/**
	 * トレースを出し始める
	 *
	 * @param serviceName	サービス名（トレースを見る道具でこの名前にまとまる）
	 * @param endpoint		OTLP の宛先（{@code http://localhost:4318}）。
	 *						末尾の {@code /v1/traces} は付けても付けなくてよい
	 * @return 出す先
	 */
	public static OtelTracer install (String serviceName, String endpoint) {

		return install(serviceName, endpoint, 1.0d);

	}

	/**
	 * トレースを出し始める（一部だけ拾う）
	 *
	 * <p>
	 * <b>全部拾うと、流量の多いアプリでは送る先が持たない。</b>
	 * {@code ratio} は 0.0〜1.0 で、0.1 なら 10 本に1本を拾う。
	 * <b>拾うかどうかはトレース単位で決まる</b>ので、
	 * 1本のトレースの途中だけ欠けることはない。
	 * </p>
	 *
	 * @param serviceName	サービス名
	 * @param endpoint		OTLP の宛先
	 * @param ratio			拾う割合（0.0〜1.0）
	 * @return 出す先
	 */
	public static OtelTracer install (String serviceName, String endpoint, double ratio) {

		return install(serviceName, OtlpHttpSpanExporter.builder()
			.setEndpoint(tracesEndpoint(endpoint))
			.build(), ratio);

	}

	/**
	 * 出す先を自分で決めてトレースを出し始める
	 *
	 * <p>
	 * OTLP 以外へ出したいとき、テストで中身を見たいときに使う。
	 * </p>
	 *
	 * @param serviceName	サービス名
	 * @param exporter		出す先
	 * @param ratio			拾う割合（0.0〜1.0）
	 * @return 出す先
	 */
	public static OtelTracer install (String serviceName, SpanExporter exporter, double ratio) {

		SdkTracerProvider provider = SdkTracerProvider.builder()
			.setResource(Resource.getDefault().merge(Resource.create(
				Attributes.builder().put("service.name", serviceName).build())))
			.setSampler(ratio >= 1.0d
				? Sampler.parentBased(Sampler.alwaysOn())
				: Sampler.parentBased(Sampler.traceIdRatioBased(ratio)))
			/*
			 * <b>ためてから送る。</b>1スパンごとに HTTP を投げると、
			 * トレースを入れたせいで遅くなる。
			 */
			.addSpanProcessor(BatchSpanProcessor.builder(exporter)
				.setScheduleDelay(DEFAULT_INTERVAL)
				.build())
			.build();

		OtelTracer tracer = new OtelTracer(provider, INSTRUMENTATION);

		running = tracer;

		Tracing.use(tracer);

		/*
		 * <b>終わるときに送り切る。</b>ためている途中で JVM が落ちると、
		 * <b>いちばん見たい「落ちる直前」のトレースが消える</b>。
		 */
		Runtime.getRuntime().addShutdownHook(new Thread(JimbleOtel::uninstall, "jimble-otel-shutdown"));

		return tracer;

	}

	/**
	 * 送り切って止める
	 */
	public static void uninstall () {

		OtelTracer tracer = running;

		if (tracer == null) {
			return;
		}

		running = null;

		Tracing.off();

		tracer.close(SHUTDOWN_TIMEOUT_MILLIS);

	}

	/**
	 * 宛先を整える
	 *
	 * <p>
	 * <b>{@code /v1/traces} を付け忘れても動くようにする。</b>
	 * 付け忘れると 404 が返るだけで、<b>ログにも出ずに何も届かない</b>。
	 * </p>
	 *
	 * @param endpoint 宛先
	 * @return 整えた宛先
	 */
	private static String tracesEndpoint (String endpoint) {

		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalArgumentException("OTLP の宛先がありません（http://localhost:4318 など）");
		}

		String value = endpoint.trim();

		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

		return value.endsWith("/v1/traces") ? value : value + "/v1/traces";

	}

}
