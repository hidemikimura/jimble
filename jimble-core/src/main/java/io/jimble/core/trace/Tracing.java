package io.jimble.core.trace;

/**
 * 分散トレーシング（要件 NF-O-05）
 *
 * <p>
 * <b>出す先を登録するまでは何もしない。</b>登録が無いあいだ、ここを通る費用は
 * <b>静的な変数を1つ読んで分岐するだけ</b>である（実測で1回あたり 0 byte・約 1ns）。
 * </p>
 *
 * <h2>使い方</h2>
 * <p>
 * アプリの起動時に1行書く。<b>フレームワークが勝手に始めることはしない</b>
 * （起動時に何かを走査しない＝NF-P-03、黙って外へ繋がない＝原則5）。
 * </p>
 *
 * <pre>{@code
 * // build.gradle.kts に implementation("io.jimble:jimble-otel:...") を足したうえで
 * JimbleOtel.install("my-app", "http://localhost:4318");
 * }</pre>
 *
 * <h2>勝手に付くスパン</h2>
 * <ul>
 *   <li><b>HTTP リクエスト</b>（{@code server}）— 名前はマッチしたルートの型</li>
 *   <li><b>SQL</b>（{@code client}）— 1文につき1つ</li>
 *   <li><b>MQ</b>（{@code producer} / {@code consumer}）— 積んだところと処理したところが繋がる</li>
 *   <li><b>バッチ</b>（{@code internal}）— 1回の実行につき1つ</li>
 * </ul>
 *
 * <h2>外へ繋ぐ</h2>
 * <p>
 * 入ってきた {@code traceparent} は自動で引き継ぐ。jimble の HTTP クライアントから出るときも
 * 自動で付く。それ以外の経路で外へ渡すときは {@link #traceparent()} を使うこと。
 * </p>
 */
public final class Tracing {

	/**
	 * 出す先
	 *
	 * <p>
	 * <b>volatile にしてある。</b>登録するのは起動時の1回だが、
	 * 見るのは全リクエストのスレッドである。
	 * </p>
	 */
	private static volatile Tracer tracer;

	private Tracing () {
	}

	// region 登録

	/**
	 * 出す先を登録する
	 *
	 * <p>
	 * <b>アプリが起動時に呼ぶ。</b>2回呼ぶと、あとのほうが残る。
	 * </p>
	 *
	 * @param tracer 出す先。null なら無効にする
	 */
	public static void use (Tracer tracer) {

		Tracing.tracer = tracer;

	}

	/**
	 * 無効にする
	 */
	public static void off () {

		Tracing.tracer = null;

	}

	/**
	 * 出す先が登録されているか
	 *
	 * <p>
	 * <b>名前を組み立てるのに費用がかかるところでは、先にこれを見ること。</b>
	 * {@link #start} は無効なら何もしないが、<b>引数を作る費用は呼ぶ側が払っている</b>。
	 * </p>
	 *
	 * @return 登録されていれば true
	 */
	public static boolean enabled () {

		return tracer != null;

	}

	// endregion

	// region 始める

	/**
	 * スパンを始める
	 *
	 * @param name	名前
	 * @param kind	種類
	 * @return スパン。無効なら {@link Span#NOOP}
	 */
	public static Span start (String name, SpanKind kind) {

		return start(name, kind, null, 0);

	}

	/**
	 * 外から来たトレースに繋いでスパンを始める
	 *
	 * @param name			名前
	 * @param traceparent	{@code traceparent} ヘッダの値。無ければ null
	 * @return スパン。無効なら {@link Span#NOOP}
	 */
	public static Span startServer (String name, String traceparent) {

		return start(name, SpanKind.server, traceparent, 0);

	}

	/**
	 * すでに終わったことを、かかった時間つきで記録する
	 *
	 * <p>
	 * SQL のように<b>測り終わってから記録するもの</b>に使う。
	 * 返ってきたスパンは、付帯情報を足したらすぐ閉じること。
	 * </p>
	 *
	 * @param name			名前
	 * @param kind			種類
	 * @param elapsedNanos	かかった時間（ナノ秒）
	 * @return スパン。無効なら {@link Span#NOOP}
	 */
	public static Span past (String name, SpanKind kind, long elapsedNanos) {

		return start(name, kind, null, elapsedNanos);

	}

	/**
	 * スパンを始める
	 *
	 * @param name			名前
	 * @param kind			種類
	 * @param traceparent	外から来たトレース。無ければ null
	 * @param elapsedNanos	すでに経過している時間（ナノ秒）
	 * @return スパン。無効なら {@link Span#NOOP}
	 */
	public static Span start (String name, SpanKind kind, String traceparent, long elapsedNanos) {

		Tracer current = tracer;

		if (current == null) {
			return Span.NOOP;
		}

		try {

			Span span = current.start(name, kind, traceparent, elapsedNanos);

			return span == null ? Span.NOOP : span;

		} catch (Exception ex) {

			/*
			 * <b>トレースの都合で処理を落とさない。</b>
			 * ここで投げると、出す先が詰まっただけでリクエストが 500 になる
			 */
			return Span.NOOP;

		}

	}

	// endregion

	// region いまのスパン

	/**
	 * いまのスパンを指す {@code traceparent}
	 *
	 * <p>
	 * 外へ HTTP を投げるときにヘッダへ入れる。jimble の HTTP クライアントは自動で入れるので、
	 * <b>自分で書く必要があるのは別の経路で外へ渡すときだけ</b>である。
	 * </p>
	 *
	 * @return {@code traceparent}。トレースが無効か、いまスパンの中でなければ null
	 */
	public static String traceparent () {

		return current().traceparent();

	}

	/**
	 * いまのトレース ID
	 *
	 * @return トレース ID。無ければ null
	 */
	public static String traceId () {

		return current().traceId();

	}

	/**
	 * いまのスパン ID
	 *
	 * @return スパン ID。無ければ null
	 */
	public static String spanId () {

		return current().spanId();

	}

	/**
	 * いまのスパン
	 *
	 * <p>
	 * <b>「いま」を覚えているのは実装側である。</b>jimble は持たない
	 * （OpenTelemetry がスレッドごとに持っているものをそのまま使う）。
	 * </p>
	 *
	 * @return いまのスパン。無ければ {@link Span#NOOP}
	 */
	public static Span current () {

		Tracer currentTracer = tracer;

		if (currentTracer == null) {
			return Span.NOOP;
		}

		try {

			Span span = currentTracer.current();

			return span == null ? Span.NOOP : span;

		} catch (Exception ex) {
			return Span.NOOP;
		}

	}

	// endregion

}
