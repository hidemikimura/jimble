package io.jimble.web.bench;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ベンチマークの測り方（要件 NF-P-06）
 *
 * <h2>落とすのは「割り当てた byte 数」だけ</h2>
 * <p>
 * <b>時間では落とさない。</b>CI の共用ランナーは走るたびに 20〜30% ぶれるので、
 * 「ベースライン比 -10%」を時間でやると<b>直していないのに赤くなる日</b>ができる。
 * 赤が信用されなくなると、本物の退行も見過ごされる。
 * </p>
 * <p>
 * かわりに<b>1回あたりに割り当てた byte 数</b>で落とす。これは
 * {@code ThreadMXBean#getThreadAllocatedBytes} が返す実測で、<b>同じコードなら機械が変わっても同じ</b>。
 * 「リクエストごとに {@code ArrayList} を1つ増やした」「ループの中で文字列を作った」は
 * ここに出る（要件 NF-P-05）。時間は<b>記録するだけ</b>で、落とす材料にはしない。
 * </p>
 *
 * <h2>ここで捕まえられないこと</h2>
 * <ul>
 *   <li><b>割り当てを増やさない退行</b>（配列を舐める回数が increase しただけ、
 *       ロックの取り合いが増えただけ、など）。時間には出るが、その時間を信じないと決めている</li>
 *   <li><b>他のスレッドでの割り当て</b>（測るのは呼んだスレッドのぶんだけ）</li>
 * </ul>
 */
public final class Bench {

	/** 割り当て量を返してくれる MXBean */
	private static final ThreadMXBean THREAD_MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();

	/** 記録（表にして出す） */
	private static final List<String> LINES = new ArrayList<>();

	/** すでにファイルを書いたか（1回目だけ書き直す） */
	private static final AtomicBoolean WRITTEN = new AtomicBoolean();

	private Bench () {
	}

	/**
	 * 1回あたりの割り当て byte 数を測る
	 *
	 * @param warmup	空回しする回数
	 * @param measure	測る回数
	 * @param op		測るもの
	 * @return 1回あたりの byte 数
	 */
	public static long bytesPerOp (int warmup, int measure, Runnable op) {

		for (int i = 0; i < warmup; i++) {
			op.run();
		}

		// 測るループそのものの取り分を引く
		long empty = allocated(measure, () -> { });
		long used = allocated(measure, op);

		return Math.max(0, (used - empty) / measure);

	}

	/**
	 * 1回あたりのナノ秒を測る（<b>落とす材料にはしない</b>）
	 *
	 * @param warmup	空回しする回数
	 * @param measure	測る回数
	 * @param op		測るもの
	 * @return 1回あたりのナノ秒
	 */
	public static double nanosPerOp (int warmup, int measure, Runnable op) {

		for (int i = 0; i < warmup; i++) {
			op.run();
		}

		long start = System.nanoTime();

		for (int i = 0; i < measure; i++) {
			op.run();
		}

		return (System.nanoTime() - start) / (double) measure;

	}

	/**
	 * 測って記録する
	 *
	 * @param name		名前
	 * @param warmup	空回しする回数
	 * @param measure	測る回数
	 * @param op		測るもの
	 * @return 1回あたりの byte 数
	 */
	public static long record (String name, int warmup, int measure, Runnable op) {

		long bytes = bytesPerOp(warmup, measure, op);
		double nanos = nanosPerOp(warmup, measure, op);

		LINES.add("%-52s %8d byte %12.1f ns".formatted(name, bytes, nanos));

		return bytes;

	}

	/**
	 * 記録を出す
	 *
	 * <p>
	 * 標準出力と {@code build/bench/bench.txt} の両方へ。
	 * CI は後者を成果物として持ち帰る（人が前後を見比べるためのもので、
	 * <b>ここの数字で落とすことはしない</b>）。
	 * </p>
	 *
	 * <p>
	 * ベンチマークのクラスごとに1回呼ぶ。<b>1回目だけ書き直し、2回目からは書き足す</b>ので、
	 * 1つのファイルに全部が並ぶ。
	 * </p>
	 */
	public static void report () {

		if (LINES.isEmpty()) {
			return;
		}

		String text = String.join("\n", LINES) + "\n";

		System.out.println();
		System.out.println("=== ベンチマーク（要件 NF-P-06）===");
		System.out.print(text);

		try {

			Path path = Path.of("build", "bench", "bench.txt");
			Files.createDirectories(path.getParent());

			if (WRITTEN.compareAndSet(false, true)) {
				Files.writeString(path, text, StandardCharsets.UTF_8);
			} else {
				Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			}

		} catch (IOException ex) {
			// 書けなくても測定は済んでいる
			System.out.println("（bench.txt は書けませんでした: " + ex + "）");
		}

		LINES.clear();

	}

	/**
	 * 割り当て量を測る
	 *
	 * @param count	回す回数
	 * @param op	回すもの
	 * @return 割り当てた byte 数
	 */
	private static long allocated (int count, Runnable op) {

		long id = Thread.currentThread().threadId();
		long start = THREAD_MX.getThreadAllocatedBytes(id);

		for (int i = 0; i < count; i++) {
			op.run();
		}

		return THREAD_MX.getThreadAllocatedBytes(id) - start;

	}

}
