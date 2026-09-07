package io.jimble.util.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * スレッド管理.
 */
public class ThreadManager {

	/* スレッドプール. */
	private ThreadPoolExecutor threadPoolExecutor = null;

	/* スレッドプール数 */
	private int threadPoolCount = 0;

	/**
	 * コンストラクタ
	 */
	public ThreadManager() {

		this.threadPoolCount = getRuntimeCpuCount() * 2;
		this.threadPoolExecutor = new ThreadPoolExecutor(threadPoolCount, threadPoolCount, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new PriorityThreadFactory());

	}

	/**
	 * コンストラクタ
	 *
	 * @param threadPoolCount	スレッドプール数
	 */
	public ThreadManager(int threadPoolCount) {

		this.threadPoolCount = threadPoolCount;
		this.threadPoolExecutor = new ThreadPoolExecutor(threadPoolCount, threadPoolCount, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new PriorityThreadFactory());

	}

	/**
	 * 処理を追加する
	 *
	 * @param runnable	処理
	 */
	public void execute (Runnable runnable) {

		threadPoolExecutor.execute(runnable);

	}

	/**
	 * スレッドの終了を待機する
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
	 * スレッドを終了する
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

	/**
	 * スレッドプール数を取得する
	 *
	 * @return  スレッドプール数
	 */
	public int getThreadPoolCount () {

		return threadPoolCount;

	}

	/**
	 * 利用可能CPU数を取得する
	 *
	 * @return 利用可能CPU数
	 */
	public static int getRuntimeCpuCount () {

		return Runtime.getRuntime().availableProcessors();

	}

}
