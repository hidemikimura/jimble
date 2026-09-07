package io.jimble.util.thread;

import java.util.concurrent.ThreadFactory;

/**
 * 普通優先度スレッドファクトリー.
 */
public class PriorityThreadFactory implements ThreadFactory {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Thread newThread(Runnable r) {

		return new Thread(r) {

			/**
			 * {@inheritDoc}
			 */
			@Override
			public void run() {

				// スレッド優先度を設定する
				Thread.currentThread().setPriority(Thread.NORM_PRIORITY);

				// スーパークラスを実行する
				super.run();

			}

		};

	}

}
