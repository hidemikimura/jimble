package io.jimble.core.trace;

/**
 * トレースの1区間
 *
 * <p>
 * <b>必ず閉じること。</b>閉じ忘れると、その区間は<b>いつまでも終わらないもの</b>として
 * 送られないまま残る。{@code try} 付きの宣言で使うこと。
 * </p>
 *
 * <pre>{@code
 * try (Span span = Tracing.start("画像の変換", SpanKind.internal)) {
 *     span.attribute("file", name);
 *     convert(file);
 * }
 * }</pre>
 *
 * <p>
 * トレースを出す先が登録されていないときは {@link #NOOP} が返る。
 * <b>null を返すことは無い</b>ので、呼ぶ側で分岐しなくてよい。
 * </p>
 */
public interface Span extends AutoCloseable {

	/**
	 * 何もしないスパン
	 *
	 * <p>
	 * トレースが無効なときはこれが返る。<b>状態を持たないので使い回してよい。</b>
	 * </p>
	 */
	Span NOOP = new Span() {

		@Override public Span attribute (String key, String value) { return this; }
		@Override public Span attribute (String key, long value) { return this; }
		@Override public Span name (String name) { return this; }
		@Override public Span error (Throwable cause) { return this; }
		@Override public String traceparent () { return null; }
		@Override public String traceId () { return null; }
		@Override public String spanId () { return null; }
		@Override public void close () { }

		@Override public String toString () { return "Span.NOOP"; }

	};

	/**
	 * 付帯情報を足す
	 *
	 * @param key	名前
	 * @param value	値。null なら足さない
	 * @return 自身
	 */
	Span attribute (String key, String value);

	/**
	 * 付帯情報を足す
	 *
	 * @param key	名前
	 * @param value	値
	 * @return 自身
	 */
	Span attribute (String key, long value);

	/**
	 * 名前を付け直す
	 *
	 * <p>
	 * <b>始めた時点では分からないことがある。</b>HTTP のリクエストは、
	 * どのルートに当たったかがルーティングのあとにしか分からない
	 * （{@code GET /posts/{id}}）。
	 * </p>
	 *
	 * @param name 名前
	 * @return 自身
	 */
	Span name (String name);

	/**
	 * 失敗したことを記録する
	 *
	 * @param cause	原因
	 * @return 自身
	 */
	Span error (Throwable cause);

	/**
	 * このスパンを指す {@code traceparent}（W3C Trace Context）
	 *
	 * <p>
	 * 外へ HTTP を投げるときにヘッダへ入れると、<b>相手側のトレースが
	 * こちらの続きとして繋がる</b>。
	 * </p>
	 *
	 * @return {@code traceparent}。トレースが無効なら null
	 */
	String traceparent ();

	/**
	 * トレース ID（1本のトレース全体で同じ。ログに出す）
	 *
	 * @return トレース ID。トレースが無効なら null
	 */
	String traceId ();

	/**
	 * スパン ID（この区間の ID。ログに出す）
	 *
	 * @return スパン ID。トレースが無効なら null
	 */
	String spanId ();

	/**
	 * 閉じる
	 *
	 * <p>
	 * <b>例外を投げない。</b>トレースの都合で処理が落ちるのは本末転倒である。
	 * </p>
	 */
	@Override
	void close ();

}
