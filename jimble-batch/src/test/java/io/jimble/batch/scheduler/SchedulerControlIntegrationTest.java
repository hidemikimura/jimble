package io.jimble.batch.scheduler;

import io.jimble.batch.BatchTables;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スケジューラの入り切りを読めないとき（要件 F-B-07）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * {@link SchedulerControl#isEnabled()} は、読めないと {@code false}（止める）を返していた。
 * 動いているスケジューラはこれで止める印を見ているので、<b>DB の一瞬のつまずきで自分から降り、
 * 二度と戻らなかった</b>。読めなければ「今のまま」にする。
 * </p>
 *
 * <p>
 * 読めない状態は <b>DB を止めて</b>作る（{@code DBUtil.getMainDB()} が例外になる）。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class SchedulerControlIntegrationTest {

	@BeforeEach
	void load () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), SchedulerControlIntegrationTest.class);
		BatchTables.install(DBUtil.getMainDB());

		SchedulerControl.forgetLastKnown();

	}

	@AfterEach
	void restore () {

		// 止めたままのテストがあるので、読み込み直してから戻す
		if (!DBUtil.isUseDB()) {
			DBUtil.load(Conf.conf().config(), SchedulerControlIntegrationTest.class);
		}

		SchedulerControl.enable();
		SchedulerControl.forgetLastKnown();

		DBUtil.stop();

	}

	@Test
	@DisplayName("F-B-07 動いている間に読めなくなっても、止めない（前に読めた値のまま）")
	void keepsRunningWhenUnreadable () {

		assertTrue(SchedulerControl.enable());
		assertTrue(SchedulerControl.isEnabled());

		DBUtil.stop();

		assertTrue(SchedulerControl.isEnabled(), "DB を読めなかっただけで、スケジューラを止めています");

	}

	@Test
	@DisplayName("F-B-07 止めてあるなら、読めなくなっても止めたまま")
	void staysStoppedWhenUnreadable () {

		assertTrue(SchedulerControl.disable());
		assertFalse(SchedulerControl.isEnabled());

		DBUtil.stop();

		assertFalse(SchedulerControl.isEnabled(), "止めてあるのに、読めなかったので動かしています");

	}

	@Test
	@DisplayName("F-B-07 一度も読めていなければ、動かさない（起動しない）")
	void neverReadMeansStopped () {

		DBUtil.stop();

		assertFalse(SchedulerControl.isEnabled());

	}

	@Test
	@DisplayName("F-B-07 読めるようになれば、DB の値に戻る")
	void followsTheDbAgain () {

		assertTrue(SchedulerControl.enable());
		assertTrue(SchedulerControl.isEnabled());

		DBUtil.stop();
		assertTrue(SchedulerControl.isEnabled());

		DBUtil.load(Conf.conf().config(), SchedulerControlIntegrationTest.class);

		// ほかの台が止めた
		SchedulerControl.disable();
		SchedulerControl.forgetLastKnown();

		assertFalse(SchedulerControl.isEnabled());

	}

}
