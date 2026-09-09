package io.jimble.core.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 記録するだけの出す先（<b>テストのためのもの</b>）
 *
 * <p>
 * 外へは何も送らず、何が記録されたかを覚えておくだけ。
 * {@code Log.sink(...)} と同じ立て付けで、<b>アプリのテストからも使える</b>ように
 * 本体側に置いてある。
 * </p>
 *
 * <pre>{@code
 * RecordingTracer tracer = new RecordingTracer();
 * Tracing.use(tracer);
 *
 * // ... テストしたい処理 ...
 *
 * assertEquals("GET /posts/{id}", tracer.spans().get(0).name());
 * }</pre>
 *
 * <p>
 * <b>本番で登録しないこと。</b>記録は増える一方で、消えない。
 * </p>
 */
public final class RecordingTracer implements Tracer {

	/** 記録 */
	private final List<Recorded> spans = new ArrayList<>();

	/** いま開いているもの（入れ子を追う） */
	private final List<Recorded> open = new ArrayList<>();

	/** ID を配る */
	private final AtomicLong sequence = new AtomicLong();

	/** トレース ID */
	private volatile String traceId = "trace-1";

	@Override
	public synchronized Span start (String name, SpanKind kind, String traceparent, long elapsedNanos) {

		if (traceparent != null && !traceparent.isBlank()) {
			traceId = traceparent;
		}

		Recorded recorded = new Recorded(this, name, kind, elapsedNanos
			, traceId, "span-" + sequence.incrementAndGet(), peek());

		spans.add(recorded);

		/*
		 * さかのぼって記録するものは「いま」にしない
		 * （OpenTelemetry の実装と同じ振る舞いにしてある）
		 */
		if (elapsedNanos <= 0) {
			open.add(recorded);
			recorded.wasCurrent = true;
		}

		return recorded;

	}

	@Override
	public synchronized Span current () {

		Recorded recorded = peek();

		return recorded == null ? Span.NOOP : new View(recorded);

	}

	/**
	 * 記録
	 *
	 * @return 記録（始めた順）
	 */
	public synchronized List<Recorded> spans () {

		return List.copyOf(spans);

	}

	/**
	 * 名前で1つ引く
	 *
	 * @param name 名前
	 * @return 記録。無ければ null
	 */
	public synchronized Recorded find (String name) {

		for (Recorded recorded : spans) {
			if (name.equals(recorded.name)) {
				return recorded;
			}
		}

		return null;

	}

	/**
	 * 全部消す
	 */
	public synchronized void reset () {

		spans.clear();
		open.clear();

	}

	/**
	 * いま開いているもの
	 *
	 * @return いちばん内側。無ければ null
	 */
	private Recorded peek () {

		return open.isEmpty() ? null : open.get(open.size() - 1);

	}

	/**
	 * 閉じられたことを受ける
	 *
	 * @param recorded 記録
	 */
	private synchronized void closed (Recorded recorded) {

		open.remove(recorded);

	}

	/**
	 * 記録された1区間
	 */
	public static final class Recorded implements Span {

		private final RecordingTracer owner;
		private final SpanKind kind;
		private final long elapsedNanos;
		private final String traceId;
		private final String spanId;
		private final Recorded parent;
		private final Map<String, Object> attributes = new LinkedHashMap<>();

		private String name;
		private Throwable error;
		private boolean closed;
		private boolean wasCurrent;

		/**
		 * コンストラクタ
		 *
		 * @param owner			記録先
		 * @param name			名前
		 * @param kind			種類
		 * @param elapsedNanos	すでに経過している時間
		 * @param traceId		トレース ID
		 * @param spanId		スパン ID
		 * @param parent		親。無ければ null
		 */
		private Recorded (RecordingTracer owner, String name, SpanKind kind, long elapsedNanos
			, String traceId, String spanId, Recorded parent) {

			this.owner = owner;
			this.name = name;
			this.kind = kind;
			this.elapsedNanos = elapsedNanos;
			this.traceId = traceId;
			this.spanId = spanId;
			this.parent = parent;

		}

		/** @return 名前 */
		public String name () { return name; }

		/** @return 種類 */
		public SpanKind kind () { return kind; }

		/** @return すでに経過していた時間（ナノ秒）。0 なら「いまから」始めたもの */
		public long elapsedNanos () { return elapsedNanos; }

		/** @return 親。無ければ null */
		public Recorded parent () { return parent; }

		/** @return 付帯情報 */
		public Map<String, Object> attributes () { return Map.copyOf(attributes); }

		/** @return 記録された例外。無ければ null */
		public Throwable error () { return error; }

		/** @return 閉じられていれば true */
		public boolean closed () { return closed; }

		/** @return 「いまのスパン」として置かれたなら true */
		public boolean wasCurrent () { return wasCurrent; }

		@Override
		public Span attribute (String key, String value) {

			if (value != null) {
				attributes.put(key, value);
			}

			return this;

		}

		@Override
		public Span attribute (String key, long value) {

			attributes.put(key, value);

			return this;

		}

		@Override
		public Span name (String name) {

			this.name = name;

			return this;

		}

		@Override
		public Span error (Throwable cause) {

			this.error = cause;

			return this;

		}

		@Override
		public String traceparent () {

			return "00-%s-%s-01".formatted(traceId, spanId);

		}

		@Override
		public String traceId () {

			return traceId;

		}

		@Override
		public String spanId () {

			return spanId;

		}

		@Override
		public void close () {

			closed = true;

			if (wasCurrent) {
				owner.closed(this);
			}

		}

	}

	/**
	 * 引いただけのもの（閉じない）
	 */
	private static final class View implements Span {

		private final Recorded recorded;

		/**
		 * コンストラクタ
		 *
		 * @param recorded 中身
		 */
		private View (Recorded recorded) {

			this.recorded = recorded;

		}

		@Override public Span attribute (String key, String value) { return this; }
		@Override public Span attribute (String key, long value) { return this; }
		@Override public Span name (String name) { return this; }
		@Override public Span error (Throwable cause) { return this; }
		@Override public String traceparent () { return recorded.traceparent(); }
		@Override public String traceId () { return recorded.traceId(); }
		@Override public String spanId () { return recorded.spanId(); }
		@Override public void close () { }

	}

}
