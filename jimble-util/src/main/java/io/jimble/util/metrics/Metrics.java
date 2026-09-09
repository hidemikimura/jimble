package io.jimble.util.metrics;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * メトリクス（要件 NF-O-04）
 *
 * <p>
 * <b>値を返すだけで、ルートは生やさない。</b>
 * {@code /metrics} を外に晒すかどうかは、認証も公開範囲も含めてアプリが決めることである。
 * ヘルスチェックをフレームワークで持たないと決めたのと同じ理由である（D-106 / 要件 NF-O-03）。
 * </p>
 *
 * <pre>{@code
 * // アプリ側に1行書く
 * get("/metrics", context -> context.response().json(Metrics.snapshot()));
 * }</pre>
 *
 * <h2>3種類ある</h2>
 * <ul>
 *   <li><b>数える</b>（{@link #count(String)}）— リクエスト数のように増えるだけのもの</li>
 *   <li><b>分布</b>（{@link #record(String, long)}）— レイテンシのように、平均だけでは足りないもの</li>
 *   <li><b>そのときの値</b>（{@link #gauge(String, LongSupplier)}）— 接続プールの使用数のように、
 *       いま何個かを外から引くもの</li>
 * </ul>
 *
 * <h2>I/O はしない</h2>
 * <p>
 * {@link #snapshot()} は<b>登録された {@link LongSupplier} を呼ぶだけ</b>で、DB もネットワークも触らない。
 * <b>I/O をする supplier を登録しないこと。</b>メトリクスを見にいっただけで DB に SQL が飛ぶのは
 * 原則5（隠れた I/O を作らない）に反するし、<b>DB が詰まっているときに限って
 * メトリクスも取れなくなる</b>——いちばん見たいときに見えない。
 * 数えるものが DB の側にあるなら、<b>すでに DB を触っている処理が値を置いていく</b>形にすること
 * （MQ のキュー滞留数がその形になっている）。
 * </p>
 *
 * <h2>名前を増やしすぎない</h2>
 * <p>
 * 名前は<b>{@link #MAX_NAMES} 種類まで</b>で、超えたぶんは捨てて1回だけ警告する。
 * <b>利用者の入力を名前にすると、いくらでも増える。</b>
 * URL のパスをそのまま名前にすると、{@code /aaa} {@code /aab} … と叩かれるだけで
 * ヒープが埋まる。jimble の中では<b>マッチしたルートの型</b>（{@code GET /posts/{id}}）を使い、
 * どのルートにも当たらなかったものは1つにまとめている。
 * </p>
 *
 * <h2>費用</h2>
 * <p>
 * 1回あたり {@link LongAdder} と配列の加算が数回で、確保するメモリは名前1つにつき
 * 数十〜数百 byte。<b>常に動いている</b>（切る設定は持たない）。
 * </p>
 */
public final class Metrics {

	/** 名前の種類の上限 */
	static final int MAX_NAMES = 1000;

	/**
	 * 分布のバケットの上限値（ミリ秒）
	 *
	 * <p>
	 * これを超えたものは最後の1つ（あふれ）に入る。
	 * </p>
	 */
	static final long[] BUCKETS_MS = {1, 5, 10, 50, 100, 500, 1000, 5000};

	/** 数えるもの */
	private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

	/** 分布 */
	private static final Map<String, Distribution> DISTRIBUTIONS = new ConcurrentHashMap<>();

	/** そのときの値 */
	private static final Map<String, LongSupplier> GAUGES = new ConcurrentHashMap<>();

	/** 上限を超えたことを1回だけ言うための印 */
	private static final AtomicBoolean OVERFLOW_WARNED = new AtomicBoolean();

	private Metrics () {
	}

	// region 入れる

	/**
	 * 1つ数える
	 *
	 * @param name 名前
	 */
	public static void count (String name) {

		count(name, 1);

	}

	/**
	 * 数える
	 *
	 * @param name	名前
	 * @param delta	増やす数
	 */
	public static void count (String name, long delta) {

		if (name == null) {
			return;
		}

		LongAdder adder = COUNTERS.get(name);

		if (adder == null) {

			if (isFull()) {
				return;
			}

			adder = COUNTERS.computeIfAbsent(name, key -> new LongAdder());

		}

		adder.add(delta);

	}

	/**
	 * 分布に入れる
	 *
	 * @param name	名前
	 * @param nanos	かかった時間（ナノ秒）
	 */
	public static void record (String name, long nanos) {

		if (name == null || nanos < 0) {
			return;
		}

		Distribution distribution = DISTRIBUTIONS.get(name);

		if (distribution == null) {

			if (isFull()) {
				return;
			}

			distribution = DISTRIBUTIONS.computeIfAbsent(name, key -> new Distribution());

		}

		distribution.add(nanos);

	}

	/**
	 * そのときの値を引く先を登録する
	 *
	 * <p>
	 * <b>{@link #snapshot()} のたびに呼ばれる。</b>I/O をするものを渡さないこと。
	 * 同じ名前で登録し直すと、あとのほうが残る。
	 * </p>
	 *
	 * @param name		名前
	 * @param supplier	値を返すもの
	 */
	public static void gauge (String name, LongSupplier supplier) {

		if (name == null || supplier == null) {
			return;
		}

		if (!GAUGES.containsKey(name) && isFull()) {
			return;
		}

		GAUGES.put(name, supplier);

	}

	/**
	 * 登録されている名前を並べる
	 *
	 * <p>
	 * <b>値は引かない。</b>外す名前を探すためだけに {@link #snapshot()} を呼ぶと、
	 * <b>これから外そうとしている壊れた supplier まで呼んでしまう</b>
	 * （閉じたあとの接続プールなど）。
	 * </p>
	 *
	 * @return 名前
	 */
	public static Set<String> gaugeNames () {

		return Set.copyOf(GAUGES.keySet());

	}

	/**
	 * そのときの値を引く先を外す
	 *
	 * @param name 名前
	 */
	public static void removeGauge (String name) {

		if (name == null) {
			return;
		}

		GAUGES.remove(name);

	}

	// endregion

	// region 取り出す

	/**
	 * いまの値を取り出す
	 *
	 * <p>
	 * 形は次のとおり。
	 * </p>
	 *
	 * <pre>{@code
	 * {
	 *   "counter": { "http.request": 1234, "http.status.5xx": 2 },
	 *   "latency": {
	 *     "GET /posts/{id}": {
	 *       "count": 1200, "sum_ms": 4321.0, "max_ms": 812.3,
	 *       "p50_ms": 10, "p95_ms": 100, "p99_ms": 500,
	 *       "bucket": { "1": 300, "5": 700, ..., "over": 1 }
	 *     }
	 *   },
	 *   "gauge": { "db.pool.main.active": 3 }
	 * }
	 * }</pre>
	 *
	 * @return いまの値
	 */
	public static Data snapshot () {

		Data counters = new Data();

		for (Map.Entry<String, LongAdder> entry : COUNTERS.entrySet()) {
			counters.put(entry.getKey(), entry.getValue().sum());
		}

		Data latencies = new Data();

		for (Map.Entry<String, Distribution> entry : DISTRIBUTIONS.entrySet()) {
			latencies.put(entry.getKey(), entry.getValue().snapshot());
		}

		Data gauges = new Data();

		for (Map.Entry<String, LongSupplier> entry : GAUGES.entrySet()) {

			try {
				gauges.put(entry.getKey(), entry.getValue().getAsLong());
			} catch (Exception ex) {
				// 1つ壊れていても、ほかは返す
				Log.warn("メトリクスの値が取れませんでした: %s（%s）".formatted(entry.getKey(), ex));
			}

		}

		Data data = new Data();
		data.put("counter", counters);
		data.put("latency", latencies);
		data.put("gauge", gauges);

		return data;

	}

	/**
	 * 全部消す
	 *
	 * <p>
	 * <b>テストのためのものである。</b>動いているアプリで呼ぶと、
	 * それまで数えたものが消える。
	 * </p>
	 */
	public static void reset () {

		COUNTERS.clear();
		DISTRIBUTIONS.clear();
		GAUGES.clear();
		OVERFLOW_WARNED.set(false);

	}

	// endregion

	// region 中身

	/**
	 * 名前がもう増やせないか
	 *
	 * @return 増やせなければ true
	 */
	private static boolean isFull () {

		if (COUNTERS.size() + DISTRIBUTIONS.size() + GAUGES.size() < MAX_NAMES) {
			return false;
		}

		if (OVERFLOW_WARNED.compareAndSet(false, true)) {
			Log.warn(("メトリクスの名前が %d 種類を超えたので、これ以上は数えません。"
				+ "利用者の入力を名前にしていないか確かめてください").formatted(MAX_NAMES));
		}

		return true;

	}

	/**
	 * 分布
	 *
	 * <p>
	 * 固定のバケットに数を入れるだけ。<b>ひとつひとつの値は覚えない</b>ので、
	 * 何件入れてもメモリは増えない。そのかわり<b>境界の粒度でしか分からない</b>。
	 * </p>
	 */
	private static final class Distribution {

		/** バケットごとの数（最後の1つはあふれ） */
		private final AtomicLongArray buckets = new AtomicLongArray(BUCKETS_MS.length + 1);

		/** 件数 */
		private final LongAdder count = new LongAdder();

		/** 合計（ナノ秒） */
		private final LongAdder sumNanos = new LongAdder();

		/** 最大（ナノ秒） */
		private final java.util.concurrent.atomic.AtomicLong maxNanos = new java.util.concurrent.atomic.AtomicLong();

		/**
		 * 1件入れる
		 *
		 * @param nanos かかった時間（ナノ秒）
		 */
		void add (long nanos) {

			count.increment();
			sumNanos.add(nanos);
			maxNanos.accumulateAndGet(nanos, Math::max);

			/*
			 * <b>ミリ秒に直してから比べない。</b>ナノ秒を割ると切り捨てになるので、
			 * 1.9ms が「1ms 以下」のバケットに入ってしまう
			 */
			for (int i = 0; i < BUCKETS_MS.length; i++) {
				if (nanos <= BUCKETS_MS[i] * 1_000_000L) {
					buckets.incrementAndGet(i);
					return;
				}
			}

			buckets.incrementAndGet(BUCKETS_MS.length);

		}

		/**
		 * 取り出す
		 *
		 * @return いまの値
		 */
		Data snapshot () {

			long total = count.sum();

			Data bucket = new Data();
			long[] values = new long[BUCKETS_MS.length + 1];

			for (int i = 0; i < values.length; i++) {
				values[i] = buckets.get(i);
				bucket.put(i < BUCKETS_MS.length ? String.valueOf(BUCKETS_MS[i]) : "over", values[i]);
			}

			double maxMs = maxNanos.get() / 1_000_000d;

			Data data = new Data();
			data.put("count", total);
			data.put("sum_ms", sumNanos.sum() / 1_000_000d);
			data.put("max_ms", maxMs);
			data.put("p50_ms", percentile(values, total, maxMs, 50));
			data.put("p95_ms", percentile(values, total, maxMs, 95));
			data.put("p99_ms", percentile(values, total, maxMs, 99));
			data.put("bucket", bucket);

			return data;

		}

		/**
		 * パーセンタイルを求める
		 *
		 * <p>
		 * <b>入ったバケットの上限を返す。</b>ひとつひとつの値を覚えていないので、
		 * 「50ms 以下」までしか言えない。いちばん上（あふれ）に入ったときは実測の最大を返す。
		 * </p>
		 *
		 * @param values	バケットごとの数
		 * @param total		件数
		 * @param maxMs		実測の最大（ミリ秒）
		 * @param percent	何パーセントか
		 * @return パーセンタイル（ミリ秒）
		 */
		private static double percentile (long[] values, long total, double maxMs, int percent) {

			if (total == 0) {
				return 0;
			}

			// 1件目を 1 と数えたときの、何件目か
			long rank = (long) Math.ceil(total * percent / 100d);
			long seen = 0;

			for (int i = 0; i < BUCKETS_MS.length; i++) {

				seen += values[i];

				if (seen >= rank) {
					return BUCKETS_MS[i];
				}

			}

			return maxMs;

		}

	}

	// endregion

}
