package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.ConnectionCount;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仕事のある DB だけを見に行く（{@code mq.hint}・{@code mq.sweep_interval}）
 *
 * <p>
 * データソースを 10 個並べ、1 つにだけ積む。取り出し役がほかのデータソースを見に行くのは、
 * 全部を回る周（{@code mq.sweep_interval}）の分だけになる。印が無くても、全部を回る周の内に取り出される。
 * </p>
 *
 * <p><b>開発用 DB と Redis が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqHintsIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_hint_test";

	/** 並べるデータソース（主のほかに） */
	static final List<String> SHOPS = IntStream.range(0, 10).mapToObj(i -> "jimble_mq_h" + i).toList();

	/** 積むデータソース */
	static final String BUSY = SHOPS.getFirst();

	/* 処理した数 */
	static final AtomicInteger DONE = new AtomicInteger();

	/**
	 * すぐ終わる処理
	 */
	public static class DoneExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "done"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			DONE.incrementAndGet();
			return MqStatus.completed;

		}

	}

	/**
	 * 止められる通知
	 */
	static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }
		@Override public void doCancel () { cancelled = true; }

	}

	@AfterEach
	void clear () {

		MqRegistry.clear();
		MqHints.clear();
		DONE.set(0);

	}

	@AfterAll
	static void stop () {

		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("印を使えば、1 つに積んでも、ほかのデータソースへは全部を回る周の分しか行かない")
	void onlyHintedDataSourcesAreVisited () throws Exception {

		load("""
			mq.hint = memory
			mq.sweep_interval = 2s
			""");

		Map<String, Integer> visits = runAndCount(30);

		visits.forEach((name, count) -> assertTrue(count <= 3
			, "%s に %d 回見に行った（全部を回る周は 3 秒で 2 回ほど）: %s".formatted(name, count, visits)));

	}

	@Test
	@DisplayName("印を使わなければ（既定）、これまでどおり毎周すべてを見る")
	void offVisitsEveryDataSource () throws Exception {

		load("");

		Map<String, Integer> visits = runAndCount(30);

		visits.forEach((name, count) -> assertTrue(count > 3
			, "%s に %d 回しか見に行っていない（印を使わないなら毎周見る）: %s".formatted(name, count, visits)));

	}

	@Test
	@DisplayName("印の無い行（印が落ちた・ほかのプロセスが印を付けずに積んだ）も、全部を回る周の内に取り出される")
	void rowWithoutHintIsPickedUpBySweep () throws Exception {

		load("""
			mq.hint = memory
			mq.sweep_interval = 1s
			""");

		run(() -> {
			insertWithoutHint(SHOPS.get(5));
			return waitUntil(() -> DONE.get() == 1, 4_000);
		});

		assertEquals(1, DONE.get());

	}

	@Test
	@DisplayName("Redis の印（ほかのプロセスが積んだ）だけで、全部を回る周を待たずに取り出される")
	void redisHintFromAnotherProcess () throws Exception {

		load("""
			mq.hint = redis
			mq.sweep_interval = 30s
			""");

		run(() -> {
			insertWithoutHint(SHOPS.get(7));
			// ほかのプロセスが積んだときと同じ（このプロセスのメモリには印を付けず、Redis にだけ入れる）
			MqHints.sendRemote(QUEUE, SHOPS.get(7), System.currentTimeMillis());
			return waitUntil(() -> DONE.get() == 1, 3_000);
		});

		assertEquals(1, DONE.get(), "Redis の印で取り出されていない（全部を回る周は 30 秒後）");

	}

	// region ヘルパー

	/**
	 * 回しながら BUSY にだけ積み、ほかのデータソースに接続を借りに来た回数を数える
	 *
	 * @param rows	積む数
	 * @return	データソース名 → 回数
	 * @throws Exception	失敗した場合
	 */
	private static Map<String, Integer> runAndCount (int rows) throws Exception {

		List<ConnectionCount> counts = new ArrayList<>();
		for (String name : SHOPS.subList(1, SHOPS.size())) {
			counts.add(ConnectionCount.install(name));
		}

		try {

			run(() -> {

				// 立ち上がり（recoverStale と最初の全部を回る周）の分を外して数える
				counts.forEach(ConnectionCount::reset);

				try (DB busy = DBUtil.getDB(BUSY)) {
					for (int i = 0; i < rows; i++) {
						new DoneExecutor().put(busy, new Data().putData("n", i));
						Thread.sleep(100);
					}
				}

				return waitUntil(() -> DONE.get() == rows, 5_000);

			});

			assertEquals(rows, DONE.get(), "全部は処理されませんでした");

			Map<String, Integer> visits = new LinkedHashMap<>();
			for (int i = 0; i < counts.size(); i++) {
				visits.put(SHOPS.get(i + 1), counts.get(i).count());
			}

			return visits;

		} finally {
			counts.forEach(ConnectionCount::close);
		}

	}

	/**
	 * キューを回し、立ち上がってから中身を流して止める
	 *
	 * @param body	回している間にすること（終わったら true）
	 * @throws Exception	失敗した場合
	 */
	private static void run (Body body) throws Exception {

		MqRegistry.add(DoneExecutor::new);

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = new MqQueue(QUEUE).startNoWait(cancel);

		try {
			Thread.sleep(300);
			body.run();
		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

	}

	/** 回している間にすること */
	@FunctionalInterface
	interface Body {

		boolean run () throws Exception;

	}

	private static boolean waitUntil (BooleanSupplier condition, long millis) throws InterruptedException {

		long deadline = System.currentTimeMillis() + millis;

		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		return condition.getAsBoolean();

	}

	/**
	 * 印を付けずに 1 行入れる（積む API を通さない）
	 *
	 * @param name	データソース名
	 */
	private static void insertWithoutHint (String name) {

		try (DB db = DBUtil.getDB(name)) {
			db.execute(("INSERT INTO %s (execute_type, mq_key, status, retry_count, data, created_at, updated_at)"
				+ " VALUES ('short_time', 'done', 'waiting', 0, '{}', NOW(), NOW())").formatted(db.dialect().identifier(QUEUE)));
		}

	}

	/**
	 * 主と 10 個のデータソースで読み込み直し、キューの表を作り直す（Redis の印も消す）
	 *
	 * @param hocon	足す設定
	 */
	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();

		Config config = Conf.conf().config();
		for (String name : SHOPS) {
			config = TwoDataSources.withCopy(config, name);
		}

		Conf.replace(ConfigFactory.parseString(hocon + """

			mq.poll_max = 100ms
			mq.thread_count.short_time = 2
			mq.queues.mq_hint_test.data_sources = %s
			""".formatted(SHOPS.stream().map(name -> "\"" + name + "\"").toList())).withFallback(config));

		DBUtil.load(Conf.conf().config(), MqHintsIntegrationTest.class);

		for (String name : SHOPS) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

		new MqQueue(QUEUE).install();

		io.jimble.db.redis.RedisClient.client().getKeys().delete(MqHints.KEY_PREFIX + QUEUE);

	}

	// endregion

}
