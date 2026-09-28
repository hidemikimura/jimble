package io.jimble.core.executor;

import io.jimble.core.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
	@DisplayName("D-196 cancel() はそこで抜ける（1.x は印を立てるだけで、あとの行も走った）。isCanceled() は true になる")
	void cancelFlag () throws Exception {

		List<String> ran = new ArrayList<>();
		AbstractExecutor<Ctx> executor = new AbstractExecutor<>() {
			@Override
			public void execute (Ctx context) {
				cancel();
				ran.add("after cancel");
			}
		};

		assertFalse(executor.isCanceled());

		try (Ctx context = new Ctx()) {
			ExecutorCanceled e = assertThrows(ExecutorCanceled.class, () -> executor.execute(context));
			assertSame(executor, e.executor());
		}

		assertTrue(executor.isCanceled());
		assertEquals(List.of(), ran, "cancel() のあとの行が走っている");

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
