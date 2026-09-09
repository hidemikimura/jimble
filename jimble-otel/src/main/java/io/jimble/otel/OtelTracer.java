package io.jimble.otel;

import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracer;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry へトレースを出す（要件 NF-O-05）
 *
 * <p>
 * {@link JimbleOtel#install} が作って {@code Tracing} に登録する。
 * 直に作る必要は普通は無い。
 * </p>
 */
public final class OtelTracer implements Tracer {

	/** {@code traceparent} の形（version-traceid-spanid-flags） */
	private static final int TRACEPARENT_PARTS = 4;

	/** 中身 */
	private final io.opentelemetry.api.trace.Tracer tracer;

	/** 送る側（閉じるときに使う） */
	private final SdkTracerProvider provider;

	/**
	 * コンストラクタ
	 *
	 * @param provider		送る側
	 * @param instrumentation	計測している側の名前
	 */
	OtelTracer (SdkTracerProvider provider, String instrumentation) {

		this.provider = provider;
		this.tracer = provider.get(instrumentation);

	}

	@Override
	public Span start (String name, SpanKind kind, String traceparent, long elapsedNanos) {

		SpanBuilder builder = tracer.spanBuilder(name == null ? "" : name)
			.setSpanKind(toOtel(kind));

		Context parent = parent(traceparent);

		if (parent != null) {
			builder.setParent(parent);
		}

		/*
		 * すでに終わっているものは、かかった時間だけさかのぼったところから始める。
		 * SQL のように<b>測り終わってから記録するもの</b>がこれである。
		 */
		long endEpochNanos = 0;

		if (elapsedNanos > 0) {

			endEpochNanos = epochNanos();

			builder.setStartTimestamp(endEpochNanos - elapsedNanos, TimeUnit.NANOSECONDS);

		}

		io.opentelemetry.api.trace.Span span = builder.startSpan();

		/*
		 * さかのぼって記録するものは「いまのスパン」にしない。
		 *
		 * <b>置いても答えは変わらない</b>（すぐ閉じるので、次のスパンの親になることはない）。
		 * 置かないのは費用のためである：SQL 1文ごとに ThreadLocal を出し入れすることになり、
		 * <b>jimble でいちばん回数の多い経路</b>がそこである。
		 */
		Scope scope = endEpochNanos > 0 ? null : span.makeCurrent();

		return new OtelSpan(span, scope, endEpochNanos, true);

	}

	@Override
	public Span current () {

		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();

		if (!span.getSpanContext().isValid()) {
			return Span.NOOP;
		}

		// 引いただけなので、閉じてはいけない
		return new OtelSpan(span, null, 0, false);

	}

	/**
	 * ためている分をいますぐ送る
	 *
	 * <p>
	 * ふだんは自動で送られるので呼ぶ必要は無い。<b>すぐ終わる処理</b>
	 * （バッチ1回だけ動かす、テストで中身を確かめる）では、
	 * 送る前に JVM が終わってしまうので呼ぶこと。
	 * </p>
	 *
	 * @param timeoutMillis	待つ上限
	 */
	public void flush (long timeoutMillis) {

		try {
			provider.forceFlush().join(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			// 落とさない
		}

	}

	/**
	 * 送り切ってから閉じる
	 *
	 * @param timeoutMillis	待つ上限
	 */
	public void close (long timeoutMillis) {

		try {
			provider.shutdown().join(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			// 落とさない
		}

	}

	/**
	 * 親を決める
	 *
	 * @param traceparent	{@code traceparent} ヘッダの値。無ければ null
	 * @return 親。{@code traceparent} が無いか読めなければ null（＝いまのスパンの子になる）
	 */
	private static Context parent (String traceparent) {

		SpanContext remote = parse(traceparent);

		if (remote == null) {
			return null;
		}

		return Context.root().with(io.opentelemetry.api.trace.Span.wrap(remote));

	}

	/**
	 * {@code traceparent} を読む（W3C Trace Context）
	 *
	 * <p>
	 * {@code 00-<32桁>-<16桁>-<2桁>}。<b>読めなければ null を返して、黙って捨てる。</b>
	 * 相手が送ってきたものが壊れていただけでこちらのリクエストを落とすのは筋が違う。
	 * </p>
	 *
	 * @param traceparent 値
	 * @return 読めた親。読めなければ null
	 */
	private static SpanContext parse (String traceparent) {

		if (traceparent == null || traceparent.isBlank()) {
			return null;
		}

		String[] parts = traceparent.trim().split("-");

		if (parts.length != TRACEPARENT_PARTS) {
			return null;
		}

		try {

			SpanContext context = SpanContext.createFromRemoteParent(
				parts[1]
				, parts[2]
				, TraceFlags.fromHex(parts[3], 0)
				, TraceState.getDefault());

			/*
			 * <b>読めた形かを確かめるのは念のためである。</b>
			 * 中身が全部 0 のような無効なものを渡しても、
			 * OpenTelemetry 側が「親なし」として扱うので答えは変わらない。
			 * ここで弾いておくと、無駄な入れ物を1つ作らずに済む。
			 */
			return context.isValid() ? context : null;

		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * いまの時刻（エポックからのナノ秒）
	 *
	 * @return ナノ秒
	 */
	private static long epochNanos () {

		java.time.Instant now = java.time.Instant.now();

		return now.getEpochSecond() * 1_000_000_000L + now.getNano();

	}

	/**
	 * 種類を移し替える
	 *
	 * @param kind 種類
	 * @return OpenTelemetry の種類
	 */
	private static io.opentelemetry.api.trace.SpanKind toOtel (SpanKind kind) {

		if (kind == null) {
			return io.opentelemetry.api.trace.SpanKind.INTERNAL;
		}

		return switch (kind) {
			case server -> io.opentelemetry.api.trace.SpanKind.SERVER;
			case client -> io.opentelemetry.api.trace.SpanKind.CLIENT;
			case producer -> io.opentelemetry.api.trace.SpanKind.PRODUCER;
			case consumer -> io.opentelemetry.api.trace.SpanKind.CONSUMER;
			case internal -> io.opentelemetry.api.trace.SpanKind.INTERNAL;
		};

	}

}
