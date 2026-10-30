package io.jimble.conventions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * いくつかを同時に流す
 */
class ParallelTest {

	@Test
	@DisplayName("全部に当て、終わるまで待つ")
	void runsAll () {

		Set<Integer> seen = ConcurrentHashMap.newKeySet();

		Parallel.each(IntStream.range(0, 100).boxed().toList(), 8, seen::add);

		assertEquals(100, seen.size());

	}

	@Test
	@DisplayName("本当に同時に流れる（4つが同時に待ち合わせられる）")
	void runsConcurrently () {

		CountDownLatch together = new CountDownLatch(4);

		Parallel.each(List.of(1, 2, 3, 4), 4, item -> {
			together.countDown();
			try {
				assertTrue(together.await(5, TimeUnit.SECONDS), "同時に流れていない");
			} catch (InterruptedException ex) {
				throw new IllegalStateException(ex);
			}
		});

	}

	@Test
	@DisplayName("失敗したら最初の失敗を投げ、まだ始めていないものは始めない")
	void stopsOnFailure () {

		IllegalStateException boom = new IllegalStateException("boom");
		AtomicInteger started = new AtomicInteger();

		IllegalStateException thrown = assertThrows(IllegalStateException.class,
			() -> Parallel.each(IntStream.range(0, 1000).boxed().toList(), 1, item -> {
				started.incrementAndGet();
				if (item == 3) {
					throw boom;
				}
			}));

		assertSame(boom, thrown);
		assertEquals(4, started.get(), "失敗のあとも始めている");

	}

	@Test
	@DisplayName("空なら何もしない")
	void empty () {

		Parallel.each(List.<Integer>of(), 8, item -> {
			throw new AssertionError("呼ばれた");
		});

	}

}
