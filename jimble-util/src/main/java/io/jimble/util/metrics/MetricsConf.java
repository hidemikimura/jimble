package io.jimble.util.metrics;

import io.jimble.util.conf.ConfFlag;

/**
 * メトリクスの設定（要件 NF-O-04 / D-167）
 *
 * <pre>
 * metrics {
 *   enabled = true    # 数えるか
 * }
 * </pre>
 *
 * <h2>切っても速くはならない</h2>
 * <p>
 * <b>数えること自体は 0 byte / 58ns である。</b>
 * 高かったのは<b>名前をリクエストごとに組み立てていた</b>ぶん（1039 byte）で、
 * そちらは<b>作らないようにした</b>（要件 D-167）——
 * <b>有効なままでも、ほとんど費用が残っていない</b>。
 * </p>
 *
 * <p>
 * <b>それでも切れるようにしてあるのは、費用のためではない。</b>
 * 数えた値をどこにも出さないアプリでは<b>持っている意味が無い</b>し、
 * 名前の上限（{@link Metrics#MAX_NAMES}）に当たる心配も消える。
 * </p>
 */
public final class MetricsConf {

	/** 設定キー：数えるか */
	public static final String KEY_ENABLED = "metrics.enabled";

	/**
	 * リクエストごとに読むもの（要件 D-167）
	 *
	 * <p><b>覚えておく。</b>設定が入れ替わったら読み直す。</p>
	 */
	private static final ConfFlag ENABLED = ConfFlag.of(KEY_ENABLED, true);

	private MetricsConf () {}

	/**
	 * 数えるか（要件 D-167）
	 *
	 * <p>
	 * <b>切ると {@link Metrics#count(String)} などは何もしない。</b>
	 * {@link Metrics#snapshot()} は<b>空のまま</b>を返す——
	 * <b>止まっていることが見て分かる</b>ようにするためで、
	 * 例外にはしない（切っているアプリで落ちても仕方がない）。
	 * </p>
	 *
	 * @return	数える場合 = true
	 */
	public static boolean enabled () {

		return ENABLED.get();

	}

}
