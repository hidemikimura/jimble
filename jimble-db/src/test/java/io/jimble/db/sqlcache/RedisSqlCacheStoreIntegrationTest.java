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
import static org.junit.jupiter.api.Assertions.assertFalse;
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


	@Test
	@DisplayName("D-282 タグの集合にも期限が付く（値の期限より少し長い）")
	void tagSetsExpire () throws Exception {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();
		String tag = run + "/customer#*";

		store.put(run + ":a", Set.of(tag), "a", Duration.ofMinutes(5));

		long ttl = RedisClient.client().getSet(RedisSqlCacheStore.TAG_PREFIX + tag).remainTimeToLive();

		assertTrue(ttl > Duration.ofMinutes(5).toMillis() && ttl <= Duration.ofMinutes(6).toMillis(), "タグの集合の期限: " + ttl);

	}

	@Test
	@DisplayName("D-296 全部消すときは、入れたキーとタグの集合だけを消す（Redis のほかのキーは見ない・消さない）")
	void clearDeletesOnlyWhatWasPut () throws Exception {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();

		// 取り出しの上限（500）を超える数を、期限なしで入れる
		for (int i = 0; i < 1200; i++) {
			store.put(run + ":v" + i, Set.of(run + "/customer#id#" + (i % 50)), "v" + i, Duration.ZERO);
		}

		// SQL 結果キャッシュとは関係ないキー（セッションなど）
		String unrelated = "test:" + run;
		RedisClient.client().getBucket(unrelated, org.redisson.client.codec.StringCodec.INSTANCE).set("keep", Duration.ofMinutes(1));

		store.clear();

		for (int i = 0; i < 1200; i++) {
			assertNull(store.get(run + ":v" + i), "残っている: " + i);
		}
		for (int i = 0; i < 50; i++) {
			assertFalse(RedisClient.client().getSet(RedisSqlCacheStore.TAG_PREFIX + run + "/customer#id#" + i).isExists()
				, "タグの集合が残っている: " + i);
		}

		assertEquals("keep", RedisClient.client().getBucket(unrelated, org.redisson.client.codec.StringCodec.INSTANCE).get()
			, "関係ないキーまで消えた");

		RedisClient.client().getBucket(unrelated).delete();

	}

	@Test
	@DisplayName("D-296 タグで消したキーは、全部消す用の集合からも外れる（無期限でも育ち続けない）")
	void invalidateForgetsKeys () throws Exception {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();
		String tag = run + "/customer#id#1";

		store.put(run + ":a", Set.of(tag), "a", Duration.ZERO);

		assertTrue(RedisClient.client().getScoredSortedSet(RedisSqlCacheStore.ALL_KEYS, org.redisson.client.codec.StringCodec.INSTANCE).contains(run + ":a"));

		store.invalidate(Set.of(tag));

		assertFalse(RedisClient.client().getScoredSortedSet(RedisSqlCacheStore.ALL_KEYS, org.redisson.client.codec.StringCodec.INSTANCE).contains(run + ":a")
			, "消したキーが全部消す用の集合に残っている");

	}

	@Test
	@DisplayName("D-296 期限つきで入れたものは、期限が過ぎると全部消す用の集合からも外れる（期限で消えた値の名前が溜まらない）")
	void expiredKeysAreTrimmed () throws Exception {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();

		store.put(run + ":short", Set.of(run + "/customer#id#1"), "short", Duration.ofMillis(200));

		var all = RedisClient.client().<String>getScoredSortedSet(RedisSqlCacheStore.ALL_KEYS, org.redisson.client.codec.StringCodec.INSTANCE);

		Double score = all.getScore(run + ":short");
		assertTrue(score != null && score <= System.currentTimeMillis() + 200, "期限の時刻で覚えていない: " + score);

		Thread.sleep(400);

		// 次に入れたときに、期限の過ぎたものが外れる
		store.put(run + ":next", Set.of(run + "/customer#id#2"), "next", Duration.ofMinutes(5));

		assertFalse(all.contains(run + ":short"), "期限の過ぎた名前が残っている");
		assertTrue(all.contains(run + ":next"));

		store.invalidate(Set.of(run + "/customer#id#2"));

	}

	@Test
	@DisplayName("D-296 前の版が入れたもの（覚えておく集合に入っていない）も、最初に全部消すときに消える")
	void clearSweepsLegacyKeysOnce () throws Exception {

		String run = UUID.randomUUID().toString();

		// 2.5.4 までの形：値とタグの集合だけ
		RedisClient.client().getBucket(RedisSqlCacheStore.PREFIX + run + ":old", org.redisson.client.codec.StringCodec.INSTANCE).set("old");
		RedisClient.client().getSet(RedisSqlCacheStore.TAG_PREFIX + run + "/customer#id#1", org.redisson.client.codec.StringCodec.INSTANCE).add(run + ":old");

		new RedisSqlCacheStore().clear();

		assertNull(RedisClient.client().getBucket(RedisSqlCacheStore.PREFIX + run + ":old", org.redisson.client.codec.StringCodec.INSTANCE).get()
			, "前の版の値が残っている");
		assertFalse(RedisClient.client().getSet(RedisSqlCacheStore.TAG_PREFIX + run + "/customer#id#1").isExists()
			, "前の版のタグの集合が残っている");

	}

}
