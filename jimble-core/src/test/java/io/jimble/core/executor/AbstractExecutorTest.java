package io.jimble.core.executor;

import io.jimble.core.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbstractExecutor のテスト
 */
class AbstractExecutorTest {

	/**
	 * テスト用コンテキスト
	 */
	static final class Ctx extends Context<Ctx> {

		@Override
		protected Ctx self () {

			return this;

		}

	}

	@Test
	@DisplayName("cancel() を呼ぶまで isCanceled() は false")
	void cancelFlag () throws Exception {

		AbstractExecutor<Ctx> executor = new AbstractExecutor<>() {
			@Override
			public void execute (Ctx context) {
				cancel();
			}
		};

		assertFalse(executor.isCanceled());

		try (Ctx context = new Ctx()) {
			executor.execute(context);
		}

		assertTrue(executor.isCanceled());

	}

	@Test
	@DisplayName("既定の isCanceled() は false、onCancel() は何もしない")
	void defaults () throws Exception {

		Executor<Ctx> executor = context -> { };

		assertFalse(executor.isCanceled());

		try (Ctx context = new Ctx()) {
			executor.onCancel(context);
		}

	}

}
