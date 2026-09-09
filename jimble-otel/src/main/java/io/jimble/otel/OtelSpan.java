package io.jimble.otel;

import io.jimble.core.trace.Span;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

/**
 * OpenTelemetry のスパン（要件 NF-O-05）
 *
 * <p>
 * jimble の {@link Span} を OpenTelemetry の {@code Span} に被せたもの。
 * </p>
 *
 * <h2>閉じる順番</h2>
 * <p>
 * <b>{@code Scope} を先に閉じてからスパンを終わらせる。</b>
 * {@code Scope} は「いまのスパンはこれ」をスレッドに置いた印で、
 * <b>置いたのと同じスレッドで、置いた順の逆に外さないといけない</b>。
 * </p>
 */
final class OtelSpan implements Span {

	/** 中身 */
	private final io.opentelemetry.api.trace.Span span;

	/** 「いまのスパン」の印。さかのぼって記録するときは null */
	private final Scope scope;

	/** 終わった時刻（ナノ秒）。0 なら「閉じたとき」 */
	private final long endEpochNanos;

	/**
	 * 自分で終わらせてよいスパンか
	 *
	 * <p>
	 * <b>「いまのスパン」を引いただけのものは終わらせてはいけない。</b>
	 * ログに出すトレース ID を引くために取り出しているだけで、
	 * 閉じるのはそれを始めたところの役目である。
	 * </p>
	 */
	private final boolean owned;

	/** 二重に閉じないための印 */
	private boolean closed = false;

	/**
	 * コンストラクタ
	 *
	 * @param span			中身
	 * @param scope			「いまのスパン」の印。無ければ null
	 * @param endEpochNanos	終わった時刻（ナノ秒）。0 なら閉じたとき
	 * @param owned			自分で終わらせてよいか
	 */
	OtelSpan (io.opentelemetry.api.trace.Span span, Scope scope, long endEpochNanos, boolean owned) {

		this.span = span;
		this.scope = scope;
		this.endEpochNanos = endEpochNanos;
		this.owned = owned;

	}

	@Override
	public Span attribute (String key, String value) {

		if (value != null) {
			span.setAttribute(key, value);
		}

		return this;

	}

	@Override
	public Span attribute (String key, long value) {

		span.setAttribute(key, value);

		return this;

	}

	@Override
	public Span name (String name) {

		if (name != null) {
			span.updateName(name);
		}

		return this;

	}

	@Override
	public Span error (Throwable cause) {

		span.setStatus(StatusCode.ERROR, cause == null ? "" : String.valueOf(cause.getMessage()));

		if (cause != null) {
			span.recordException(cause);
		}

		return this;

	}

	@Override
	public String traceparent () {

		SpanContext context = span.getSpanContext();

		if (!context.isValid()) {
			return null;
		}

		/*
		 * W3C Trace Context の書き方（version-traceid-spanid-flags）。
		 *
		 * <b>OpenTelemetry の伝搬器を通さずに自分で組んでいる。</b>
		 * 形は仕様で固定されていて 55 文字しかなく、
		 * このためだけに伝搬器を持ち回るより短く済む。
		 */
		return "00-%s-%s-%s".formatted(
			context.getTraceId()
			, context.getSpanId()
			, context.getTraceFlags().asHex());

	}

	@Override
	public String traceId () {

		SpanContext context = span.getSpanContext();

		return context.isValid() ? context.getTraceId() : null;

	}

	@Override
	public String spanId () {

		SpanContext context = span.getSpanContext();

		return context.isValid() ? context.getSpanId() : null;

	}

	@Override
	public void close () {

		if (closed || !owned) {
			return;
		}

		closed = true;

		try {

			if (scope != null) {
				scope.close();
			}

			if (endEpochNanos > 0) {
				span.end(endEpochNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
			} else {
				span.end();
			}

		} catch (Exception ex) {
			// トレースの都合で処理を落とさない
		}

	}

}
