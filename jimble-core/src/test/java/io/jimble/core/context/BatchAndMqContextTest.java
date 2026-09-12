package io.jimble.core.context;

import org.junit.jupiter.api.DisplayName;
import io.jimble.core.lifecycle.CancelOrderNotify;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BatchContext / MqContext のテスト
 */
class BatchAndMqContextTest {

	@Test
	@DisplayName("F-C-12 バッチは HTTP の偽物なしで成立する")
	void batchContextNeedsNoHttp () {

		try (BatchContext context = new BatchContext("RSSフィード取得")) {
			context.run(() -> {
				assertSame(context, Context.current());
				assertSame(context, Context.current(BatchContext.class));
				assertEquals("RSSフィード取得", Context.current(BatchContext.class).batchName());
			});
		}

	}

	@Test
	@DisplayName("F-B-06 中断指示を確認できる")
	void cancelOrder () {

		try (BatchContext context = new BatchContext("長時間バッチ")) {
			assertFalse(context.isCancelOrdered());

			context.orderCancel();

			assertTrue(context.isCancelOrdered());
		}

	}

	@Test
	@DisplayName("D-155 指示元が止めろと言えば、コンテキストもそう答える")
	void cancelComesFromTheNotify () {

		/*
		 * <b>この道が繋がっていなかった。</b>
		 *
		 * 中断の判定は {@code AbstractBatch#isCancelOrder()}（履歴の cancel_status を見る）にあり、
		 * {@code BatchContext} はそれを知らないまま<b>自分の boolean だけ</b>を返していた。
		 * 上の {@code cancelOrder()} は<b>その boolean の往復しか見ていない</b>ので、
		 * 繋がっていなくても緑のままだった。
		 */
		StubNotify notify = new StubNotify();

		try (BatchContext context = new BatchContext("長時間バッチ")) {

			context.cancelNotify(notify);

			assertFalse(context.isCancelOrdered());

			// 管理画面から止めた、に当たる
			notify.canceled = true;

			assertTrue(context.isCancelOrdered(), "指示元を見ていません");

		}

	}

	@Test
	@DisplayName("D-155 コンテキストから止めたら、指示元にも伝わる")
	void orderCancelReachesTheNotify () {

		/*
		 * <b>伝えないと「止めたのに完了」になる。</b>
		 * バッチはループを抜けるが、履歴に残る結果を決めるのは指示元側の
		 * {@code cancelOrder} フィールドなので、そちらが false のままだと
		 * <b>completed として記録される</b>。
		 */
		StubNotify notify = new StubNotify();

		try (BatchContext context = new BatchContext("長時間バッチ")) {

			context.cancelNotify(notify);
			context.orderCancel();

			assertTrue(notify.doCancelCalled, "指示元に伝わっていません（止めたのに完了として残ります）");

		}

	}

	@Test
	@DisplayName("指示元を繋いでいなければ、これまでどおり自分の印だけを見る")
	void withoutNotify () {

		try (BatchContext context = new BatchContext("バッチ")) {
			assertFalse(context.isCancelOrdered());
			context.orderCancel();
			assertTrue(context.isCancelOrdered());
		}

	}

	/** 中断の指示元の代わり */
	private static final class StubNotify implements CancelOrderNotify {

		private volatile boolean canceled = false;

		private volatile boolean doCancelCalled = false;

		@Override public boolean isCancelOrder () { return canceled; }

		@Override public void doCancel () { doCancelCalled = true; canceled = true; }

	}

	@Test
	@DisplayName("F-C-12 MQ も HTTP の偽物なしで成立する")
	void mqContextNeedsNoHttp () {

		try (MqContext context = new MqContext("mq_get_rss", 123L, 2)) {
			context.run(() -> {
				MqContext current = Context.current(MqContext.class);
				assertEquals("mq_get_rss", current.queueName());
				assertEquals(123L, current.messageId());
				assertEquals(2, current.attempt());
			});
		}

	}

	@Test
	@DisplayName("3種のコンテキストは同じライフサイクル規約に従う")
	void sameLifecycle () {

		Context<?>[] contexts = {
			new BatchContext("batch")
			, new MqContext("queue", 1L, 1)
			, new TestContext()
		};

		for (Context<?> context : contexts) {
			assertFalse(context.isClosed());
			assertFalse(context.executionId().isEmpty());
			context.close();
			assertTrue(context.isClosed(), context.getClass().getSimpleName());
		}

	}

}
