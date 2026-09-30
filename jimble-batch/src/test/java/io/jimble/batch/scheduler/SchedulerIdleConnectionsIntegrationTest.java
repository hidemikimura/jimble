package io.jimble.batch.scheduler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.mq.SchedulerQueue;
import io.jimble.db.ConnectionWatch;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqRegistry;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 何もしていないスケジューラが DB を借りるスレッド（要件 F-B-07）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * スケジューラのプロセスは、何もしていないときでも<b>7本前後のスレッドがそれぞれ定期的に DB を借りていた</b>
 * （MQ のワーカーが1本ずつ・止める印の確認・定期処理・心拍）。ふだんは 1ms で返すので重ならないが、
 * DB の応答が数秒遅れると<b>全員が同時に1本ずつ握り</b>、プールが埋まって取得待ちが時間切れになった。
 * </p>
 *
 * <p>
 * いまは <b>定期処理のスレッド（{@code jimble-scheduler}）と、キューの取り出し役</b>の2本だけが DB を借りる。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class SchedulerIdleConnectionsIntegrationTest {

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		originalConf = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("""
			scheduler.exit_check = 100ms
			scheduler.reload_interval = 300ms
			mq.poll_max = 100ms
			mq.thread_count.short_minus_time = 5
			""").withFallback(originalConf));

		DBUtil.load(Conf.conf().config(), SchedulerIdleConnectionsIntegrationTest.class);
		BatchTables.install(DBUtil.getMainDB());
		SchedulerQueue.queue().install();

		// sync は呼ばない（空の登録で呼ぶと、ほかのテストのバッチのマスタまで無効にする）
		BatchRegistry.clear();
		MqRegistry.clear();

		SchedulerControl.enable();

	}

	@AfterAll
	static void stopDataSource () {

		BatchRegistry.clear();
		MqRegistry.clear();
		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@Test
	@DisplayName("F-B-07 何もしていないとき、DB を借りるのは定期処理とキューの取り出し役の2本だけ")
	void onlyTwoThreadsTouchTheDbWhenIdle () throws Exception {

		DbScheduler scheduler = new DbScheduler("idle-connections");

		try (ConnectionWatch watch = ConnectionWatch.install()) {

			Thread main = Thread.ofVirtual().name("scheduler-main").start(scheduler::start);

			try {

				long deadline = System.currentTimeMillis() + 10_000;
				while (DbScheduler.current() == null && System.currentTimeMillis() < deadline) {
					Thread.sleep(50);
				}
				assertNotNull(DbScheduler.current(), "スケジューラが立ち上がりません");

				// 立ち上がりの分を外して見る
				Thread.sleep(500);
				watch.startRecording();

				// exit_check = 100ms・reload = 300ms・poll_max = 100ms なので、この間に何回も見にいく
				Thread.sleep(1500);

				Set<String> threads = watch.stopRecording();

				Set<String> names = threads.stream()
					.map(t -> t.substring(0, t.lastIndexOf('@')))
					.collect(Collectors.toSet());

				assertEquals(Set.of("jimble-scheduler", "jimble-mq-poll-" + SchedulerConf.queueName()), names
					, "定期処理と取り出し役のほかにも DB を借りに来ています: " + threads);
				assertEquals(2, threads.size(), "同じ名前のスレッドが何本も DB を借りに来ています: " + threads);

			} finally {
				scheduler.stop();
				main.join(15_000);
			}

			assertTrue(!main.isAlive(), "スケジューラが止まりません");

		}

	}

}
