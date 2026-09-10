package io.jimble.web.bench;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;
import io.jimble.web.router.Router;
import io.jimble.web.server.ServerConf;
import io.jimble.web.support.Fakes;

import com.typesafe.config.ConfigFactory;

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
 * <h2>内訳は「段ごとに積んで、その差」で出す</h2>
 * <p>
 * <b>部品を1つずつ別に測って並べると、合計と一致しない分が黙って残る。</b>
 * 実際そうなっていて、<b>1リクエスト 約 8,100 byte のうち 約 3,700 byte（45%）に名前が無かった</b>。
 * 上限まで 2,000 byte ほどしか余裕が無いのに、<b>いちばん大きい塊が「何か分からないもの」</b>という状態である。
 * これでは、明日誰かが 800 byte 足しても<b>どこで増えたのかを誰も言えない</b>
 * ——byte で見張ると決めた（D-125）意味が、そこだけ効いていない。
 * </p>
 * <p>
 * そこで<b>途中まで実行したものを段ごとに測り、隣との差を名前にする</b>。
 * 差を全部足すと最後の段（＝1リクエスト全体）になるので、
 * <b>名前の付いていない残りは構造上できない</b>。
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
	 * 実測は 約 8,100 byte（Gradle 経由・マッチしたとき。測るたびに数十 byte 動く）。
	 * <b>目標ではなく壁である。</b>
	 * 4割ほど余裕を持たせてあるのは、<b>直していないのに赤くなる日を作らない</b>ためである
	 * （JDK の細かい版が変わるだけでも数十 byte は動く）。
	 * ここを削る努力をするための数字ではなく、<b>知らないうちに増えたことに気づく</b>ための数字。
	 * 実際の値は {@code build/bench/bench.txt} に出る。
	 * </p>
	 */
	private static final long MAX_BYTES = 10_240;

	/** ルータ */
	private static Router router;

	/*
	 * 測ったものの置き場（{@link #stage} の説明）。
	 * <b>読まない。</b>JIT に「使われている」と思わせるためだけにある
	 */
	private static Object keepSource;
	private static Object keepSink;

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
	@DisplayName("内訳：段ごとに積んで、隣との差に名前を付ける")
	void breakdown () {

		/*
		 * <b>途中まで実行したものを段ごとに測る。</b>
		 * 隣との差が、その段でやっていることの費用になる。
		 * 差を全部足すと最後の段（1リクエスト全体）になるので、
		 * <b>「名前の付いていない残り」ができない</b>
		 */
		long sourceSink = Bench.record("1. テスト用の入力口・出力口だけ（jimble の費用ではない）"
			, WARMUP, MEASURE, () -> stage(1));

		long created = Bench.record("2. ＋ Context を作る"
			, WARMUP, MEASURE, () -> stage(2));

		long routed = Bench.record("3. ＋ ルーティング1回"
			, WARMUP, MEASURE, () -> stage(3));

		long ran = Bench.record("4. ＋ ハンドラを走らせる枠（ScopedValue）"
			, WARMUP, MEASURE, () -> stage(4));

		long closed = Bench.record("5. ＋ 後始末（＝1リクエスト全体）"
			, WARMUP, MEASURE, () -> stage(5));

		long withoutAccessLog;

		try {

			/*
			 * <b>アクセスログはその場で切って引く。</b>{@code Log.access} を単体で呼んで測ると、
			 * <b>実際に組み立てている項目（実行ID・SQL 集計・ホスト情報）が揃わない</b>——
			 * 前はそれで測っていて、<b>1,000 byte ほど安く見えていた</b>。
			 */
			Conf.replace(ConfigFactory.parseString("server { access_log = false }"));

			withoutAccessLog = Bench.record("（参考）1リクエスト（アクセスログなし）"
				, WARMUP, MEASURE, () -> stage(5));

		} finally {
			Conf.reload();
		}

		long accessLog = closed - withoutAccessLog;

		Bench.derived("　うち Context の生成（Request / Response / 実行ID / 区間）", created - sourceSink);
		Bench.derived("　うち ルーティング", routed - created);
		Bench.derived("　うち ハンドラを走らせる枠", ran - routed);
		Bench.derived("　うち 後始末", closed - ran);
		Bench.derived("　　　うち アクセスログ1行（要件 NF-O-02）", accessLog);
		Bench.derived("　　　うち メトリクス・トレース・セッションの後始末", closed - ran - accessLog);

		/*
		 * <b>段が減る（前の段より安くなる）ことは無い。</b>
		 * 起きたら測り方が壊れている——差が負になると、
		 * <b>足しても全体にならない</b>ので内訳として読めなくなる
		 */
		assertTrue(sourceSink <= created && created <= routed && routed <= ran && ran <= closed
			, "段ごとの積み上げが逆転している: %d / %d / %d / %d / %d"
				.formatted(sourceSink, created, routed, ran, closed));

		/*
		 * <b>栓が効いていることも、ここで一緒に見る。</b>
		 * 切っても減らないなら、{@code server.access_log} は名前だけの設定である（D-130）。
		 *
		 * <b>「減った」では足りない。</b>最初は {@code withoutAccessLog < closed} と書いていたが、
		 * <b>栓を外す変異が生き残った</b>——栓が効いていなくても、
		 * 測定の揺れ（数十 byte）で偶然そちらが小さくなることがあるためである。
		 * <b>実測は全体の 4割</b>なので、5分の1を下回ったら効いていないとみなす。
		 */
		assertTrue(accessLog >= closed / 5
			, "アクセスログを切っても大きくは減っていない（栓が効いていない）: %d → %d（差 %d）"
				.formatted(closed, withoutAccessLog, accessLog));

		assertTrue(ServerConf.accessLog(), "設定を戻せていない（あとのテストが理由なく落ちる）");

	}

	/**
	 * 1リクエストを途中の段まで実行する
	 *
	 * <p>
	 * <b>閉じない段があるのはわざとである。</b>{@code Context} はどこにも登録されないので、
	 * 閉じずに捨ててもゴミになるだけで、あとの測定には影響しない。
	 * </p>
	 *
	 * @param upTo	どの段まで進めるか（1〜5）
	 */
	private static void stage (int upTo) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/posts/1");
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		/*
		 * <b>作ったものを外に出す。</b>使われていないと判定されると、
		 * JIT が生成そのものを消してしまい<b>0 byte になる</b>
		 * （消えたのか安いのかが区別できなくなる）
		 */
		keepSource = source;
		keepSink = sink;

		if (upTo == 1) {
			return;
		}

		WebContext context = new WebContext(source, sink);
		keepSource = context;

		if (upTo == 2) {
			return;
		}

		context.route(router.match("GET", "/posts/1"));

		if (upTo == 3) {
			return;
		}

		context.run(() -> { });

		if (upTo == 4) {
			return;
		}

		context.close();

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
