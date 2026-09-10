package approval.ops;

import io.jimble.core.lifecycle.Shutdown;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;
import io.jimble.otel.JimbleOtel;
import io.jimble.util.conf.Conf;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.bot.BotBlocker;
import io.jimble.web.call.CallRequest;
import io.jimble.web.call.CallResponse;
import io.jimble.web.context.WebContext;
import io.jimble.web.ratelimit.RateLimit;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * サンプル：動いている中を見る（残件 N-3）
 *
 * <p><b>DB を使わない。</b>そのまま起動できる。</p>
 *
 * <pre>
 * ./gradlew :examples:approval-ops:run
 * </pre>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET/HEAD /health_check}</td>
 *       <td><b>ヘルスチェック</b>（NF-O-03）／止め始めたら 503（D-91）</td></tr>
 *   <tr><td>{@code GET /metrics}</td><td><b>メトリクス</b>（NF-O-04）</td></tr>
 *   <tr><td>{@code GET /slow}</td><td><b>トレース</b>（NF-O-05）</td></tr>
 *   <tr><td>{@code GET /limited}</td><td><b>流量制限</b>（F-R-15）</td></tr>
 *   <tr><td>{@code GET /}</td><td>Bot 判定・UserAgent（F-W-14 / F-W-16 / F-H-05）</td></tr>
 *   <tr><td>{@code GET /strict}</td><td>Bot は 403（{@link BotBlocker}）</td></tr>
 *   <tr><td>{@code GET /internal}</td><td><b>内部呼び出し</b>（F-W-27）</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>{@code /metrics} も {@code /health_check} も、フレームワークは生やさない</b>
 * （要件 NF-O-03 / D-106）。アプリが1行書く。
 * 枠組みが握ると<b>閉じたいときに閉じられない</b>し、
 * 止める順番（先にヘルスチェックだけ落とす）をアプリから決められない。
 * </p>
 * <p>
 * <b>トレースは依存に入れただけでは出ない。</b>{@code otel.endpoint} を書いたときだけ
 * {@link JimbleOtel#install} を呼ぶ。<b>書いていないのに collector を探しに行くと、
 * 起動のたびに接続エラーがログに出る</b>。
 * </p>
 */
public class OpsApp extends JimbleApp {

	/** 設定の鍵：トレースの出し先 */
	static final String KEY_OTEL_ENDPOINT = "otel.endpoint";

	/** メトリクスの名前：起きてからの秒数 */
	static final String METRIC_UPTIME = "app.uptime_seconds";

	/** 流量制限：何回まで */
	static final int LIMIT = 3;

	/** 流量制限：何秒のあいだ */
	static final int LIMIT_SECONDS = 10;

	/** 起きた時刻（ナノ秒） */
	private static final AtomicLong STARTED_AT = new AtomicLong(System.nanoTime());

	/**
	 * ルート定義
	 */
	public OpsApp () {

		registerGauges();

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		healthCheck();

		get("/metrics", context -> context.response().json(Metrics.snapshot()));

		get("/slow", OpsApp::slow);

		get("/limited", context -> context.response().text("ok"))
			.attribute(RateLimit.KEY
				, RateLimit.perIp(LIMIT, Duration.ofSeconds(LIMIT_SECONDS)));

		get("/", OpsApp::visitor);

		/*
		 * ボットを弾く（要件 F-W-14）。
		 * <b>既定の弾き方は枠組みが持っていない。</b>
		 * 一律に弾くと<b>検索エンジンまで追い返す</b>ので、アプリが決める。
		 */
		path("/strict", () -> {
			before(BotBlocker.forbidden());
			get("", context -> context.response().text("人だけが読めるところ"));
		});

		get("/internal", OpsApp::internal);

	}

	/**
	 * 自分で測る数字を登録する（要件 NF-O-04）
	 *
	 * <p>
	 * <b>ここで I/O をしてはいけない。</b>DB を触ると、
	 * <b>DB が詰まっているときに限ってメトリクスも取れなくなる</b>——
	 * いちばん見たいときに見えない（D-124）。
	 * </p>
	 *
	 * <p>
	 * <b>メソッドに切り出してあるのは、{@code Metrics.reset()} が
	 * 登録した gauge ごと消す</b>ためである。消したあとに呼び直せる形にしておく。
	 * </p>
	 */
	static void registerGauges () {

		Metrics.gauge(METRIC_UPTIME, () -> (System.nanoTime() - STARTED_AT.get()) / 1_000_000_000L);

	}

	/**
	 * ヘルスチェック（要件 NF-O-03 / D-91）
	 *
	 * <p>
	 * <b>止め始めたら、まずここだけ落とす。</b>ロードバランサがこれを見て
	 * 新しい人を送らなくなり、そのあいだに<b>処理中のリクエストは返しきる</b>。
	 * </p>
	 * <p>
	 * <b>HEAD も要る。</b>監視の道具は本文を要らないことが多い。
	 * </p>
	 */
	private void healthCheck () {

		get("/health_check", OpsApp::health);
		head("/health_check", OpsApp::health);

	}

	/**
	 * 生きているか
	 *
	 * @param context	コンテキスト
	 */
	private static void health (WebContext context) {

		context.response().send(Shutdown.isStopping() ? 503 : 200);

	}

	/**
	 * 少し時間のかかる処理（トレースの題材。要件 NF-O-05）
	 *
	 * @param context	コンテキスト
	 */
	private static void slow (WebContext context) {

		/*
		 * <b>登録していなければ Span.NOOP が返る。</b>
		 * {@code if (Tracing.enabled())} で囲む必要は無い——
		 * 囲まなくても 1回あたり 0 byte である（`TracingTest` が見張っている）。
		 */
		try (var span = Tracing.start("在庫を確かめる", SpanKind.internal)) {

			span.attribute("kind", "sample");

			context.response()
				.json("traceparent", Tracing.traceparent())
				.json("enabled", Tracing.enabled());

		}

	}

	/**
	 * 来た人のこと（要件 F-W-16 / F-H-05）
	 *
	 * @param context	コンテキスト
	 */
	private static void visitor (WebContext context) {

		/*
		 * <b>ボットかどうかはアクセスログにも出る</b>（要件 F-H-05）。
		 * {@code server.bot_access_log = true} なら、
		 * ボットの行は {@code access.bot} という別のロガーへ出る——
		 * まとめて出すと<b>クローラの分で人のアクセスが埋もれる</b>。
		 */
		context.response()
			.json("bot", context.request().isBotAccess())
			.json("device", context.request().userAgent().getDevice())
			.json("os", context.request().userAgent().getOS());

	}

	/**
	 * HTTP を通さずに別のルートを呼ぶ（要件 F-W-27）
	 *
	 * <p>
	 * <b>自分のサーバーに HTTP で繋ぎ直さない。</b>繋ぎ直すと、
	 * <b>自分の接続を1本食う</b>うえに、詰まっているときに自分で自分を待つ。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	private static void internal (WebContext context) {

		CallResponse response = context.dispatcher()
			.call(context, CallRequest.of("GET", "/metrics"));

		/*
		 * <b>内部呼び出しはアクセスログにもメトリクスにも出ない。</b>
		 * 出すと<b>1リクエストが2回に数えられる</b>
		 */
		context.response()
			.json("called", response.code())
			.json("has_counter", response.isJsonObject() && response.json().containsKey("counter"));

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		installTracing();

		JimbleServer.start(new OpsApp());

	}

	/**
	 * 出し先が書いてあればトレースを繋ぐ
	 *
	 * <p>
	 * <b>依存に入れただけでは何も起きない。</b>ここで呼んで初めて出る。
	 * </p>
	 */
	static void installTracing () {

		String endpoint = Conf.conf().getString(KEY_OTEL_ENDPOINT, "");

		if (endpoint.isEmpty()) {
			return;
		}

		JimbleOtel.install("approval-ops", endpoint);

	}

}
