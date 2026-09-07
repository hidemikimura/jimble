package io.jimble.db.redis;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.ICache;
import io.jimble.db.redis.lock.RedisLock;
import io.jimble.db.redis.lock.RedisLockResult;
import io.jimble.db.redis.lock.RedisLockStatus;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis キャッシュと分散ロックの確認（要件 F-U-07 / F-U-09）
 *
 * <p>
 * <b>Redis と開発用 DB が必要。</b>
 * </p>
 *
 * <pre>
 * redis-server --daemonize yes
 * ./gradlew :jimble-db:dbTest
 * </pre>
 */
@Tag("db")
class RedisIntegrationTest {

	@BeforeAll
	static void setUp () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), RedisIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		assertTrue(RedisClient.isConfigured(), "Redis が設定されていません");

	}

	@AfterAll
	static void tearDown () {

		RedisClient.close();
		DBUtil.stop();

	}

	// region Redis キャッシュ（F-U-07 / F-U-08）

	@Test
	@DisplayName("Redis キャッシュに入れて取れる")
	void redisCache () {

		ICache cache = new RedisCache();
		String key = "test:" + UUID.randomUUID();

		assertTrue(cache.set(key, "値", "text/plain"));
		assertEquals("値", cache.getString(key));

		cache.remove(key);
		assertFalse(cache.has(key, null));

	}

	@Test
	@DisplayName("cache.type = redis で Redis キャッシュが選ばれる")
	void selectRedisCache () {

		Conf.replace(com.typesafe.config.ConfigFactory
			.parseString("cache.type = \"redis\"")
			.withFallback(Conf.conf().config()));

		try {
			assertTrue(Cache.instance(DBUtil.getMainDB()) instanceof RedisCache);
		} finally {
			Conf.reload();
		}

	}

	// endregion

	// region 分散ロック（F-U-09）

	@Test
	@DisplayName("ロックを取って解放できる")
	void lockAndUnlock () {

		String key = "lock:" + UUID.randomUUID();

		RedisLockResult result = RedisLock.lock(key);

		assertEquals(RedisLockStatus.Success, result.status());

		closeQuietly(result);

	}

	@Test
	@DisplayName("tryLock は待ち時間を過ぎたら取れない")
	void tryLockTimeout () throws Exception {

		String key = "lock:" + UUID.randomUUID();

		RedisLockResult held = RedisLock.lock(key);
		assertEquals(RedisLockStatus.Success, held.status());

		try {

			// 別スレッドから取りに行く（同じスレッドだと再入で取れてしまう）
			RedisLockStatus[] status = new RedisLockStatus[1];

			Thread thread = Thread.ofVirtual().start(() ->
				status[0] = RedisLock.tryLock(key, 100, 1000).status());
			thread.join();

			assertEquals(RedisLockStatus.Failed, status[0]);

		} finally {
			closeQuietly(held);
		}

	}

	@Test
	@DisplayName("DB 楽観ロック付きのロックが動く")
	void lockWithDb () {

		// 移送元は SQL が「ON DUPLICATE KEYS UPDATE」（KEYS が誤り）で、
		// 必ず構文エラーになり Failed しか返さなかった
		DB db = DBUtil.getMainDB();
		String key = "lock:" + UUID.randomUUID();

		RedisLockResult result = RedisLock.lock(db, key);

		assertEquals(RedisLockStatus.Success, result.status(),
			"DB 付きロックが取れていない（SQL 構文エラーの疑い）");
		assertFalse(db.isError(), "SQL でエラーが出ている");

		closeQuietly(result);

	}

	// endregion

	/**
	 * ロックを解放する
	 *
	 * @param result	ロック結果
	 */
	private static void closeQuietly (RedisLockResult result) {

		try {
			result.close();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

}
