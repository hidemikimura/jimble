package io.jimble.util.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * sleep
 */
public class SleepManager {

	/* 最小スリープ（ms） */
	private final long minSleep;

	/* 最大スリープ（ms） */
	private final long maxSleep;

	/* スリープ一覧 */
	private final List<Long> sleepList = new ArrayList<>();

	/* スリープインデックス */
	private int index = 0;

	/**
	 * コンストラクタ
	 *
	 * @param minSleep  最小スリープ（ms）
	 * @param maxSleep  最大スリープ（ms）
	 */
	public SleepManager(long minSleep, long maxSleep) {
		if (minSleep <= 0) {
			this.minSleep = 10;
		} else {
			this.minSleep = minSleep;
		}
		if (maxSleep <= this.minSleep) {
			this.maxSleep = 1000;
		} else {
			this.maxSleep = maxSleep;
		}
		createSleepList();
	}

	/**
	 * スリープ一覧を作成する
	 */
	private void createSleepList() {

		long v1 = 0;
		long v2 = minSleep;
		while (true) {
			long n = v1 + v2;
			if (n >= maxSleep) {
				sleepList.add(maxSleep);
				break;
			} else {
				sleepList.add(n);
			}
			v1 = v2;
			v2 = n;
		}

	}

	/**
	 * リセット
	 */
	public void reset () {
		this.index = 0;
	}

	/**
	 * スリープ
	 */
	public void sleep () {

		try {
			Thread.sleep(sleepList.get(index));
		} catch (Exception ignore) {}

		index++;
		if (index >= sleepList.size()) {
			index = sleepList.size() - 1;
		}

	}

	/**
	 * スリープ（{@code wake} に許可が来たら、そこで起きる）
	 *
	 * <p>間隔の伸ばし方は {@link #sleep()} と同じ。起こされても間隔は伸びる（戻すのは {@link #reset()}）。</p>
	 *
	 * @param wake	起こすもの（許可を1つ引いて起きる）
	 */
	public void sleep (Semaphore wake) {

		await(wake, sleepList.get(index));

		index++;
		if (index >= sleepList.size()) {
			index = sleepList.size() - 1;
		}

	}

	/**
	 * 最大スリープ（{@code wake} に許可が来たら、そこで起きる）
	 *
	 * @param wake	起こすもの（許可を1つ引いて起きる）
	 */
	public void sleepMax (Semaphore wake) {

		await(wake, maxSleep);

	}

	/**
	 * 許可が来るか、時間が来るまで待つ
	 *
	 * @param wake		起こすもの
	 * @param millis	待つ上限（ms）
	 */
	private static void await (Semaphore wake, long millis) {

		try {
			wake.tryAcquire(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	/**
	 * 最小スリープ
	 */
	public void sleepMin () {

		try {
			Thread.sleep(minSleep);
		} catch (Exception ignore) {}

	}

	/**
	 * 最大スリープ
	 */
	public void sleepMax () {

		try {
			Thread.sleep(maxSleep);
		} catch (Exception ignore) {}

	}

}
