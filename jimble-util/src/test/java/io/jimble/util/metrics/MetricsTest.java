package io.jimble.util.metrics;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * メトリクス（{@link Metrics}／要件 NF-O-04）
 *
 * <p>
 * <b>止まらないことが値より大事なところである。</b>メトリクスの都合でリクエストが落ちるのは本末転倒なので、
 * null を渡しても、値を取る先が例外を投げても、名前が増えすぎても<b>黙って続く</b>。
 * ここではその「黙って続く」ぶんまで固定する（黙って続くということは、
 * 壊れても気づけないということでもある）。
 * </p>
 */
class MetricsTest {

	/** ミリ秒をナノ秒に */
	private static final long MS = 1_000_000L;

	@BeforeEach
	void clear () {

		// <b>静的な入れ物なので、テストの間で漏れる</b>
		Metrics.reset();

	}

	// region 数える

	@Test
	@DisplayName("数える")
	void count () {

		Metrics.count("a");
		Metrics.count("a");
		Metrics.count("a", 8);
		Metrics.count("b", -3);

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(10, counter.getLong("a"));
		assertEquals(-3, counter.getLong("b"));

		// 触っていない名前は出てこない（0 も出さない）
		assertFalse(counter.containsKey("c"));

	}

	@Test
	@DisplayName("名前が null でも落ちない")
	void nullName () {

		Metrics.count(null);
		Metrics.count(null, 5);
		Metrics.record(null, 1);
		Metrics.gauge(null, () -> 1);
		Metrics.gauge("g", null);
		Metrics.removeGauge(null);

		Data data = Metrics.snapshot();

		assertEquals(0, data.getData("counter").size());
		assertEquals(0, data.getData("latency").size());
		assertEquals(0, data.getData("gauge").size());

	}

	// endregion

	// region 分布

	@Test
	@DisplayName("バケットの境目は「以下」で入る")
	void bucketBoundary () {

		// 1ms ちょうどは 1 のバケット、そこに 1 ナノ足すと次のバケット
		Metrics.record("x", MS);
		Metrics.record("x", MS + 1);

		Data bucket = latency("x").getData("bucket");

		assertEquals(1, bucket.getLong("1"));
		assertEquals(1, bucket.getLong("5"));

	}

	@Test
	@DisplayName("いちばん上を超えたものはあふれに入る")
	void overflowBucket () {

		Metrics.record("x", 5000 * MS);
		Metrics.record("x", 5001 * MS);

		Data latency = latency("x");

		assertEquals(1, latency.getData("bucket").getLong("5000"));
		assertEquals(1, latency.getData("bucket").getLong("over"));

	}

	@Test
	@DisplayName("1ms 未満はすべていちばん下に入る（0 のバケットは無い）")
	void subMillisecond () {

		Metrics.record("x", 0);
		Metrics.record("x", 1);
		Metrics.record("x", 999_999);

		assertEquals(3, latency("x").getData("bucket").getLong("1"));

	}

	@Test
	@DisplayName("件数・合計・最大はミリ秒で出す")
	void countSumMax () {

		Metrics.record("x", 3 * MS);
		Metrics.record("x", 7 * MS);
		Metrics.record("x", 500_000);

		Data latency = latency("x");

		assertEquals(3, latency.getLong("count"));
		assertEquals(10.5, latency.getDouble("sum_ms"));
		assertEquals(7.0, latency.getDouble("max_ms"));

	}

	@Test
	@DisplayName("負の時間は捨てる")
	void negative () {

		Metrics.record("x", -1);

		assertFalse(Metrics.snapshot().getData("latency").containsKey("x"));

	}

	@Test
	@DisplayName("パーセンタイルは入ったバケットの上限を返す")
	void percentile () {

		// 1ms を 90 件、100ms を 9 件、1000ms を 1 件
		for (int i = 0; i < 90; i++) {
			Metrics.record("x", MS);
		}
		for (int i = 0; i < 9; i++) {
			Metrics.record("x", 100 * MS);
		}
		Metrics.record("x", 1000 * MS);

		Data latency = latency("x");

		// 50 件目は 1ms のバケット、95 件目・99 件目は 100ms のバケット
		assertEquals(1.0, latency.getDouble("p50_ms"));
		assertEquals(100.0, latency.getDouble("p95_ms"));
		assertEquals(100.0, latency.getDouble("p99_ms"));

	}

	@Test
	@DisplayName("あふれに入ったぶんのパーセンタイルは実測の最大を返す")
	void percentileOverflow () {

		Metrics.record("x", MS);
		Metrics.record("x", 12_345 * MS);

		Data latency = latency("x");

		// 1 件目は 1ms のバケット、2 件目はあふれ。
		// <b>あふれには上限が無い</b>ので、バケットの上限のかわりに実測の最大を返す
		assertEquals(1.0, latency.getDouble("p50_ms"));
		assertEquals(12_345.0, latency.getDouble("p99_ms"));
		assertEquals(12_345.0, latency.getDouble("max_ms"));

	}

	@Test
	@DisplayName("何件入れても覚える量は変わらない")
	void fixedMemory () {

		for (int i = 0; i < 10_000; i++) {
			Metrics.record("x", i * 1000L);
		}

		Data bucket = latency("x").getData("bucket");

		// バケットは 8 個 + あふれの 9 個で固定
		assertEquals(Metrics.BUCKETS_MS.length + 1, bucket.size());
		assertEquals(10_000, latency("x").getLong("count"));

	}

	// endregion

	// region そのときの値

