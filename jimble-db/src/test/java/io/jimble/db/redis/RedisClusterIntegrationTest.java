package io.jimble.db.redis;

import com.typesafe.config.ConfigFactory;
import io.jimble.db.cache.ICache;
import io.jimble.db.redis.lock.RedisLock;
import io.jimble.db.redis.lock.RedisLockResult;
import io.jimble.db.redis.lock.RedisLockStatus;
import io.jimble.db.sqlcache.RedisSqlCacheStore;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis クラスターに繋いで、jimble が Redis でやることが動くか
 *
 * <p>
 * <b>Redis クラスターが要る。</b>キーはスロットでノードに振り分けられるので、
 * 「複数のキーを1回で扱う」ところ（SQL 結果キャッシュのまとめた書き込み・まとめた削除・パターンでの削除）が
 * ノードをまたいでも動くかを見る。
 * </p>
 *
 * <pre>
 * .github/scripts/redis-cluster.sh
 * export JIMBLE_TEST_REDIS_CLUSTER=127.0.0.1:17001,127.0.0.1:17002,127.0.0.1:17003
 * ./gradlew :jimble-db:dbTest --tests '*RedisClusterIntegrationTest'
 * </pre>
 *
 * <p>{@code JIMBLE_TEST_REDIS_CLUSTER} が無ければ飛ばす（CI の db ジョブは渡している）。</p>
 */
@Tag("db")
class RedisClusterIntegrationTest {

	/* クラスターのノード（, で区切る） */
	private static final String NODES = System.getenv("JIMBLE_TEST_REDIS_CLUSTER");

	/* ノードをまたがせるために使うキーの数（スロットは 16384 あり、マスター 3 つなら十分ばらける） */
	private static final int KEYS = 30;

	@BeforeAll
	static void setUp () {

		assumeTrue(NODES != null && !NODES.isBlank(), "JIMBLE_TEST_REDIS_CLUSTER が無いので飛ばします");

		String nodes = Set.of(NODES.split(",")).stream().map(n -> "\"" + n.trim() + "\"").collect(Collectors.joining(", "));

		RedisClient.close();
		Conf.replace(ConfigFactory.parseString("""
			redis.host = ""
			redis.cluster.nodes = [%s]
			redis.settings.connection_minimum_idle = 2
			""".formatted(nodes)));

		assertEquals(RedisClient.Mode.CLUSTER, RedisClient.mode());
		assertTrue(RedisClient.client().getConfig().isClusterConfig(), "クラスターとして繋いでいない");

	}

	@AfterAll
	static void tearDown () {

		RedisClient.close();
		Conf.reload();

	}

	@Test
	@DisplayName("使うキーが、複数のマスターのスロットに散っている（テストの前提）")
	void keysSpreadOverMasters () {

		Set<Integer> masters = new HashSet<>();

		for (int i = 0; i < KEYS; i++) {
			// スロット 0〜5460 / 5461〜10922 / 10923〜16383 がマスター 3 つの受け持ち（redis-cli --cluster create の割り当て）
			masters.add(RedisClient.client().getKeys().getSlot("jimble:sql_cache:spread" + i) / 5461);
		}

		assertEquals(3, masters.size(), "キーが1つのマスターに偏っていて、ノードをまたぐ確かめにならない");

	}

	@Test
	@DisplayName("キャッシュに入れて取れ、消せる")
	void cache () {

		ICache cache = new RedisCache();
		Set<String> keys = new LinkedHashSet<>();

		for (int i = 0; i < KEYS; i++) {
			String key = "test:cluster:" + UUID.randomUUID();
			keys.add(key);
			assertTrue(cache.set(key, "値" + i, "text/plain"));
		}

		int i = 0;
		for (String key : keys) {
			assertEquals("値" + i++, cache.getString(key), "書いた直後に読めない（レプリカから読んでいないか）");
			cache.remove(key);
			assertFalse(cache.has(key, null));
		}

	}

	@Test
	@DisplayName("ロックを取って解放できる")
	void lock () {

		try (RedisLockResult result = RedisLock.lock("lock:cluster:" + UUID.randomUUID())) {
			assertEquals(RedisLockStatus.Success, result.status());
		}

	}

	@Test
	@DisplayName("SQL 結果キャッシュ：ノードをまたぐ書き込み・タグでの消し込み・全部消すが動く")
	void sqlCache () {

		RedisSqlCacheStore store = new RedisSqlCacheStore();
		String run = UUID.randomUUID().toString();
		String tag = run + "/item";

		for (int i = 0; i < KEYS; i++) {
			store.put(run + ":row" + i, Set.of(tag, run + "/item#id#" + i), "row" + i, Duration.ofMinutes(5));
		}

		store.put(run + ":other", Set.of(run + "/shop"), "other", Duration.ofMinutes(5));

		for (int i = 0; i < KEYS; i++) {
			assertEquals("row" + i, store.get(run + ":row" + i));
		}

		// 1 つのタグから、ノードをまたいだキーをまとめて消す
		store.invalidate(Set.of(tag));

		for (int i = 0; i < KEYS; i++) {
			assertNull(store.get(run + ":row" + i), "消し込みで残った: " + i);
		}
		assertEquals("other", store.get(run + ":other"), "関係ないタグのものまで消えた");

		// 全部消す（覚えておいた集合から取り出して消す + 最初の1回はパターンでの削除）
		store.put(run + ":again", Set.of(tag), "again", Duration.ofMinutes(5));
		store.clear();

		assertNull(store.get(run + ":other"));
		assertNull(store.get(run + ":again"));

	}

}
