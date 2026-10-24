package io.jimble.util.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 項目ごとの割り当ての上限（byte）をまとめて見る
 *
 * <p>
 * <b>1つ超えたところで止めず、全部測ってから落とす。</b>
 * 途中で止めると、そのあとの項目が {@code bench.txt} に出ず、
 * <b>どこまで増えたのかが一度に見えない</b>。
 * </p>
 *
 * <p>
 * 1つのテストメソッドの中で作って使い、最後に {@link #assertWithin()} を呼ぶ。
 * </p>
 */
final class Budget {

	/**
	 * 何回まわすか（入力の大きさで変える）
	 *
	 * <p>
	 * 大きい入力を小さい入力と同じ回数まわすと、1項目に何分もかかる。
	 * 割り当ては<b>同じコードなら回数によらず同じ</b>なので、回数を減らしても上限の判定は変わらない
	 * （時間のほうは温まり方が変わるので、比べるのは同じ回数どうしにすること）。
	 * </p>
	 */
	enum Runs {

		/** 1回が数百 ns〜数 µs のもの（小さいオブジェクト・型変換・getter） */
		SMALL(20_000, 50_000),

		/** 1回が数十〜数百 µs のもの（100 件の配列） */
		MEDIUM(1_000, 1_000),

		/** 1回が数 ms のもの（数値 1,000 件） */
		LARGE(200, 200);

		/** 空回しする回数 */
		final int warmup;

		/** 測る回数 */
		final int measure;

		Runs (int warmup, int measure) {
			this.warmup = warmup;
			this.measure = measure;
		}

	}

	/*
	 * 測ったものの置き場。<b>読まない。</b>
	 * 使われていないと判定されると、JIT が処理そのものを消してしまい<b>0 byte になる</b>
	 */
	private static Object keep;

	/** 上限を超えた項目 */
	private final List<String> over = new ArrayList<>();

	/**
	 * 測って記録し、上限を超えていたら覚えておく
	 *
	 * @param name	名前（{@code bench.txt} に出る）
	 * @param runs	何回まわすか
	 * @param max	1回あたりの上限（byte）
	 * @param op	測るもの（返した値は捨てずに持っておく）
	 * @return 1回あたりの byte 数
	 */
	long measure (String name, Runs runs, long max, Supplier<?> op) {

		long bytes = Bench.record(name, runs.warmup, runs.measure, () -> keep = op.get());

		if (bytes > max) {
			over.add("%s: %,d byte（上限 %,d）".formatted(name, bytes, max));
		}

		return bytes;

	}

	/**
	 * 上限を超えた項目が無いことを確かめる
	 */
	void assertWithin () {

		assertTrue(over.isEmpty(), "1回あたりの割り当てが上限を超えた:\n  " + String.join("\n  ", over));

	}

}
