package io.jimble.conventions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * いくつかを同時に流す（置き場へ上げるときの往復の待ちを重ねる）
 */
final class Parallel {

	private Parallel () {
	}

	/**
	 * 全部に action を当て、全部終わるまで待つ
	 *
	 * <p>
	 * <b>1つ失敗したら、まだ始めていないものは始めない</b>（流している最中のものは終わるまで待つ）。
	 * 投げるのは最初の失敗。流し直せば続きから上げられるので、途中で止まっても困らない。
	 * </p>
	 *
	 * @param items		流すもの
	 * @param threads	同時に流す数
	 * @param action	1つ分の処理
	 * @param <T>		流すものの型
	 */
	static <T> void each (List<T> items, int threads, Consumer<T> action) {

		if (items.isEmpty()) {
			return;
		}

		ExecutorService executor = Executors.newFixedThreadPool(Math.min(threads, items.size()));
		AtomicBoolean failed = new AtomicBoolean();
		List<Future<?>> futures = new ArrayList<>();

		try {

			for (T item : items) {
				futures.add(executor.submit(() -> {
					if (failed.get()) {
						return;
					}
					try {
						action.accept(item);
					} catch (RuntimeException | Error ex) {
						failed.set(true);
						throw ex;
					}
				}));
			}

			RuntimeException first = null;

			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (ExecutionException ex) {
					if (first == null) {
						first = ex.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(ex.getCause());
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("止められました", ex);
				}
			}

			if (first != null) {
				throw first;
			}

		} finally {
			executor.shutdownNow();
		}

	}

}
