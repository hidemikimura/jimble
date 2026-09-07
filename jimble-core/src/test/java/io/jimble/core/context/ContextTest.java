package io.jimble.core.context;

import io.jimble.core.executor.AbstractExecutor;
import io.jimble.core.executor.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context のテスト
 */
class ContextTest {

	// region current

	@Test
	@DisplayName("T-10 スコープ外の current() は例外。null を返さない")
	void currentOutsideScopeThrows () {

		assertFalse(Context.isBound());

		IllegalStateException ex = assertThrows(IllegalStateException.class, Context::current);
		assertTrue(ex.getMessage().contains("Context#run"), "直し方が分かるメッセージであること: " + ex.getMessage());

	}

	@Test
	@DisplayName("スコープ内の current() は同一インスタンスを返す")
	void currentInsideScope () {

		try (TestContext context = new TestContext()) {
			context.run(() -> {
				assertTrue(Context.isBound());
				assertSame(context, Context.current());
			});
		}

		assertFalse(Context.isBound(), "スコープを抜けたらバインドが外れること");

	}

	@Test
	@DisplayName("型を指定した current() は型付きで返す")
	void currentWithType () {

		try (TestContext context = new TestContext()) {
			context.run(() -> assertSame(context, Context.current(TestContext.class)));
		}

	}

	@Test
	@DisplayName("型が違う current() は例外")
	void currentWithWrongType () {

		try (TestContext context = new TestContext()) {
			context.run(() -> assertThrows(
				IllegalStateException.class,
				() -> Context.current(ScopedTestContext.class)));
		}

	}

	@Test
	@DisplayName("ネストした run() は内側のコンテキストが現在のものになる")
	void nestedRun () {

		try (TestContext outer = new TestContext(); TestContext inner = new TestContext()) {
			outer.run(() -> {
				assertSame(outer, Context.current());
				inner.run(() -> assertSame(inner, Context.current()));
				assertSame(outer, Context.current(), "内側を抜けたら外側に戻ること");
			});
		}

	}

	@Test
	@DisplayName("F-C-11 サブクラスは ScopedValue を追加できる")
	void subclassCanAddScopedValues () {

		try (ScopedTestContext context = new ScopedTestContext("value-1")) {
			context.run(() -> {
				assertSame(context, Context.current());
				assertTrue(ScopedTestContext.EXTRA.isBound());
				assertEquals("value-1", ScopedTestContext.EXTRA.get());
			});
		}

	}

	// endregion


	// region 基本情報・属性

	@Test
	@DisplayName("実行IDは一意で、経過時間が取れる")
	void executionIdAndElapsed () {

		try (TestContext a = new TestContext(); TestContext b = new TestContext()) {
			assertFalse(a.executionId().isEmpty());
			assertFalse(a.executionId().equals(b.executionId()));
			assertFalse(a.elapsed().isNegative());
		}

	}

	@Test
	@DisplayName("属性の設定と取得")
	void attributes () {

		try (TestContext context = new TestContext()) {
			assertNull(context.attribute("none"));

			context.attribute("key", "value");
			String value = context.attribute("key");
			assertEquals("value", value);
		}

	}

	// endregion


	// region Executorキュー

	@Test
	@DisplayName("T-7 Executor は積んだ順に取り出される")
	void executorOrder () {

		try (TestContext context = new TestContext()) {
			List<String> log = new ArrayList<>();

			context.addExecutor(recorder(log, "a"));
			context.addExecutor(recorder(log, "b"));
			context.addExecutor(recorder(log, "c"));

			assertEquals(3, context.pendingExecutorCount());

			drain(context);

			assertEquals(List.of("a", "b", "c"), log);
			assertEquals(0, context.pendingExecutorCount());
		}

	}

	@Test
	@DisplayName("T-7 Executor の中から次の Executor を積める")
	void executorCanAppendDuringExecution () {

		try (TestContext context = new TestContext()) {
			List<String> log = new ArrayList<>();

			context.addExecutor(ctx -> {
				log.add("first");
				ctx.addExecutor(recorder(log, "appended"));
			});

			drain(context);

			assertEquals(List.of("first", "appended"), log);
		}

	}

	@Test
	@DisplayName("T-7 キャンセルすると残りが破棄されキャンセル処理が走る")
	void executorCancel () throws Exception {

		try (TestContext context = new TestContext()) {
			List<String> log = new ArrayList<>();

			AbstractExecutor<TestContext> canceling = new AbstractExecutor<>() {
				@Override
				public void execute (TestContext ctx) {
					log.add("canceling");
					cancel();
				}

				@Override
				public void onCancel (TestContext ctx) {
					log.add("onCancel");
				}
			};

			context.addExecutor(canceling);
			context.addExecutor(recorder(log, "never"));

			Executor<TestContext> executor = context.pollExecutor();
			executor.execute(context);

			assertTrue(executor.isCanceled());
			context.clearExecutors();
			executor.onCancel(context);

			assertEquals(List.of("canceling", "onCancel"), log);
			assertEquals(0, context.pendingExecutorCount(), "残りが破棄されていること");
		}

	}

	// endregion


	// region ライフサイクル

	@Test
	@DisplayName("T-9 close は例外が出ても呼ばれ、1回だけ実行される")
	void closeIsCalledOnceEvenOnException () {

		TestContext captured = new TestContext();

		try (captured) {
			captured.run(() -> {
				throw new IllegalStateException("boom");
			});
			// ここには来ない
		} catch (IllegalStateException expected) {
			// try-with-resources が close してから例外が伝播する
		}

		assertTrue(captured.isClosed());
		assertEquals(1, captured.closeCount());

		captured.close();
		assertEquals(1, captured.closeCount(), "close は冪等であること");

	}

	@Test
	@DisplayName("クローズ後の操作は例外")
	void operationsAfterCloseThrow () {

		TestContext context = new TestContext();
		context.close();

		assertThrows(IllegalStateException.class, () -> context.attribute("k", "v"));
		assertThrows(IllegalStateException.class, () -> context.addExecutor(ctx -> { }));
		assertThrows(IllegalStateException.class, () -> context.run(() -> { }));

	}

	@Test
	@DisplayName("クローズすると残りの Executor は破棄される")
	void closeClearsExecutors () {

		TestContext context = new TestContext();
		context.addExecutor(ctx -> { });
		assertEquals(1, context.pendingExecutorCount());

		context.close();

		assertEquals(0, context.pendingExecutorCount());

	}

	// endregion


	// region ヘルパ

	/**
	 * 実行を記録するだけの Executor
	 */
	private static Executor<TestContext> recorder (List<String> log, String name) {

		return ctx -> log.add(name);

	}

	/**
	 * キューを空になるまで実行する（Dispatcher の Stage 相当の最小版）
	 */
	private static void drain (TestContext context) {

		AtomicReference<Exception> failure = new AtomicReference<>();

		Executor<TestContext> executor;
		while ((executor = context.pollExecutor()) != null) {
			try {
				executor.execute(context);
			} catch (Exception ex) {
				failure.set(ex);
				break;
			}
		}

		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}

	}

	// endregion

}
