package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * データソースを回る順を公平にする
 *
 * <p>
 * 取り出し役は、毎周<b>同じ順に</b>データソースを見て、手の空いたワーカーの数を使い切ったら抜けていた。
 * 前のデータソースに行が溜まり続けると、<b>後ろのデータソースの行は、前が空になるまで取り出されなかった</b>。
 * いまは、周ごとに見始めるデータソースを1つずつずらす。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class MqFairnessIntegrationTest {

	/** テスト用のキュー名 */
	static final String QUEUE = "mq_fair_test";

	/** 前のデータソースに積む数 */
	static final int MANY = 40;

	/* 処理した行のデータソース（処理した順） */
	static final List<String> ORDER = new CopyOnWriteArrayList<>();

	/**
	 * 少しだけ時間のかかる処理（どのデータソースの行かを記録する）
	 */
	public static class RecordingExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "record"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			ORDER.add(db.getDBName());

			try {
				Thread.sleep(20);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

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

	@BeforeAll
	static void load () {

		Conf.reload();
		Config base = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("""
			mq.thread_count.short_time = 1
			mq.poll_max = 100ms
			""").withFallback(TwoDataSources.withOther(base)));

		DBUtil.load(Conf.conf().config(), MqFairnessIntegrationTest.class);

		for (String name : new String[] { TwoDataSources.MAIN, TwoDataSources.OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

		new MqQueue(QUEUE).install();

	}

	@AfterAll
	static void stop () {

		for (String name : new String[] { TwoDataSources.MAIN, TwoDataSources.OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + db.dialect().identifier(QUEUE));
				DBVersion.reload(db);
			}
		}

		MqRegistry.clear();
		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("前のデータソースに行が溜まっていても、後ろのデータソースの行を待たせない（ワーカー1本）")
	void laterDataSourceIsNotStarved () throws Exception {

		MqRegistry.clear();
		MqRegistry.add(RecordingExecutor::new);
		ORDER.clear();

		// 名前の順で前（主）にたくさん、後ろに1件
		try (DB main = DBUtil.getDB(TwoDataSources.MAIN)) {
			for (int i = 0; i < MANY; i++) {
				new RecordingExecutor().put(main, new Data().putData("n", i));
			}
		}
		try (DB other = DBUtil.getDB(TwoDataSources.OTHER)) {
			new RecordingExecutor().put(other, new Data());
		}

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = new MqQueue(QUEUE).startNoWait(cancel);

		try {

			long deadline = System.currentTimeMillis() + 20_000;

			while (ORDER.size() < MANY + 1 && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}

		} finally {
			cancel.doCancel();
			assertTrue(manager.awaitTermination(10, TimeUnit.SECONDS), "止まりません");
		}

		assertEquals(MANY + 1, ORDER.size(), "全部は処理されませんでした");

		int position = ORDER.indexOf(TwoDataSources.OTHER);

		// 周ごとに見始める先をずらすので、遅くとも2周目には後ろのデータソースを見る
		assertTrue(position <= 2, "後ろのデータソースの行が %d 件目まで待たされました（前に %d 件）".formatted(position + 1, MANY));

	}

}
