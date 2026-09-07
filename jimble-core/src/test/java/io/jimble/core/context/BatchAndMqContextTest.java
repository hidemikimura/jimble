package io.jimble.core.context;

import org.junit.jupiter.api.DisplayName;
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
