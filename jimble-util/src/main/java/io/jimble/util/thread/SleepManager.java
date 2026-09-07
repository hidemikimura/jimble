package io.jimble.util.thread;

import java.util.ArrayList;
import java.util.List;

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
