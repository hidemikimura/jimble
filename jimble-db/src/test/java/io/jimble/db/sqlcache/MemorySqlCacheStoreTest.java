package io.jimble.db.sqlcache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jimble.util.data.Data;
import io.jimble.util.internal.JsonArrayList;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

	/**
	 * いろいろな型の入った1行
	 *
	 * @return	行
	 */
	private static Data row () {

		Data nested = new Data();
		nested.put("city", "Tokyo");

		Data row = new Data();
		row.put("id", 1L);
		row.put("name", "あ");
		row.put("price", new BigDecimal("1234.50"));
		row.put("flag", true);
		row.put("created_at", new Timestamp(1_700_000_000_000L));
		row.put("day", LocalDate.of(2026, 10, 9));
		row.put("blob", new byte[] {1, 2, 3});
		row.put("tags", new JsonArrayList(List.of("a", "b")));
		row.put("address", nested);
		row.put("none", null);

		return row;

	}

	@Test
	@DisplayName("D-296 行のまま入れて取り出せる。型は変わらない")
	void putRowsAndGetRows () throws Exception {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		store.putRows("k1", Set.of("customer#id#1"), List.of(row()), Duration.ZERO);

		List<Data> rows = store.getRows("k1");

		assertEquals(1, rows.size());

		Data got = rows.getFirst();
		Data expected = row();

		assertEquals(expected.keySet(), got.keySet());
		for (String key : List.of("id", "name", "price", "flag", "created_at", "day", "tags", "address", "none")) {
			assertEquals(expected.get(key), got.get(key), key);
			if (expected.get(key) != null) {
				assertEquals(expected.get(key).getClass(), got.get(key).getClass(), key);
			}
		}
		assertArrayEquals((byte[]) expected.get("blob"), (byte[]) got.get("blob"));

		// 書き出した形でも取り出せる（置き場の口はそのまま）
		assertNotNull(store.get("k1"));
		assertEquals(expected.get("price"), SqlCache.deserialize(store.get("k1")).getFirst().get("price"));

	}

	@Test
	@DisplayName("D-296 取り出した行を書き換えても、置き場の中身もほかに渡した行も変わらない")
	void returnedRowsAreCopies () throws Exception {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		List<Data> put = new ArrayList<>(List.of(row()));
		store.putRows("k1", Set.of("customer#id#1"), put, Duration.ZERO);

		// 入れたあとで、入れた側が書き換える
		put.getFirst().put("name", "書き換えた");
		((Data) put.getFirst().get("address")).put("city", "書き換えた");

		Data first = store.getRows("k1").getFirst();
		Data second = store.getRows("k1").getFirst();

		assertNotSame(first, second);

		// 受け取った側が、中のものまで書き換える
		first.put("id", 99L);
		((Data) first.get("address")).put("city", "Osaka");
		((JsonArrayList) first.get("tags")).add("c");
		((byte[]) first.get("blob"))[0] = 9;
		((Timestamp) first.get("created_at")).setTime(0L);

		Data third = store.getRows("k1").getFirst();

		for (Data row : List.of(second, third)) {
			assertEquals(1L, row.get("id"));
			assertEquals("あ", row.get("name"));
			assertEquals("Tokyo", ((Data) row.get("address")).get("city"));
			assertEquals(List.of("a", "b"), row.get("tags"));
			assertEquals(1, ((byte[]) row.get("blob"))[0]);
			assertEquals(1_700_000_000_000L, ((Timestamp) row.get("created_at")).getTime());
		}

	}

	@Test
	@DisplayName("D-296 複製の仕方を知らない型が入っていたら、書き出して持つ（取り出せる）")
	void unknownTypeFallsBackToSerialization () throws Exception {

		MemorySqlCacheStore store = new MemorySqlCacheStore();

		HashMap<String, Object> map = new HashMap<>();
		map.put("x", 1);

		Data row = new Data();
		row.put("map", map);

		store.putRows("k1", Set.of("customer#id#1"), List.of(row), Duration.ZERO);

		Data got = store.getRows("k1").getFirst();

		assertEquals(map, got.get("map"));
		assertNotSame(map, got.get("map"));

	}

}
