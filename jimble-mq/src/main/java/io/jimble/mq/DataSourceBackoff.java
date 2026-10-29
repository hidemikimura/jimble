package io.jimble.mq;

import io.jimble.util.log.Log;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 落ちているデータソースを、しばらくあけてから見直す（キューごとに1つ。2.7.9）
 *
 * <p>
 * 店の DB を {@code required = false} にして並べると、1 つが落ちることは起こりうる。
 * 落ちた DB を毎周見に行くと、<b>つながらない DB は接続を {@code connection_timeout} まで待たせる</b>ので、
 * 取り出し役がそのたびに止まり、<b>ほかの DB の行まで待たされる</b>（1 秒待たせる DB が 1 つあるだけで、
 * ほかの DB の 10 件に 10 秒かかった）。ログも毎周出る。
 * </p>
 *
 * <p>
 * 失敗したデータソースは {@link #FIRST} から倍々に（{@link #MAX} まで）あけて見直し、その間は飛ばす。
 * ログは、落ちたとき・見直しても落ちていたとき・戻ったときにだけ出す。
 * 取り出し役・迷子の戻し・件数の数えが同じものを見る（どれかが失敗に気づけば、ほかも待たずに飛ばす）。
 * </p>
 */
final class DataSourceBackoff {

	/** 最初にあける時間 */
	static final Duration FIRST = Duration.ofSeconds(1);

	/** あける時間の上限 */
	static final Duration MAX = Duration.ofSeconds(30);

	/* データソース名 → 落ちている状態 */
	private final Map<String, Down> downs = new ConcurrentHashMap<>();

	/* キュー名（ログに出す） */
	private final String queueName;

	/**
	 * 落ちている状態
	 *
	 * @param retryAt	次に見直す時刻（ミリ秒）
	 * @param waitMillis	いまあけている時間（ミリ秒）
	 */
	private record Down (long retryAt, long waitMillis) {}

	DataSourceBackoff (String queueName) {

		this.queueName = queueName;

	}

	/**
	 * いま見に行ってよいか
	 *
	 * @param dataSource	データソース名
	 * @param now			いま（ミリ秒）
	 * @return	落ちていない、または見直す時刻が来た場合 = true
	 */
	boolean isAvailable (String dataSource, long now) {

		Down down = downs.get(dataSource);

		return down == null || down.retryAt() <= now;

	}

	/**
	 * 失敗した
	 *
	 * @param dataSource	データソース名
	 * @param now			いま（ミリ秒）
	 * @param what			何をしていたか（ログに出す）
	 * @param ex			失敗
	 */
	void failed (String dataSource, long now, String what, Throwable ex) {

		Down next = downs.compute(dataSource, (key, before) -> {
			long wait = before == null ? FIRST.toMillis() : Math.min(before.waitMillis() * 2, MAX.toMillis());
			return new Down(now + wait, wait);
		});

		Log.warn("MQ: データソース %s で%sに失敗しました。%d 秒後にまた見ます（そのあいだ、このデータソースは飛ばします）: %s / %s"
			.formatted(dataSource, what, next.waitMillis() / 1000, queueName, ex.getMessage()));

	}

	/**
	 * うまくいった（落ちていたなら、戻ったと言う）
	 *
	 * @param dataSource	データソース名
	 */
	void succeeded (String dataSource) {

		if (downs.remove(dataSource) != null) {
			Log.info("MQ: データソース %s が戻りました: %s".formatted(dataSource, queueName));
		}

	}

}
