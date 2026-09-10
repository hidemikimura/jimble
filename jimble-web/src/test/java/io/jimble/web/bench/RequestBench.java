package io.jimble.web.bench;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;
import io.jimble.web.router.Router;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1リクエストの費用（要件 NF-P-05 / NF-P-06）
 *
 * <pre>
 * ./gradlew :jimble-web:bench
 * </pre>
 *
 * <h2>何を見ているか</h2>
 * <p>
 * <b>フレームワークが1リクエストごとに何 byte 作るか。</b>
 * {@code Context} / {@code Request} / {@code Response} 以外に大きな割り当てを作らない、
 * というのが要件 NF-P-05 である。ここを見張っていないと、
 * <b>1リクエストに 1KB ずつ足す変更</b>は誰にも気づかれずに入る（1本では速いままなので）。
 * </p>
 *
 * <h2>入っているもの・いないもの</h2>
 * <ul>
 *   <li><b>アクセスログ（要件 NF-O-02）は入っている。</b>既定で出るので、
 *       抜いて測ると実際より小さく見える。<b>いちばん大きいのはここ</b>で、
 *       表にその内訳も出している。
 *       {@code server.access_log = false} で切れる（D-130）が、
 *       <b>ここは既定のまま測る</b>——切った状態を基準にすると、
 *       <b>既定のまま動いているアプリの費用が誰にも見えなくなる</b></li>
 *   <li><b>テスト用の入力口・出力口（{@link Fakes}）も入っている。</b>
 *       実物の Helidon の入力口とは違うので、<b>絶対値をそのまま「1リクエストの費用」と読まないこと</b>。
 *       ここで見たいのは<b>前と比べて増えていないか</b>である</li>
 *   <li><b>DB もテンプレートも入っていない。</b>アプリの仕事はここでは測らない</li>
 * </ul>
 */
@Tag("bench")
class RequestBench {

	/** 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	/** 空回しする回数 */
	private static final int WARMUP = 50_000;

	/** 測る回数 */
	private static final int MEASURE = 100_000;

	/**
	 * 1リクエストの上限（byte）
	 *
	 * <p>
	 * 実測は 7,969 byte（Gradle 経由・マッチしたとき）。<b>目標ではなく壁である。</b>
	 * 4割ほど余裕を持たせてあるのは、<b>直していないのに赤くなる日を作らない</b>ためである
	 * （JDK の細かい版が変わるだけでも数十 byte は動く）。
	 * ここを削る努力をするための数字ではなく、<b>知らないうちに増えたことに気づく</b>ための数字。
	 * 実際の値は {@code build/bench/bench.txt} に出る。
	 * </p>
	 */
	private static final long MAX_BYTES = 10_240;

	/** ルータ */
	private static Router router;

	@BeforeAll
	static void warm () {

		router = new Router();
		router.get("/posts/{id}", NOOP);
		router.seal();

		// いちばん最初に測ったものだけ高く出るので、捨てるための1周を回しておく
		for (int i = 0; i < WARMUP; i++) {
			request(true);
		}

	}

	@AfterAll
	static void report () {

		Bench.report();

	}

	@Test
	@DisplayName("1リクエストの割り当てが上限を超えない（要件 NF-P-05）")
	void perRequest () {

		long matched = Bench.record("1リクエスト（ルートに当たる）", WARMUP, MEASURE, () -> request(true));
		long unmatched = Bench.record("1リクエスト（当たらない）", WARMUP, MEASURE, () -> request(false));

		assertTrue(matched <= MAX_BYTES, "1リクエストで %d byte 使っている（上限 %d）".formatted(matched, MAX_BYTES));
		assertTrue(unmatched <= MAX_BYTES, "1リクエストで %d byte 使っている（上限 %d）".formatted(unmatched, MAX_BYTES));

	}

	@Test
	@DisplayName("内訳（落とす材料にはしない）")
	void breakdown () {

		/*
		 * <b>どこが大きいかを表に出しておく。</b>「1リクエスト 7KB」だけ見ても、
		 * 削れるところがあるのか、それとも全部必要なのかが分からない
		 */
		Bench.record("　うち テスト用の入力口・出力口を作るところ", WARMUP, MEASURE
			, () -> Fakes.context("GET", "/posts/1"));

		Bench.record("　うち アクセスログ1行（要件 NF-O-02）", WARMUP, MEASURE, () -> {

			Data fields = new Data();
			fields.put("method", "GET");
			fields.put("path", "/posts/1");
			fields.put("query", "");
			fields.put("status", 200);
			fields.put("elapsed", 0.1);
			fields.put("matched", true);
			fields.put("bot", false);

			Log.access("GET /posts/1 200", fields, false);

		});

		Bench.record("　うち ルーティング1回", WARMUP, MEASURE, () -> router.match("GET", "/posts/1"));

	}

	/**
	 * 1リクエスト分（作る → ハンドラ → 閉じる）
	 *
	 * @param matched	ルートに当てるか
	 */
	private static void request (boolean matched) {

		try (WebContext context = Fakes.context("GET", "/posts/1")) {

			if (matched) {
				context.route(router.match("GET", "/posts/1"));
			}

			context.run(() -> { });

		}

	}

}
