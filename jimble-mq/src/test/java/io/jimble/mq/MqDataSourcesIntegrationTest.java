package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.ConnectionCount;
import io.jimble.db.DB;
import io.jimble.db.DBSource;
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * キューを置くデータソースを絞る（{@code db.<名前>.mq}・{@code mq.queues.<キュー名>.data_sources}・主だけのキュー）
 *
 * <p>
 * 共通の DB（主）と店の DB を並べ、キューは店の DB にだけ置く、という使い方のため。
 * 置かないデータソースには、表を作らず、取り出し・迷子の戻し・件数の数えにも行かない。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqDataSourcesIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_ds_test";

	/** 主 */
	static final String MAIN = "jimble_test";

	/** もう1つ */
	static final String OTHER = "jimble_test_sub";

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
	 * 主だけに置くキューの処理
	 */
	public static class MainOnlyExecutor extends DoneExecutor {

		@Override public boolean mainOnly () { return true; }

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
		DONE.set(0);

	}

	@AfterAll
	static void stop () {

		load("");
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
			}
		}
		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("書かなければ、これまでどおり全部のデータソースに置く")
	void defaultIsAll () {

		load("");

		MqQueue queue = new MqQueue(QUEUE);
		queue.install();

		assertEquals(List.of(MAIN, OTHER), names(queue.dataSources()));
		assertTrue(hasTable(MAIN) && hasTable(OTHER));

	}

	@Test
	@DisplayName("db.<名前>.mq = false のデータソースには、表を作らず、取り出し・迷子の戻し・件数の数えに行かない")
	void mqFalse () throws Exception {

		load("db.jimble_test_sub.mq = false");

		MqQueue queue = new MqQueue(QUEUE);
		queue.install();

		assertEquals(List.of(MAIN), names(queue.dataSources()));
		assertFalse(hasTable(OTHER), "置かないデータソースに表を作った");

		MqRegistry.add(DoneExecutor::new);

		try (ConnectionCount other = ConnectionCount.install(OTHER)) {

			// 表が無いので、見に行けば例外になる（これまでは起動時の recoverStale で落ち、pendingCount も落ちた）
			assertEquals(0, queue.recoverStale());
			assertEquals(0, queue.pendingCount());

			run(queue, 800);

			assertEquals(0, other.count(), "置かないデータソースに接続を借りに行った");

		}

	}

	@Test
	@DisplayName("mq.queues.<キュー名>.data_sources で絞ると、ほかのデータソースからは取り出さない")
	void dataSourcesConfig () throws Exception {

		load("mq.queues.mq_ds_test.data_sources = [\"jimble_test_sub\"]");

		// どちらにも表があり、どちらにも行がある（主の行は、絞る前に積まれたもの）
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				MqTables.install(db, QUEUE);
				db.execute("DELETE FROM " + db.dialect().identifier(QUEUE));
				db.execute(("INSERT INTO %s (execute_type, mq_key, status, retry_count, data, created_at, updated_at)"
					+ " VALUES ('short_time', 'done', 'waiting', 0, '{}', NOW(), NOW())").formatted(db.dialect().identifier(QUEUE)));
			}
		}

		MqQueue queue = new MqQueue(QUEUE);
		assertEquals(List.of(OTHER), names(queue.dataSources()));

		MqRegistry.add(DoneExecutor::new);
		run(queue, 1500);

		assertEquals(1, DONE.get());
		assertEquals("waiting", status(MAIN), "絞った外のデータソースから取り出した");
		assertEquals("（無い）", status(OTHER), "処理し終えた行は消える");

	}

	@Test
	@DisplayName("置いていないデータソースの DB に積もうとしたら、例外にする")
	void putToExcludedFails () {

		load("db.jimble_test_sub.mq = false");

		new MqQueue(QUEUE).install();

		try (DB other = DBUtil.getDB(OTHER)) {
			MqException thrown = assertThrows(MqException.class, () -> new DoneExecutor().put(other, new Data()));
			assertTrue(thrown.getMessage().contains("db.jimble_test_sub.mq"), thrown.getMessage());
		}

		try (DB main = DBUtil.getDB(MAIN)) {
			new DoneExecutor().put(main, new Data());     // 置いているほうには積める
		}

	}

	@Test
	@DisplayName("data_sources に知らない名前を書いたら、例外にする")
	void unknownDataSource () {

		load("mq.queues.mq_ds_test.data_sources = [\"shop_x\"]");

		assertThrows(MqException.class, () -> new MqQueue(QUEUE).dataSources());

	}

	@Test
	@DisplayName("主だけのキューは、db.<名前>.mq = false でも主に置き、主だけから取り出し、主にしか積めない")
	void mainOnly () throws Exception {

		load("db.jimble_test.mq = false");

		MqQueue queue = MqQueue.mainOnly(QUEUE);
		queue.install();

		assertTrue(queue.isMainOnly());
		assertEquals(List.of(MAIN), names(queue.dataSources()));
		assertTrue(hasTable(MAIN));
		assertFalse(hasTable(OTHER), "主だけのキューの表を、ほかに作った");

		try (DB other = DBUtil.getDB(OTHER)) {
			assertThrows(MqException.class, () -> new MainOnlyExecutor().put(other, new Data()));
		}

		try (DB main = DBUtil.getDB(MAIN)) {
			new MainOnlyExecutor().put(main, new Data());
		}

		MqRegistry.add(MainOnlyExecutor::new);
		run(queue, 1500);

		assertEquals(1, DONE.get());

	}

	// region ヘルパー

	/**
	 * 設定を足して読み込み直し、キューの表を消す
	 *
	 * @param hocon	足す設定
	 */
	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();

		Config base = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString(hocon + """

			mq.poll_max = 100ms
			""").withFallback(withOtherDataSource(base)));
		DBUtil.load(Conf.conf().config(), MqDataSourcesIntegrationTest.class);

		// 表を消したら、表の一覧の写しも読み直す（読み直さないと「ある」と見て作らない）
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

	}

	/**
	 * 主の設定を写して、もう1つのデータソース（jimble_test_sub）を足す
	 *
	 * <p>このモジュールのテストの設定には主しか無いので、同じサーバーの別の DB を指すものを足す。</p>
	 *
	 * @param base	設定
	 * @return	足した設定
	 */
	private static Config withOtherDataSource (Config base) {

		Config main = base.getConfig("db." + MAIN);
		String url = main.getString("url").replaceFirst("/" + MAIN + "(\\?|$)", "/" + OTHER + "$1");

		Config other = main
			.withoutPath("create_database_sql")
			.withValue("main", ConfigValueFactory.fromAnyRef(false))
			.withValue("url", ConfigValueFactory.fromAnyRef(url));

		return base.withValue("db." + OTHER, other.root());

	}

	/**
	 * しばらく回して止める
	 *
	 * @param queue		キュー
	 * @param millis	回す時間
	 * @throws InterruptedException	割り込まれた場合
	 */
	private static void run (MqQueue queue, long millis) throws InterruptedException {

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		try {
			Thread.sleep(millis);
		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

	}

	private static List<String> names (List<DBSource> sources) {

		return sources.stream().map(DBSource::name).toList();

	}

	private static boolean hasTable (String name) {

		try (DB db = DBUtil.getDB(name)) {
			return db.selectList(db.dialect().tableCommentsSql()).stream()
				.anyMatch(row -> QUEUE.equalsIgnoreCase(row.getString("table_name")));
		}

	}

	private static String status (String name) {

		try (DB db = DBUtil.getDB(name)) {
			return db.select("SELECT status FROM " + db.dialect().identifier(QUEUE))
				.map(row -> row.getString("status")).orElse("（無い）");
		}

	}

	// endregion

}
