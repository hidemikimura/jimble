package io.jimble.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScopeCache のテスト
 */
class ScopeCacheTest {

	@Test
	@DisplayName("スコープ外の current() は例外")
	void outsideScopeThrows () {

		assertThrows(IllegalStateException.class, ScopeCache::current);

	}

	@Test
	@DisplayName("同じキーの読み込みは1回だけ")
	void loadsOnce () {

		AtomicInteger loadCount = new AtomicInteger();

		try (TestContext context = new TestContext()) {
			context.run(() -> {
				for (int index = 0; index < 5; index++) {
					String value = ScopeCache.current().get("key", () -> {
						loadCount.incrementAndGet();
						return "value";
					});
					assertEquals("value", value);
				}
			});
		}

		assertEquals(1, loadCount.get());

	}

	@Test
	@DisplayName("null もキャッシュする（毎回取りに行かない）")
	void cachesNull () {

		AtomicInteger loadCount = new AtomicInteger();

		try (TestContext context = new TestContext()) {
			context.run(() -> {
				for (int index = 0; index < 3; index++) {
					String value = ScopeCache.current().get("missing", () -> {
						loadCount.incrementAndGet();
						return null;
					});
					assertNull(value);
				}
				assertTrue(ScopeCache.current().contains("missing"));
			});
		}

		assertEquals(1, loadCount.get(), "null でも1回しか読み込まないこと");

	}

	@Test
	@DisplayName("実行単位ごとに分離される")
	void isolatedPerExecution () {

		try (TestContext first = new TestContext()) {
			first.run(() -> ScopeCache.current().get("key", () -> "first"));
		}

		try (TestContext second = new TestContext()) {
			second.run(() -> assertEquals("second", ScopeCache.current().get("key", () -> "second")));
		}

	}

	@Test
	@DisplayName("remove すると再読み込みされる")
	void removeForcesReload () {

		AtomicInteger loadCount = new AtomicInteger();

		try (TestContext context = new TestContext()) {
			context.run(() -> {
				ScopeCache.current().get("key", loadCount::incrementAndGet);
				ScopeCache.current().remove("key");
				ScopeCache.current().get("key", loadCount::incrementAndGet);
			});
		}

		assertEquals(2, loadCount.get());

	}

	@Test
	@DisplayName("クローズすると破棄される")
	void clearedOnClose () {

		TestContext context = new TestContext();
		context.run(() -> ScopeCache.current().get("key", () -> "value"));

		assertEquals(1, context.scopeCache().size());

		context.close();

		assertEquals(0, context.scopeCache().size());
		assertFalse(context.scopeCache().contains("key"));

	}

	@Test
	@DisplayName("run() の外からは scopeCache() で取れる")
	void accessibleFromContext () {

		try (TestContext context = new TestContext()) {
			assertEquals("value", context.scopeCache().get("key", () -> "value"));
			context.run(() -> assertTrue(ScopeCache.current().contains("key"), "同じインスタンスであること"));
		}

	}

}
