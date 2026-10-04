package io.jimble.db.sqlcache;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis の SQL 結果キャッシュの置き場（D-273）
 *
 * <p><b>Redis が必要</b>（{@code JIMBLE_TEST_REDIS_HOST}）。</p>
 */
@Tag("db")
class RedisSqlCacheStoreIntegrationTest {

	@BeforeAll
	static void setUp () {

		Conf.reload();

		assertTrue(RedisClient.isConfigured(), "Redis が設定されていません");

	}

	@AfterAll
	static void tearDown () {

		RedisClient.close();

	}

	@Test
	@DisplayName("D-273 いくつものタグをまとめて消す。取り出しの上限（500）を超えるタグも取り切る。ほかのタグのものは残る")
	void invalidatesManyTags () throws Exception {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();

		// 行ごとのタグ 100 個（UPDATE ... WHERE id IN (100 個) の形）
		Set<String> rowTags = new LinkedHashSet<>();
		for (int i = 0; i < 100; i++) {
			String tag = run + "/customer#id#" + i;
			rowTags.add(tag);
			store.put(run + ":row" + i, Set.of(tag), "row" + i, Duration.ofMinutes(5));
		}

		// 1 つのタグに 1200 件（取り出しの上限を超える）
		String listTag = run + "/customer#*";
		for (int i = 0; i < 1200; i++) {
			store.put(run + ":list" + i, Set.of(listTag), "list" + i, Duration.ofMinutes(5));
		}

		// 別のタグ
		store.put(run + ":other", Set.of(run + "/shop#id#1"), "other", Duration.ofMinutes(5));

		Set<String> tags = new LinkedHashSet<>(rowTags);
		tags.add(listTag);
		store.invalidate(tags);

		for (int i = 0; i < 100; i++) {
			assertNull(store.get(run + ":row" + i), "行のキャッシュが残っている: " + i);
		}
		for (int i = 0; i < 1200; i++) {
			assertNull(store.get(run + ":list" + i), "一覧のキャッシュが残っている: " + i);
		}

		assertEquals("other", store.get(run + ":other"), "関係ないタグのものまで消えた");

		store.invalidate(Set.of(run + "/shop#id#1"));
		assertNull(store.get(run + ":other"));

	}

}
