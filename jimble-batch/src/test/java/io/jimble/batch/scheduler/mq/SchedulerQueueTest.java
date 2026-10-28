package io.jimble.batch.scheduler.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スケジューラのキューは主のデータソースだけに置く（2.7.8）
 *
 * <p>
 * スケジューラの管理（{@code batch_master} など）も、管理画面から積むのも主なので、
 * ほかのデータソースに表を作って毎周見に行っても、何も来ない。
 * </p>
 */
class SchedulerQueueTest {

	@Test
	@DisplayName("スケジューラのキューは主だけに置き、主だけから取り出す")
	void queueIsMainOnly () {

		assertTrue(SchedulerQueue.queue().isMainOnly());

	}

	@Test
	@DisplayName("スケジューラのキューに積む処理は、主の DB にしか積まない")
	void executorsPutOnMainOnly () {

		assertTrue(new ExecuteBatchExecutor().mainOnly());
		assertTrue(new ReExecuteBatchExecutor().mainOnly());

	}

}
