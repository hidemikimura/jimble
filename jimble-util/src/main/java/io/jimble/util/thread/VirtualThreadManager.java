package io.jimble.util.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * スレッド管理.
 */
public class VirtualThreadManager {

	/* スレッドプール. */
	private final ExecutorService threadPoolExecutor;

	/* 流量制御 */
	private Semaphore semaphore = null;

	/**
	 * コンストラクタ.
	 */
	public VirtualThreadManager() {

		this.threadPoolExecutor = Executors.newVirtualThreadPerTaskExecutor();

	}

	/**
	 * コンストラクタ
	 *
	 * @param pool  スレッド数
	 */
	public VirtualThreadManager(int pool) {

		this.threadPoolExecutor = Executors.newVirtualThreadPerTaskExecutor();
		this.semaphore = new Semaphore(pool);

	}

	/**
	 * 処理を追加する.
	 *
	 * @param runnable	処理
	 */
	public void execute (final Runnable runnable) {

		threadPoolExecutor.execute(() -> {
			if (semaphore != null) {
				semaphore.acquireUninterruptibly();
			}
			try {
				runnable.run();
			} finally {
				if (semaphore != null) {
					semaphore.release();
				}
			}
		});

	}

	/**
	 * スレッドの終了を待機する.
	 */
	public void waitThread () {

		threadPoolExecutor.shutdown();

		while (!threadPoolExecutor.isTerminated()) {
			ThreadUtil.sleep(100);
		}

		try {
			if (!threadPoolExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
				threadPoolExecutor.shutdownNow();
				threadPoolExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
			}
		} catch (Exception ex) {
			threadPoolExecutor.shutdownNow();
		}

	}

	/**
	 * スレッドを終了する.
	 *
	 * @param time		待機時間
	 * @param timeUnit	時間単位
	 */
	public boolean awaitTermination (int time, TimeUnit timeUnit) {

		boolean result = true;

		threadPoolExecutor.shutdown();

		try {
			result = threadPoolExecutor.awaitTermination(time, timeUnit);
			if (!result) {
				threadPoolExecutor.shutdownNow();
				threadPoolExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
			}
		} catch (Exception ex) {
			threadPoolExecutor.shutdownNow();
		}

		return result;

	}

}