	@Test
	@DisplayName("そのときの値は snapshot のたびに引き直す")
	void gauge () {

		AtomicLong value = new AtomicLong(3);

		Metrics.gauge("pool.active", value::get);

		assertEquals(3, Metrics.snapshot().getData("gauge").getLong("pool.active"));

		value.set(7);

		assertEquals(7, Metrics.snapshot().getData("gauge").getLong("pool.active"));

	}

	@Test
	@DisplayName("同じ名前で入れ直すと、あとのほうが残る")
	void gaugeReplaced () {

		Metrics.gauge("g", () -> 1);
		Metrics.gauge("g", () -> 2);

		assertEquals(2, Metrics.snapshot().getData("gauge").getLong("g"));

		Metrics.removeGauge("g");

		assertFalse(Metrics.snapshot().getData("gauge").containsKey("g"));

	}

	@Test
	@DisplayName("1つ壊れていても、ほかは返す")
	void gaugeFailureIsolated () {

		/*
		 * <b>接続プールを止めたあとも gauge は残っている</b>ことがあり、
		 * そこを引くと例外が飛ぶ。1つのために snapshot ごと落ちると、
		 * <b>いちばん見たいときに何も見えなくなる</b>
		 */
		Metrics.gauge("broken", () -> {
			throw new IllegalStateException("プールが閉じています");
		});
		Metrics.gauge("ok", () -> 42);

		Data gauge = Metrics.snapshot().getData("gauge");

		assertEquals(42, gauge.getLong("ok"));
		assertFalse(gauge.containsKey("broken"));

	}

	// endregion

	// region 増えすぎ

	@Test
	@DisplayName("名前が上限に達したら、それ以上は増やさない")
	void nameLimit () {

		for (int i = 0; i < Metrics.MAX_NAMES; i++) {
			Metrics.count("n" + i);
		}

		Data before = Metrics.snapshot();
		assertEquals(Metrics.MAX_NAMES, before.getData("counter").size());

		// 3 種類とも、新しい名前は入らない
		Metrics.count("あふれ");
		Metrics.record("あふれ", MS);
		Metrics.gauge("あふれ", () -> 1);

		Data after = Metrics.snapshot();

		assertEquals(Metrics.MAX_NAMES, after.getData("counter").size());
		assertEquals(0, after.getData("latency").size());
		assertEquals(0, after.getData("gauge").size());

	}

	@Test
	@DisplayName("上限に達しても、すでにある名前は数え続ける")
	void existingNamesKeepCounting () {

		for (int i = 0; i < Metrics.MAX_NAMES; i++) {
			Metrics.count("n" + i);
		}

		Metrics.count("n0");
		Metrics.count("あふれ");

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(2, counter.getLong("n0"));
		assertFalse(counter.containsKey("あふれ"));

	}

	@Test
	@DisplayName("上限は 3 種類の合計で見る")
	void limitIsShared () {

		// カウンタと分布で半分ずつ埋める
		for (int i = 0; i < Metrics.MAX_NAMES / 2; i++) {
			Metrics.count("c" + i);
			Metrics.record("d" + i, MS);
		}

		Metrics.gauge("g", () -> 1);

		assertFalse(Metrics.snapshot().getData("gauge").containsKey("g"));

	}

	@Test
	@DisplayName("上限に達していても、gauge の入れ直しはできる")
	void gaugeReplaceUnderLimit () {

		Metrics.gauge("g", () -> 1);

		for (int i = 0; i < Metrics.MAX_NAMES; i++) {
			Metrics.count("n" + i);
		}

		// <b>入れ直しまで拒むと、上限に達したあとプールを作り直せなくなる</b>
		Metrics.gauge("g", () -> 2);

		assertEquals(2, Metrics.snapshot().getData("gauge").getLong("g"));

	}

	// endregion

	// region そのほか

	@Test
	@DisplayName("形は counter / latency / gauge の3つ")
	void shape () {

		Data data = Metrics.snapshot();

		assertEquals(3, data.size());
		assertTrue(data.containsKey("counter"));
		assertTrue(data.containsKey("latency"));
		assertTrue(data.containsKey("gauge"));

	}

	@Test
	@DisplayName("消せる")
	void reset () {

		Metrics.count("a");
		Metrics.record("b", MS);
		Metrics.gauge("c", () -> 1);

		Metrics.reset();

		Data data = Metrics.snapshot();

		assertEquals(0, data.getData("counter").size());
		assertEquals(0, data.getData("latency").size());
		assertEquals(0, data.getData("gauge").size());

	}

	@Test
	@DisplayName("同時に入れても数が合う")
	void concurrent () throws Exception {

		int threads = 8;
		int loops = 5000;

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);

		for (int t = 0; t < threads; t++) {

			pool.execute(() -> {

				try {

					start.await();

					for (int i = 0; i < loops; i++) {
						Metrics.count("http.request");
						Metrics.record("http.GET /posts/{id}", MS);
					}

				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}

			});

		}

		start.countDown();

		assertTrue(done.await(30, TimeUnit.SECONDS));
		pool.shutdown();

		Data data = Metrics.snapshot();

		assertEquals(threads * loops, data.getData("counter").getLong("http.request"));

		Data latency = data.getData("latency").getData("http.GET /posts/{id}");

		assertEquals(threads * loops, latency.getLong("count"));
		assertEquals(threads * loops, latency.getData("bucket").getLong("1"));

	}

	// endregion

	// region 道具

	/**
	 * 分布を取り出す
	 *
	 * @param name 名前
	 * @return 分布
	 */
	private static Data latency (String name) {

		return Metrics.snapshot().getData("latency").getData(name);

	}

	// endregion

}
