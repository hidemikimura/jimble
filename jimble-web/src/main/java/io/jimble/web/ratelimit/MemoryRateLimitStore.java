package io.jimble.web.ratelimit;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * メモリに置く（要件 F-R-15）
 *
 * <p>
 * <b>その JVM の中だけ。</b>2台に並べると、それぞれが別々に数えるので
 * 全体では2倍まで通る。台をまたいで数えたいなら {@code rate_limit.store = redis} を使う。
 * </p>
 *
 * <p>
 * 数えた値は<b>使われなくなったら捨てる</b>。IP ごとに作るので、
 * 放っておくと増え続ける。
 * </p>
 */
public final class MemoryRateLimitStore implements RateLimitStore {

	/** 掃除する間隔（ミリ秒） */
	private static final long CLEAN_INTERVAL_MILLIS = 60_000;

	/* 単位ごとのバケツ */
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	/* 次に掃除する時刻 */
	private final AtomicLong nextClean = new AtomicLong(System.currentTimeMillis() + CLEAN_INTERVAL_MILLIS);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimitResult consume (String key, long limit, Duration duration) {

		clean();

		Bucket bucket = buckets.computeIfAbsent(key, name -> new Bucket(limit));

		return bucket.consume(limit, duration.toNanos());

	}

	/**
	 * 覚えているものの数
	 *
	 * @return	数
	 */
	public int size () {

		return buckets.size();

	}

	/**
	 * 全部忘れる（テスト用）
	 */
	public void clear () {

		buckets.clear();

	}

	/**
	 * 満タンのまま置きっぱなしのものを捨てる
	 *
	 * <p>
	 * 満タン＝<b>その単位からしばらく来ていない</b>ということなので、
	 * 忘れても結果は変わらない。
	 * </p>
	 */
	private void clean () {

		long now = System.currentTimeMillis();
		long planned = nextClean.get();

		if (now < planned || !nextClean.compareAndSet(planned, now + CLEAN_INTERVAL_MILLIS)) {
			return;
		}

		for (Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {

			if (it.next().getValue().isFull()) {
				it.remove();
			}

		}

	}

	/**
	 * バケツ1つ
	 *
	 * <p>
	 * 状態を2つ（残りと最後に足した時刻）持つので、<b>まとめて守る</b>。
	 * 別々に触ると、同時に来たぶんが数え落ちる。
	 * </p>
	 */
	private static final class Bucket {

		/* 残り */
		private double tokens;

		/* 最後に足した時刻 */
		private long lastNanos = System.nanoTime();

		/* 満タンの量（掃除の判定に使う） */
		private double capacity;

		/* 空から満タンに戻るまでの時間（掃除の判定に使う） */
		private long durationNanos = Long.MAX_VALUE;

		/**
		 * コンストラクタ
		 *
		 * @param limit	貯められる回数
		 */
		private Bucket (long limit) {

			this.tokens = limit;
			this.capacity = limit;

		}

		/**
		 * 1回ぶん数える
		 *
		 * @param limit			貯められる回数
		 * @param durationNanos	空から満タンに戻るまでの時間
		 * @return	結果
		 */
		private synchronized RateLimitResult consume (long limit, long durationNanos) {

			long now = System.nanoTime();

			capacity = limit;
			this.durationNanos = durationNanos;

			// 経った時間ぶんだけ戻す
			double refill = (double) (now - lastNanos) * limit / durationNanos;

			tokens = Math.min(limit, tokens + refill);
			lastNanos = now;

			if (tokens >= 1) {
				tokens -= 1;
				return RateLimitResult.allow((long) tokens);
			}

			double waitNanos = (1 - tokens) * durationNanos / limit;

			return RateLimitResult.deny((long) Math.ceil(waitNanos / 1_000_000d));

		}

		/**
		 * 満タンか
		 *
		 * <p>
		 * <b>いま数えたらどうなるか</b>で見る。戻すのは {@code consume} のときだけなので、
		 * 持っている値をそのまま見ると<b>来なくなったものが永遠に残る</b>。
		 * </p>
		 *
		 * @return	満タンなら true
		 */
		private synchronized boolean isFull () {

			double refill = (double) (System.nanoTime() - lastNanos) * capacity / durationNanos;

			return tokens + refill >= capacity;

		}

	}

}
