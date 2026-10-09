package io.jimble.util.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 起こされたら起きる待ち方（D-295。MQ の取り出し役が使う）
 */
class SleepManagerTest {

	@Test
	@DisplayName("D-295 許可が来ていれば、待たずに起きる（許可は1つ使う）")
	void wakesImmediatelyWhenPermitted () {

		SleepManager sleepManager = new SleepManager(10, 5_000);
		Semaphore wake = new Semaphore(1);

		long start = System.nanoTime();
		sleepManager.sleepMax(wake);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertTrue(elapsedMillis < 1_000, "起こされたのに待ちました: %d ms".formatted(elapsedMillis));
		assertEquals(0, wake.availablePermits(), "許可を使っていません");

	}

	@Test
	@DisplayName("D-295 待っている途中で許可が来たら、そこで起きる")
	void wakesWhenPermitArrives () throws Exception {

		SleepManager sleepManager = new SleepManager(10, 5_000);
		Semaphore wake = new Semaphore(0);

		Thread waker = Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			wake.release();
		});

		long start = System.nanoTime();
		sleepManager.sleepMax(wake);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		waker.join();

		assertTrue(elapsedMillis < 2_000, "起こされたのに最後まで待ちました: %d ms".formatted(elapsedMillis));

	}

	@Test
	@DisplayName("D-295 許可が来なければ、間隔のぶんだけ待つ")
	void waitsForIntervalWithoutPermit () {

		SleepManager sleepManager = new SleepManager(30, 1_000);
		Semaphore wake = new Semaphore(0);

		long start = System.nanoTime();
		sleepManager.sleep(wake);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertTrue(elapsedMillis >= 25, "間隔より早く起きました: %d ms".formatted(elapsedMillis));

	}

}
