package io.jimble.web.server;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * アクセスログのテスト
 *
 * <p>要件 F-U-05 / NF-O-02：実行時間・SQL実行回数・SQL実行時間・実行IDが必ず出ること。</p>
 */
class AccessLogTest {

	/**
	 * 記録したログ1件
	 *
	 * @param loggerName	ロガー名
	 * @param level			レベル
	 * @param message		メッセージ
	 * @param data			構造化データ
	 */
	private record Entry (String loggerName, Level level, String message, Data data) {
	}

	/* 記録したログ */
	private final List<Entry> logs = new ArrayList<>();

	// docs:begin log-sink
	@BeforeEach
	void captureLog () {

		Log.sink((loggerName, level, message, data, throwable) ->
			logs.add(new Entry(loggerName, level, message, data)));

	}

	@AfterEach
	void restoreLog () {

		Log.resetSink();

		// 設定を触るテストがあるので、必ず戻す（残すと後ろのテストが理由なく落ちる）
		Conf.reload();

	}
	// docs:end

	/**
	 * アクセスログを取り出す
	 */
	private Entry accessLog () {

		// ボットは別のロガーへ出る（要件 F-H-05）
		return logs.stream()
			.filter(entry -> Log.LOGGER_ACCESS.equals(entry.loggerName())
				|| Log.LOGGER_ACCESS_BOT.equals(entry.loggerName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("アクセスログが出ていません: " + logs));

	}

	@Test
	@DisplayName("F-U-05 リクエストごとにアクセスログが1件出る")
	void accessLogIsEmitted () {

		JimbleApp app = new JimbleApp() {
			{
				get("/users/{id}", context -> context.response().code(201).send("ok"));
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		try (WebContext context = Fakes.context("GET", "/users/42")) {
			dispatcher.dispatch(context);
		}

		Entry entry = accessLog();

		assertEquals(Level.INFO, entry.level());
		assertEquals("GET /users/42 201", entry.message());
		assertEquals("GET", entry.data().getString("method"));
		assertEquals("/users/42", entry.data().getString("path"));
		assertEquals(201, entry.data().getInt("status"));
		assertTrue(entry.data().getBoolean("matched"));

	}

	@Test
	@DisplayName("NF-O-02 実行ID・SQL実行回数・SQL実行時間が必ず含まれる")
	void accessLogHasExecutionMetrics () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> context.response().send());
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		String executionId;

		try (WebContext context = Fakes.context("GET", "/x")) {
			executionId = context.executionId();
			dispatcher.dispatch(context);
		}

		Data data = accessLog().data();

		assertEquals(executionId, data.getString("request_id"), "実行IDがログに出ること");
		assertNotNull(data.get("sql_execute_count"));
		assertNotNull(data.get("sql_execute_time"));
		assertNotNull(data.get("elapsed"));
		assertNotNull(data.getData("local_info"), "ホスト情報が出ること");

	}

	@Test
	@DisplayName("SQL を実行した回数と時間がアクセスログに反映される")
	void sqlMetricsAppearInAccessLog () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> {
					// DB 接続なしで集計だけを検証する
					io.jimble.core.context.Context.recordSqlExecution(1_500_000L);
					io.jimble.core.context.Context.recordSqlExecution(2_500_000L);
					context.response().send();
				});
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		try (WebContext context = Fakes.context("GET", "/x")) {
			dispatcher.dispatch(context);
		}

		Data data = accessLog().data();

