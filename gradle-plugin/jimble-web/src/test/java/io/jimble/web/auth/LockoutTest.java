package io.jimble.web.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 待ち時間の計算（要件 F-W-29）
 *
 * <p>
 * DB を使う側は {@code LockoutIntegrationTest}（{@code @Tag("db")}）が見ている。
 * ここは<b>DB なしで決まること</b>だけを固定する。
 * </p>
 */
class LockoutTest {

	// region 倍にしていく

	@Test
	@DisplayName("F-W-29 打ち間違いのうちは待たせない")
	void freeAttemptsDoNotWait () {

		assertEquals(0, Lockout.requiredSeconds(0));
		assertEquals(0, Lockout.requiredSeconds(1));
		assertEquals(0, Lockout.requiredSeconds(2));
		assertEquals(0, Lockout.requiredSeconds(3), "既定の free_attempts は 3。3回目までは待たせない");

	}

	@Test
	@DisplayName("F-W-29 4回目から 1 → 2 → 4 と倍になる")
	void doublesAfterTheFreeAttempts () {

		assertEquals(1, Lockout.requiredSeconds(4));
		assertEquals(2, Lockout.requiredSeconds(5));
		assertEquals(4, Lockout.requiredSeconds(6));
		assertEquals(8, Lockout.requiredSeconds(7));

	}

	@Test
	@DisplayName("F-W-29 上限で止まる")
	void stopsAtTheMaximum () {

		// 既定の max_seconds は 300。2^8 = 256 までは伸びて、その次で頭を打つ
		assertEquals(256, Lockout.requiredSeconds(12));
		assertEquals(300, Lockout.requiredSeconds(13));
		assertEquals(300, Lockout.requiredSeconds(20));

	}

	// endregion

	// region 数えるほど甘くならないこと

	@Test
	@DisplayName("F-W-29 失敗を重ねても、待ち時間が短くならない")
	void neverGetsEasierAsFailuresPileUp () {

		/*
		 * <b>ここが本題である。</b>待ち時間は 2 の累乗で作っているので、
		 * 上限で先に打ち切らないと <b>桁が溢れる</b>。
		 *
		 *   67 回目 … 1L << 63 が<b>負</b>になり、Math.min(負, 300) で<b>負の待ち時間</b>
		 *   68 回目 … Java のシフトは 64 で一周するので 1L << 64 == 1。<b>1秒</b>
		 *
		 * どちらも「待たなくてよい」になる——<b>総当たりを続けた人だけが通れる</b>。
		 */
		long previous = 0;

		for (long failed = 0; failed <= 200; failed++) {

			long seconds = Lockout.requiredSeconds(failed);

			assertTrue(seconds >= 0, "%d 回目の待ち時間が負になっている: %d".formatted(failed, seconds));
			assertTrue(seconds >= previous
				, "%d 回目で待ち時間が短くなっている: %d → %d".formatted(failed, previous, seconds));

			previous = seconds;

		}

		assertEquals(300, Lockout.requiredSeconds(67), "桁が溢れて負になっている");
		assertEquals(300, Lockout.requiredSeconds(68), "シフトが一周して 1 秒に戻っている");
		assertEquals(300, Lockout.requiredSeconds(Long.MAX_VALUE));

	}

	// endregion

	// region DB が無いとき

	@Test
	@DisplayName("F-W-29 DB が無ければ、何もしないで通す")
	void doesNothingWithoutDb () {

		/*
		 * <b>ここで例外にすると、DB を使わないアプリがログインを組めなくなる。</b>
		 * 数えないだけで、ログインそのものは通す（ログには1度だけ出す）。
		 */
		assertEquals(0, Lockout.waitSeconds("alice"));
		assertDoesNotThrow(() -> Lockout.fail("alice"));
		assertDoesNotThrow(() -> Lockout.clear("alice"));
		assertEquals(0, Lockout.cleanup());
		assertEquals(0, Lockout.waitSeconds("alice"), "DB が無いのに数えている");

	}

	// endregion

}
