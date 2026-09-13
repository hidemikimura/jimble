package io.jimble.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 実行 ID の形（要件 NF-O-01 / D-171）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>実行 ID はログを辿る鍵である。</b>
 * 形が変わると、<b>それで集計しているものが黙って壊れる</b>——
 * ログは出続けるし例外も出ないので、<b>気づくのは後から調べようとしたとき</b>になる。
 * </p>
 *
 * <p>
 * 中の作りを 36 進の桁を直に書く形に変えた（要件 D-171）ので、
 * <b>出来上がりが1文字も変わっていないこと</b>をここで押さえる。
 * </p>
 */
class ExecutionIdsTest {

	@Test
	@DisplayName("D-171 36 進の書き方が Long.toUnsignedString と一致する")
	void theBaseThirtySixMatchesTheJdk () {

		/*
		 * <b>作り直したのは、この桁の並べ方だけである。</b>
		 * JDK のものと突き合わせておけば、形が変わっていないと言える。
		 */
		for (long value : new long[] {
			0L, 1L, 35L, 36L, 37L, 1295L, 1296L
			, 0xFFFFL, 0xFFFF_FFFFL
			, System.currentTimeMillis(), Long.MAX_VALUE }) {

			assertEquals(Long.toUnsignedString(value, 36), base36(value), "値: " + value);

		}

		// 適当な値でも合う
		for (int i = 0; i < 10000; i++) {

			long value = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);

			assertEquals(Long.toUnsignedString(value, 36), base36(value), "値: " + value);

		}

	}

	@Test
	@DisplayName("D-171 形は「36進-36進-36進」のまま")
	void theShapeIsUnchanged () {

		String id = ExecutionIds.generate();

		String[] parts = id.split("-");

		assertEquals(3, parts.length, id);

		for (String part : parts) {
			assertTrue(part.matches("[0-9a-z]+"), "36 進の小文字ではありません: " + id);
		}

	}

	@Test
	@DisplayName("続けて作っても重ならない")
	void idsDoNotRepeat () {

		Set<String> seen = new HashSet<>();

		for (int i = 0; i < 10000; i++) {
			assertTrue(seen.add(ExecutionIds.generate()), "同じ ID が2回出ました");
		}

	}

	@Test
	@DisplayName("時刻が先頭にあるので、並べると時系列になる")
	void theTimeComesFirst () throws Exception {

		String first = ExecutionIds.generate();

		Thread.sleep(2);

		String later = ExecutionIds.generate();

		String firstTime = first.substring(0, first.indexOf('-'));
		String laterTime = later.substring(0, later.indexOf('-'));

		/*
		 * <b>36 進は桁数が増えれば必ず大きい。</b>
		 * 同じ桁数なら辞書順で比べられる（数字が英字より前にあるため）。
		 */
		assertEquals(firstTime.length(), laterTime.length(), "桁数が変わりました: " + first + " / " + later);

		assertTrue(firstTime.compareTo(laterTime) <= 0, first + " / " + later);

		assertNotEquals(first, later);

	}

	// region ここで固定していないこと

	/*
	 * - <b>長さ</b>は固定していない。時刻の桁が増えれば伸びる
	 *   （36 進で 9 文字になるのは 2043 年ごろまで）
	 * - <b>乱数の質</b>も見ていない。ここは重なりにくくするためのもので、
	 *   <b>当てられて困る値ではない</b>——推測されて困るものは
	 *   {@code TokenUtil}（{@code SecureRandom}）を使うこと
	 * - <b>割り当て量</b>は {@code RequestBench} が見ている
	 */

	// endregion

	/**
	 * 36 進にする
	 *
	 * <p>
	 * <b>本体を呼ぶ。</b>同じ書き方をここに写すと、
	 * <b>両方が同じように間違っていても通ってしまう</b>。
	 * </p>
	 *
	 * @param value	値
	 * @return	36 進
	 */
	private static String base36 (long value) {

		StringBuilder sb = new StringBuilder();

		ExecutionIds.append(sb, value);

		return sb.toString();

	}

}
