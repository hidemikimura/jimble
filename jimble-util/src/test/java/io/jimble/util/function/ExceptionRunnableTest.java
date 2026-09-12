package io.jimble.util.function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 例外を投げられる {@code Runnable}（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>これが無いと、渡す側が全部 try-catch で包むことになる。</b>
 * 包んだ結果<b>握り潰される</b>のがいちばんよくある事故なので、
 * <b>投げたものがそのまま外へ出ること</b>を書き留めておく。
 * </p>
 */
class ExceptionRunnableTest {

	/**
	 * 受け取って走らせる側の見本
	 *
	 * @param runnable	仕事
	 * @throws Exception	仕事が投げたもの
	 */
	private static void run (ExceptionRunnable runnable) throws Exception {

		runnable.run();

	}

	@Test
	@DisplayName("ラムダで書ける")
	void itIsALambda () throws Exception {

		AtomicInteger count = new AtomicInteger();

		run(count::incrementAndGet);

		assertEquals(1, count.get());

	}

	@Test
	@DisplayName("投げた例外はそのまま外へ出る（包まない・潰さない）")
	void exceptionsComeStraightOut () {

		/*
		 * <b>包むと、呼ぶ側が型で捕まえられなくなる。</b>
		 * <b>潰すと、失敗したのに成功したように見える</b>——
		 * どちらもここが最後の砦である。
		 */
		IOException thrown = assertThrows(IOException.class, () -> run(() -> {
			throw new IOException("読めません");
		}));

		assertEquals("読めません", thrown.getMessage());

	}

	// region ここで固定していないこと

	/*
	 * - <b>{@code Runnable} との相互変換</b>は用意していない。
	 *   検査例外を投げられる側から投げられない側へは<b>安全に変換できない</b>ので、
	 *   <b>変換する道を作らない</b>のがここの立場である
	 */

	// endregion

}
