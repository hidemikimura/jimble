package io.jimble.core.context;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 実行IDの生成
 *
 * <p>
 * 先頭に時刻を置くことで、ログを実行ID順に並べると時系列になる。
 * </p>
 */
final class ExecutionIds {

	/* 連番 */
	private static final AtomicLong SEQUENCE = new AtomicLong();

	/**
	 * コンストラクタ
	 */
	private ExecutionIds () {

	}

	/**
	 * 実行IDを生成する
	 *
	 * @return	実行ID
	 */
	static String generate () {

		long time = System.currentTimeMillis();
		long random = ThreadLocalRandom.current().nextLong() & 0xFFFF_FFFFL;
		long sequence = SEQUENCE.incrementAndGet() & 0xFFFFL;

		return Long.toUnsignedString(time, 36)
			+ "-" + Long.toUnsignedString(random, 36)
			+ "-" + Long.toUnsignedString(sequence, 36);

	}

}
