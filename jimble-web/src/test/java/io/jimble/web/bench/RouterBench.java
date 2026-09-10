package io.jimble.web.bench;

import io.jimble.web.router.Handler;
import io.jimble.web.router.Router;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ルーティングの費用（要件 NF-P-04 / NF-P-06 / D-6）
 *
 * <pre>
 * ./gradlew :jimble-web:bench
 * </pre>
 *
 * <h2>何を見ているか</h2>
 * <ul>
 *   <li><b>ルート数が増えても費用が変わらないこと</b>（木を降りているのであって、
 *       ルートを1本ずつ試していないこと）</li>
 *   <li><b>パスの深さに対して線形以下であること</b>（要件 NF-P-04）</li>
 * </ul>
 *
 * <h2>落とすのは byte 数だけ</h2>
 * <p>
 * 理由は {@link Bench} に書いてある。時間は表に出るが、赤にはしない。
 * </p>
 */
@Tag("bench")
class RouterBench {

	/** 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	/** 空回しする回数 */
	private static final int WARMUP = 100_000;

	/** 測る回数 */
	private static final int MEASURE = 300_000;

	/**
	 * ルート数を変えても同じとみなす幅（byte）
	 *
	 * <p>
	 * <b>ぴったり同じは求めない。</b>前は {@code assertEquals} で突き合わせていて、
	 * <b>552 が一度だけ 556 になっただけで赤くなった</b>（続けて3回流すと 552 に戻る）。
	 * 直していないのに赤くなる日を作らない、というのが D-125 で決めたことなので、
	 * <b>時間を byte に替えたのに、そこだけ元に戻っていた</b>ことになる。
	 * </p>
	 *
	 * <p>
	 * <b>この幅で見逃すものは無い。</b>ここが守っているのは
	 * 「ルートを1本ずつ試す実装に戻る」ことで、
	 * そうなれば 1000 本では<b>桁が変わる</b>（1本 8 byte でも 8KB）。
	 * 数十 byte の揺れとは比べものにならない。
	 * </p>
	 */
	private static final long SLACK_BYTES = 64;

	/**
	 * 先に温めておく
	 *
	 * <p>
	 * <b>いちばん最初に測ったものだけ高く出る。</b>まだ JIT が効いていないぶんが
	 * 混ざるためで、そのまま比べると<b>測った順で答えが変わる</b>。
	 * 捨てるための1周を先に回しておく。
	 * </p>
	 */
	@BeforeAll
	static void warm () {

		for (int depth : new int[]{2, 4, 8}) {

			Router router = router(1000, depth);
			String path = path(depth);

			router.seal();

			for (int i = 0; i < WARMUP; i++) {
				router.match("GET", path);
				router.match("GET", "/no/such/path/here");
			}

		}

	}

	@AfterAll
	static void report () {

		Bench.report();

	}

	@Test
	@DisplayName("ルートが 100 倍あっても費用は変わらない（D-6）")
	void constantInRouteCount () {

		long ten = match("routes=10 depth=4", 10, 4);
		long hundred = match("routes=100 depth=4", 100, 4);
		long thousand = match("routes=1000 depth=4", 1000, 4);

		/*
		 * <b>ここが崩れたら木を降りていない。</b>ルートを1本ずつ試す実装に戻ると、
		 * 1000 本ぶんの比較が要る。マッチ結果をキャッシュするかどうか（D-6）は
		 * この数字で決めた：<b>1000 本でも 10 本と同じ費用</b>なので、
		 * キャッシュ（＝利用者の入力を鍵にする表）を持つ理由が無い
		 */
		assertTrue(Math.abs(hundred - ten) <= SLACK_BYTES
			, "ルート数で費用が変わっている: 10本=%d / 100本=%d".formatted(ten, hundred));

		assertTrue(Math.abs(thousand - ten) <= SLACK_BYTES
			, "ルート数で費用が変わっている: 10本=%d / 1000本=%d".formatted(ten, thousand));

	}

	@Test
	@DisplayName("パスの深さに対して線形以下（要件 NF-P-04）")
	void linearInDepth () {

		long two = match("routes=10 depth=2", 10, 2);
		long four = match("routes=10 depth=4", 10, 4);
		long eight = match("routes=10 depth=8", 10, 8);

		// 深さが4倍でも、費用は4倍まで
		assertTrue(eight <= two * 4, "深さ8=%d / 深さ2=%d".formatted(eight, two));
		assertTrue(four <= two * 2, "深さ4=%d / 深さ2=%d".formatted(four, two));

		// 上限（実測 704 byte）。ここを超えたら、1回のマッチで何かを作り始めている
		assertTrue(eight <= 1024, "深さ8で %d byte 使っている".formatted(eight));

	}

	@Test
	@DisplayName("どのルートにも当たらなくても費用は変わらない")
	void unmatched () {

		Router router = router(1000, 4);
		String path = path(4);

		router.seal();

		long matched = Bench.record("match（当たる） routes=1000 depth=4", WARMUP, MEASURE
			, () -> router.match("GET", path));

		long missed = Bench.record("match（当たらない） routes=1000 depth=4", WARMUP, MEASURE
			, () -> router.match("GET", "/no/such/path/here"));

		/*
		 * <b>外れたときのほうが高い実装がある。</b>木を降りきってから
		 * 全部を舐め直すもの（404 のときだけ遅い）は、<b>叩かれると効く</b>
		 */
		assertTrue(missed <= matched, "当たらないほうが高い: %d / %d".formatted(missed, matched));

	}

	/**
	 * 測る
	 *
	 * @param name		表に出す名前
	 * @param routes	ルート数
	 * @param depth		パスの深さ
	 * @return 1回あたりの byte 数
	 */
	private static long match (String name, int routes, int depth) {

		Router router = router(routes, depth);
		String path = path(depth);

		router.seal();

		return Bench.record("match " + name, WARMUP, MEASURE, () -> router.match("GET", path));

	}

	/**
	 * ルータを作る
	 *
	 * @param routes	ルート数
	 * @param depth		パスの深さ
	 * @return ルータ
	 */
	private static Router router (int routes, int depth) {

		Router router = new Router();
		String prefix = prefix(depth);

		for (int i = 0; i < routes; i++) {
			router.get(prefix + "/r" + i, NOOP);
		}

		// 最後の1つはパス変数（実際のアプリはたいてい ID で終わる）
		router.get(prefix + "/{id}", NOOP);

		return router;

	}

	/**
	 * 測るパス
	 *
	 * @param depth	パスの深さ
	 * @return パス
	 */
	private static String path (int depth) {

		return prefix(depth) + "/12345";

	}

	/**
	 * 最後の1つを除いたところまで
	 *
	 * @param depth	パスの深さ
	 * @return パス
	 */
	private static String prefix (int depth) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < depth - 1; i++) {
			sb.append("/seg").append(i);
		}

		return sb.toString();

	}

}