		assertEquals(2L, data.getLong("sql_execute_count"));
		assertEquals(4.0d, data.getDouble("sql_execute_time"), 0.001d, "ミリ秒で出ること");

	}

	@Test
	@DisplayName("ルート未マッチでもアクセスログは出る")
	void accessLogOnNotFound () {

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> context.response().send());
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		try (WebContext context = Fakes.context("GET", "/nope")) {
			dispatcher.dispatch(context);
		}

		Data data = accessLog().data();

		assertEquals(404, data.getInt("status"));
		assertFalse(data.getBoolean("matched"));

	}

	@Test
	@DisplayName("F-H-05 ボットのアクセスログは別のロガーへ出る")
	void botAccessLogIsSeparated () {

		/*
		 * まとめて出すと、ボットの分で人のアクセスが埋もれる。
		 * logback 側で access.bot を別のファイルに振り分ける。
		 */
		Fakes.FakeResponseSink bot = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/x").header("User-Agent", "Googlebot/2.1")
			, bot)) {

			context.response().send("ok");

		}

		Entry entry = logs.stream()
			.filter(e -> Log.LOGGER_ACCESS_BOT.equals(e.loggerName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("ボットのアクセスログが出ていません: " + logs));

		assertTrue(entry.data().getBoolean("bot"), entry.data().toString());

		logs.clear();

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/x")
				.header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
					+ " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36")
			, new Fakes.FakeResponseSink())) {

			context.response().send("ok");

		}

		assertTrue(logs.stream().anyMatch(e -> Log.LOGGER_ACCESS.equals(e.loggerName()))
			, "人のアクセスがボット扱いになっている: " + logs);

	}

	// region 切る（要件 NF-O-02 / D-130）

	@Test
	@DisplayName("D-130 server.access_log = false で1行も出ない")
	void accessLogCanBeTurnedOff () {

		Conf.replace(ConfigFactory.parseString("server { access_log = false }"));

		JimbleApp app = new JimbleApp() {
			{
				get("/x", context -> context.response().send("ok"));
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		try (WebContext context = Fakes.context("GET", "/x")) {
			dispatcher.dispatch(context);
		}

		assertTrue(
			logs.stream().noneMatch(entry -> Log.LOGGER_ACCESS.equals(entry.loggerName())
				|| Log.LOGGER_ACCESS_BOT.equals(entry.loggerName()))
			, "切ったのにアクセスログが出ています: " + logs);

	}

	@Test
	@DisplayName("D-130 ボットのアクセスログも一緒に止まる")
	void botAccessLogIsTurnedOffToo () {

		/*
		 * <b>別のロガーへ出ているので、片方だけ残るのがいちばんありそうな壊れ方である。</b>
		 * 「切ったのにログが増え続ける」は、切った人が気づくまで時間がかかる
		 */
		Conf.replace(ConfigFactory.parseString("server { access_log = false }"));

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/x").header("User-Agent", "Googlebot/2.1")
			, new Fakes.FakeResponseSink())) {

			context.response().send("ok");

		}

		assertTrue(
			logs.stream().noneMatch(entry -> Log.LOGGER_ACCESS_BOT.equals(entry.loggerName()))
			, "切ったのにボットのアクセスログが出ています: " + logs);

	}

	@Test
	@DisplayName("D-130 切ってもメトリクスは残る")
	void metricsSurviveWithoutAccessLog () {

		/*
		 * <b>ここを間違えると、速くするつもりで監視を消すことになる。</b>
		 * アクセスログとメトリクスは doClose() の中で並んでいるので、
		 * まとめて {@code if} の中に入れてしまうのはいかにも起きる
		 */
		Conf.replace(ConfigFactory.parseString("server { access_log = false }"));

		Metrics.reset();

		try (WebContext context = Fakes.context("GET", "/x")) {
			context.run(() -> { });
		}

		Data counter = Metrics.snapshot().getData("counter");

		assertEquals(1L, counter.getLong("http.request"), "メトリクスまで消えています");
		assertEquals(1L, counter.getLong("http.status.2xx"));

	}

	@Test
	@DisplayName("D-130 既定は出す（設定を書かなければ今までどおり）")
	void accessLogIsOnByDefault () {

		Conf.replace(ConfigFactory.empty());

		try (WebContext context = Fakes.context("GET", "/x")) {
			context.response().send("ok");
		}

		assertNotNull(accessLog());

	}

	// endregion

}
