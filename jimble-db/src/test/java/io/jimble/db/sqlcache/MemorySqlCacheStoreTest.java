package io.jimble.db.sqlcache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * メモリの置き場（要件 F-D-28）
 */
class MemorySqlCacheStoreTest {

	@Test
	@DisplayName("入れて取り出せる")
	void putAndGet () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1"), "v1", Duration.ZERO);

		assertEquals("v1", store.get("k1"));
		assertNull(store.get("k2"));

	}

	@Test
	@DisplayName("タグで消える")
	void invalidateByTag () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1", "shop#id#1"), "v1", Duration.ZERO);
		store.put("k2", Set.of("customer#id#2"), "v2", Duration.ZERO);

		store.invalidate(Set.of("customer#id#1"));

		assertNull(store.get("k1"));
		assertEquals("v2", store.get("k2"), "関係ないタグまで消えている");

	}

	@Test
	@DisplayName("どれか1つのタグが当たれば消える")
	void anyTagInvalidates () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1", "shop#id#1"), "v1", Duration.ZERO);

		store.invalidate(Set.of("shop#id#1"));

		assertNull(store.get("k1"), "結合先の更新で消えていない");

	}

	@Test
	@DisplayName("消したらタグの覚えも残らない")
	void tagsAreCleanedUp () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1", "shop#id#1"), "v1", Duration.ZERO);
		store.invalidate(Set.of("customer#id#1"));

		/*
		 * タグ → キー の覚えを残したままだと、
		 * <b>使われないタグが際限なく溜まる。</b>
		 */
		assertEquals(0, store.tagCount(), "タグの覚えが残っている");
		assertEquals(0, store.size());

	}

	@Test
	@DisplayName("入れ直すと古いタグから外れる")
	void replaceRelinksTags () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1"), "v1", Duration.ZERO);
		store.put("k1", Set.of("customer#id#2"), "v2", Duration.ZERO);

		store.invalidate(Set.of("customer#id#1"));

		assertEquals("v2", store.get("k1"), "古いタグで消えている");

	}

	@Test
	@DisplayName("期限が来たら返さない")
	void expires () throws Exception {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("customer#id#1"), "v1", Duration.ofMillis(30));

		assertNotNull(store.get("k1"));

		Thread.sleep(60);

		assertNull(store.get("k1"));

	}

	@Test
	@DisplayName("タグが無いものは入れない（消す手立てが無い）")
	void refusesUntagged () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of(), "v1", Duration.ZERO);

		assertNull(store.get("k1"));

	}

	@Test
	@DisplayName("全部消せる")
	void clear () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.put("k1", Set.of("a"), "v1", Duration.ZERO);
		store.put("k2", Set.of("b"), "v2", Duration.ZERO);

		store.clear();

		assertEquals(0, store.size());
		assertEquals(0, store.tagCount());

	}

}
