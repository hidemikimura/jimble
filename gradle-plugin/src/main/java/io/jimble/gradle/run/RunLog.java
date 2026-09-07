package io.jimble.gradle.run;

/**
 * {@code jimbleRun} のコンソール出力（要件 F-X-05）
 *
 * <p>
 * アプリのログと、プラグイン自身の動きを見分けられるようにする。
 * 移送元は両方とも素の {@code System.out} に混ざっていた。
 * </p>
 */
final class RunLog {

	/** プラグインの印 */
	private static final String MARK = "[jimbleRun] ";

	/* 前に出した時刻 */
	private long previous = 0;

	/**
	 * プラグインの動き
	 *
	 * @param message	メッセージ
	 */
	void debug (String message) {

		long now = System.currentTimeMillis();
		String elapsed = previous > 0 ? " (%dms)".formatted(now - previous) : "";
		previous = now;

		System.out.println(MARK + message + elapsed);

	}

	/**
	 * 伝えたいこと（時刻を付けない）
	 *
	 * @param message	メッセージ
	 */
	void info (String message) {

		previous = System.currentTimeMillis();

		System.out.println(MARK + message);

	}

	/**
	 * うまくいかなかったこと
	 *
	 * @param message	メッセージ
	 */
	void error (String message) {

		previous = System.currentTimeMillis();

		System.err.println(MARK + message);

	}

	/**
	 * アプリが出したもの（そのまま流す）
	 *
	 * @param line	1行
	 */
	void app (String line) {

		System.out.println(line);

	}

}
