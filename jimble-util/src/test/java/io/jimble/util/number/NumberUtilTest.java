package io.jimble.util.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 範囲内の乱数（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>公開しているのに、どこからも呼ばれておらず、テストも無かった。</b>
 * 1.0 を出せば約束の対象になって消せなくなるので、
 * <b>何を約束したことになるのか</b>を書き出しておく。
 * </p>
 *
 * <p>
 * <b>ここで返すのは「予測できてよい」乱数である。</b>
 * 合言葉やトークンには使わないこと——それは {@code SecureRandom} の仕事で、
 * 枠組みの中では {@code TokenUtil} が持っている。
 * </p>
 */
class NumberUtilTest {

	@Test
	@DisplayName("min と max のあいだに入る（両端を含む）")
	void theResultIsInsideTheRange () {

		/*
		 * <b>上端を含むかどうかは、ここでしか分からない。</b>
		 * {@code Random.nextInt(bound)} は上端を含まないので、
		 * <b>{@code +1} を書き忘れると max が一度も出ない</b>——
		 * 何度回しても例外は出ないので、<b>気づけない</b>。
		 */
		boolean sawMin = false;
		boolean sawMax = false;

		for (int i = 0; i < 1000; i++) {

			int v = NumberUtil.random(1, 3);

			assertTrue(1 <= v && v <= 3, "範囲の外が出ました: " + v);

			sawMin |= v == 1;
			sawMax |= v == 3;

		}

		assertTrue(sawMin, "下端が一度も出ませんでした");
		assertTrue(sawMax, "上端が一度も出ませんでした（+1 が抜けています）");

	}

	@Test
	@DisplayName("min と max が同じなら、その値だけ")
	void aSinglePointRange () {

		assertEquals(7, NumberUtil.random(7, 7));
		assertEquals(7L, NumberUtil.random(7L, 7L));

	}

	@Test
	@DisplayName("負の範囲でも動く")
	void negativeRanges () {

		for (int i = 0; i < 200; i++) {

			int v = NumberUtil.random(-5, -1);

			assertTrue(-5 <= v && v <= -1, "範囲の外が出ました: " + v);

		}

	}

	@Test
	@DisplayName("long のほうも範囲に入る")
	void theLongVersion () {

		for (int i = 0; i < 200; i++) {

			long v = NumberUtil.random(1L, 3L);

			assertTrue(1L <= v && v <= 3L, "範囲の外が出ました: " + v);

		}

	}

	@Test
	@DisplayName("min > max は落ちる（黙って入れ替えない）")
	void reversedBoundsThrow () {

		/*
		 * <b>入れ替えて動かしてはいけない。</b>
		 * 逆に書いたのは<b>たいてい間違い</b>なので、
		 * 動いてしまうと<b>間違いに気づく機会が消える</b>。
		 */
		assertThrows(IllegalArgumentException.class, () -> NumberUtil.random(3, 1));
		assertThrows(IllegalArgumentException.class, () -> NumberUtil.random(3L, 1L));

	}

	// region ここで固定していないこと

	/*
	 * - <b>偏りの無さ</b>は見ていない。{@code java.util.Random} に任せている
	 * - <b>速さ</b>も見ていない。呼ぶたびに {@code new Random()} を作る作りなので、
	 *   <b>1回だけ使う道具</b>である。回し続けるところでは自分で {@code Random} を持つこと
	 * - <b>{@code max - min} が {@code long} の幅を超える場合</b>は見ていない
	 *   （{@code random(Long.MIN_VALUE, Long.MAX_VALUE)} は桁があふれる）
	 */

	// endregion

}
