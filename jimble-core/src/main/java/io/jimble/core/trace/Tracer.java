package io.jimble.core.trace;

/**
 * トレースを出す先（要件 NF-O-05）
 *
 * <p>
 * <b>jimble はこの口だけを持ち、実装は持たない。</b>
 * OpenTelemetry の実装は {@code jimble-otel} にあり、<b>そちらを依存に足したときだけ</b>
 * 1.3MB ほどの jar が増える。足さないアプリの実行時クラスパスは1 byte も増えない。
 * </p>
 *
 * <p>
 * 自分で書くこともできる（試験用に標準出力へ出す、社内の仕組みへ送る、など）。
 * 実装するときは次の2つを守ること。
 * </p>
 *
 * <ul>
 *   <li><b>例外を投げない。</b>トレースの都合でリクエストが落ちるのは本末転倒である</li>
 *   <li><b>親子は実装側で面倒を見る。</b>jimble は「いまのスパン」を持たない。
 *       OpenTelemetry はスレッドごとに現在のスパンを持っているので、それに任せている</li>
 * </ul>
 */
public interface Tracer {

	/**
	 * スパンを始める
	 *
	 * @param name			名前。<b>種類の数が増えない書き方にすること</b>
	 *						（{@code GET /posts/{id}} であって {@code GET /posts/12345} ではない）
	 * @param kind			種類
	 * @param traceparent	外から来たトレースに繋ぐときの {@code traceparent}（W3C Trace Context）。
	 *						無ければ null で、その場合は「いまのスパン」の子になる
	 * @param elapsedNanos	<b>すでに経過している時間</b>（ナノ秒）。
	 *						0 なら「いまから始まる」。0 より大きいときは
	 *						<b>その時間だけさかのぼったところから始まったこと</b>にする
	 *						（SQL のように、測り終わってから記録するもの）
	 * @return スパン
	 */
	Span start (String name, SpanKind kind, String traceparent, long elapsedNanos);

	/**
	 * いま開いているスパン
	 *
	 * <p>
	 * ログに出すトレース ID と、外へ渡す {@code traceparent} を引くのに使う。
	 * <b>「いま」を覚えているのは実装側である</b>（jimble は持たない）。
	 * </p>
	 *
	 * @return いまのスパン。無ければ {@link Span#NOOP}
	 */
	default Span current () {

		return Span.NOOP;

	}

}
