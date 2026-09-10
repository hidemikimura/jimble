package io.jimble.load;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 負荷をかけて測る（要件 NF-P-08）
 *
 * <p>
 * <b>接続を何本か張って、返ってきたらすぐ次を投げる</b>形（閉ループ）である。
 * 秒あたり何本さばけるかを知るのに向いている。
 * </p>
 *
 * <h2>この形で分かること・分からないこと</h2>
 * <p>
 * <b>秒あたりの本数（RPS）は信じてよい。</b>相手が限界まで詰まった状態を作れるためである。
 * </p>
 * <p>
 * <b>レイテンシの尾（p99）は、実際より良く出る。</b>閉ループでは
 * <b>1本が遅れると次の送信もその分だけ遅れる</b>ので、遅れているあいだに
 * 本来なら積み上がっていたはずの「待たされた要求」が数から抜け落ちる
 * （coordinated omission と呼ばれる）。
 * <b>ここで出る p99 は「詰まっていないときの応答の速さ」であって、
 * 「利用者から見た待ち時間の上限」ではない。</b>
 * </p>
 * <p>
 * 利用者から見た待ち時間を測るには、相手の状態に関係なく<b>一定の間隔で投げ続ける</b>
 * （開ループ）必要がある。ここには入れていない——
 * <b>入れていないことを黙っているほうが害が大きい</b>ので書いておく。
 * </p>
 *
 * <h2>失敗を成功に混ぜない</h2>
 * <p>
 * <b>2xx 以外と例外は、速い応答として数に混ぜない。</b>
 * 混ぜると、<b>アプリが落ちているときにいちばん良い数字が出る</b>——
 * 500 を返すのはたいてい速いからである。
 * </p>
 */
public final class Generator {

	/** 1スレッドあたり、最初に用意する記録の数 */
	private static final int INITIAL_CAPACITY = 16 * 1024;

	private Generator () {
	}

	/**
	 * 測る
	 *
	 * @param url			叩き先
	 * @param connections	同時に張る接続の数
	 * @param warmup		捨てる時間（JIT が温まるまで）
	 * @param measure		測る時間
	 * @return	結果
	 * @throws Exception	失敗した場合
	 */
	public static Result run (URI url, int connections, Duration warmup, Duration measure)
		throws Exception {

		// 温め。結果は捨てる
		if (!warmup.isZero()) {
			measure(url, connections, warmup, false);
		}

		return measure(url, connections, measure, true);

	}

	/**
	 * 決めた時間だけ投げ続ける
	 *
	 * @param url			叩き先
	 * @param connections	同時に張る接続の数
	 * @param duration		時間
	 * @param keep			記録を残すか
	 * @return	結果
	 * @throws Exception	失敗した場合
	 */
	private static Result measure (URI url, int connections, Duration duration, boolean keep)
		throws Exception {

		AtomicBoolean running = new AtomicBoolean(true);

		CountDownLatch ready = new CountDownLatch(connections);
		CountDownLatch done = new CountDownLatch(connections);

		LongAdder errors = new LongAdder();

		long[][] samples = new long[connections][];
		int[] counts = new int[connections];

		Thread[] workers = new Thread[connections];

		for (int index = 0; index < connections; index++) {

			int worker = index;

			/*
			 * <b>仮想スレッドではなく普通のスレッドで投げる。</b>
			 * 負荷をかける側が多重化されていると、<b>測っているのが
			 * 相手の遅さなのか自分の順番待ちなのか分からなくなる</b>。
			 */
			workers[index] = new Thread(() -> {

				try (HttpClient client = client()) {

					HttpRequest request = HttpRequest.newBuilder(url)
						.timeout(Duration.ofSeconds(10))
						.GET()
						.build();

					long[] recorded = new long[INITIAL_CAPACITY];
					int count = 0;

					ready.countDown();

					while (running.get()) {

						long start = System.nanoTime();

						boolean ok = send(client, request);

						long elapsed = System.nanoTime() - start;

						if (!ok) {
							errors.increment();
							continue;
						}

						if (!keep) {
							count++;
							continue;
						}

						if (count == recorded.length) {
							recorded = Arrays.copyOf(recorded, recorded.length * 2);
						}

						recorded[count++] = elapsed;

					}

					samples[worker] = recorded;
					counts[worker] = count;

				} finally {
					done.countDown();
				}

			}, "load-" + index);

			workers[index].start();

		}

		// 全員が構えてから測り始める（立ち上がりの遅れを時間に含めない）
		ready.await();

		long start = System.nanoTime();

		Thread.sleep(duration.toMillis());

		running.set(false);

		long elapsed = System.nanoTime() - start;

		done.await();

		return keep
			? Result.of(url, connections, elapsed, merge(samples, counts), errors.sum())
			: Result.of(url, connections, elapsed, new long[0], errors.sum());

	}

	/**
	 * 1本投げる
	 *
	 * @param client	クライアント
	 * @param request	要求
	 * @return	2xx が返れば true
	 */
	private static boolean send (HttpClient client, HttpRequest request) {

		try {

			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

			return response.statusCode() >= 200 && response.statusCode() < 300;

		} catch (IOException ex) {

			return false;

		} catch (InterruptedException ex) {

			Thread.currentThread().interrupt();

			return false;

		}

	}

	/**
	 * クライアントを1つ作る
	 *
	 * @return	クライアント
	 */
	private static HttpClient client () {

		/*
		 * <b>HTTP/1.1 に固定する。</b>HTTP/2 だと1本の接続に何本も相乗りするので、
		 * <b>「接続を N 本張った」が意味を失う</b>。
		 */
		return HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	}

	/**
	 * スレッドごとの記録を1本にまとめる
	 *
	 * @param samples	記録
	 * @param counts	件数
	 * @return	まとめたもの
	 */
	private static long[] merge (long[][] samples, int[] counts) {

		int total = 0;

		for (int count : counts) {
			total += count;
		}

		long[] merged = new long[total];
		int at = 0;

		for (int index = 0; index < samples.length; index++) {

			if (samples[index] == null) {
				continue;
			}

			System.arraycopy(samples[index], 0, merged, at, counts[index]);
			at += counts[index];

		}

		return merged;

	}

}
