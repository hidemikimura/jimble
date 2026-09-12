package io.jimble.util.info;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * この JVM の様子（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code com.sun.management} を直に見ている。</b>
 * 標準の {@code java.lang.management} ではなく <b>HotSpot 側の拡張</b>なので、
 * <b>Java を上げたときや別の JVM に載せ替えたときに、
 * キャストのところで落ちうる</b>——落ちるなら CI で落ちてほしい。
 * </p>
 */
class SystemInfoUtilTest {

	@Test
	@DisplayName("この JVM の様子が取れる")
	void itReadsTheJvm () {

		SystemInfo info = SystemInfoUtil.get();

		assertNotNull(info);

		assertTrue(info.availableProcessors() > 0, "CPU が0個です: " + info.availableProcessors());

		/*
		 * <b>使用率は「まだ測れていない」ことがある。</b>
		 * 起動直後は {@code -1} が返るので、<b>0 以上とは書けない</b>。
		 * ここで見たいのは<b>数が返ってくること</b>だけである。
		 */
		assertTrue(info.processCpuLoad() <= 100.0, "100% を超えました: " + info.processCpuLoad());
		assertTrue(info.systemCpuLoad() <= 100.0, "100% を超えました: " + info.systemCpuLoad());

		assertTrue(info.heapMemoryUsage() > 0, "ヒープの使用量が0です");
		assertTrue(info.nonHeapMemoryUsage() > 0, "ヒープ外の使用量が0です");

	}

	// region ここで固定していないこと

	/*
	 * - <b>値そのもの</b>は当然固定していない。見ているのは
	 *   <b>取りに行って落ちないこと</b>と<b>桁が正気であること</b>だけである
	 * - <b>使用率が -1 のまま返ること</b>も直していない。
	 *   {@code com.sun.management} の仕様（まだ測れていない）で、
	 *   <b>0 に丸めると「無負荷」と見分けが付かなくなる</b>
	 */

	// endregion

}
