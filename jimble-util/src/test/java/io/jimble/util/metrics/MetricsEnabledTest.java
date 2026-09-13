package io.jimble.util.metrics;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * メトリクスを切る（要件 NF-O-04 / D-167）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>切ったつもりで数え続けている</b>のがいちばん困る。
 * 名前の上限（{@link Metrics#MAX_NAMES}）に当たる心配も消えない。
 * </p>
 *
 * <p>
 * <b>切っても速くはならない。</b>数えること自体は 0 byte / 58ns で、
 * 高かったのは<b>名前をリクエストごとに組み立てていた</b>ぶんである
 * （そちらは作らないようにした）。<b>切れるようにしてあるのは費用のためではない。</b>
 * </p>
 */
class MetricsEnabledTest {

	@BeforeEach
	void clear () {

		Conf.reload();
		Metrics.reset();

	}

	@AfterEach
	void reset () {

		Conf.reload();
		Metrics.reset();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private static void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon));

	}

	@Test
	@DisplayName("D-167 既定は有効")
	void enabledByDefault () {

		conf("");

		assertTrue(MetricsConf.enabled());

		Metrics.count("a");

		assertEquals(1, Metrics.snapshot().getData("counter").getLong("a"));

	}

	@Test
	@DisplayName("D-167 切ると、数えるほうが全部止まる")
	void everythingStopsWhenDisabled () {

		conf("metrics { enabled = false }");

		assertFalse(MetricsConf.enabled());

		Metrics.count("a");
		Metrics.count("b", 5);
		Metrics.record("c", 1000);
		Metrics.gauge("d", () -> 7);

		Data snapshot = Metrics.snapshot();

		/*
		 * <b>空のままを返す。</b>例外にはしない——
		 * <b>切っているアプリで落ちても仕方がない</b>。
		 * 止まっていることは、空であることで分かる。
		 */
		assertTrue(snapshot.getData("counter").isEmpty(), snapshot.toString());
		assertTrue(snapshot.getData("latency").isEmpty(), snapshot.toString());
		assertTrue(snapshot.getData("gauge").isEmpty(), snapshot.toString());

	}

	@Test
	@DisplayName("D-167 途中で有効に戻せる")
	void itCanBeTurnedBackOn () {

		conf("metrics { enabled = false }");

		Metrics.count("a");

		conf("metrics { enabled = true }");

		Metrics.count("a");

		/*
		 * <b>切っていたあいだのぶんは戻ってこない。</b>
		 * 数えていないので当たり前だが、
		 * <b>切り替えた前後で数が飛ぶ</b>ことは知っておくこと。
		 */
		assertEquals(1, Metrics.snapshot().getData("counter").getLong("a"));

	}

	// region ここで固定していないこと

	/*
	 * - <b>切っているあいだに登録した gauge</b>は残らない。
	 *   有効に戻したら<b>登録し直す</b>必要がある——
	 *   gauge は起動時に1度だけ登録するものなので、
	 *   <b>途中で切り替える使い方は想定していない</b>
	 * - <b>切ったときの速さ</b>も見ていない（{@code Bench} の仕事）
	 */

	// endregion

}
