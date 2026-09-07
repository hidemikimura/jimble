package io.jimble.util.thread;

/**
 * スレッドユーティリティ.
 */
public class ThreadUtil {

	/**
	 * スレッドを一定時間待機する.
	 *
	 * @param ms	ミリ秒
	 */
	public static void sleep (long ms) {

		try {

			Thread.sleep(ms);

		} catch (Exception ex) {}

	}

}
