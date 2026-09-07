package io.jimble.db.cache;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.redis.RedisClient;
import io.jimble.db.redis.RedisNotConfiguredException;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * メモリキャッシュとキャッシュ実装の選択のテスト（M4 ステップ7）
 *
 * <p>DB / Redis を使わないので通常の {@code build} で走る。</p>
 */
class MemoryCacheTest {

	/* キャッシュ */
	private MemoryCache cache;

	@BeforeEach
	void setUp () {

		Conf.reload();
		cache = new MemoryCache();
		cache.removeGroup("group");

	}

	@AfterEach
	void tearDown () {

		Conf.reload();

	}

	@Test
	@DisplayName("D-85 ICache 越しに呼んでも引数の並びが同じ")
	void setThroughInterface () {

		/*
		 * MemoryCache の 4引数 set は (key, group, value, contentType) で、
		 * ICache の (key, value, contentType, group) と並びが違っていた。
		 * 4つとも String なので @Override は通り、
		 * インターフェース越しに呼んだときだけ中身が入れ替わっていた。
		 *
		 * 実際にスケジューラのハートビートがこの形で呼んでいる。
		 */
		ICache api = new MemoryCache();

		api.set("iface-key", "本文", "application/json", "iface-group");

		CacheData data = api.get("iface-key");

		assertEquals("本文", data.contentString(), "値が入れ替わっている");
		assertEquals("application/json", data.contentType(), "コンテンツタイプが入れ替わっている");
		assertEquals(List.of("本文"), api.getStringGroup("iface-group"), "グループに入っていない");

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	// region set / get

	@Test
	@DisplayName("set() は成功したら true を返す")
	void setReturnsTrue () {

		// 移送元は成功しても false を返していた
		assertTrue(cache.set("key-1", "value", "text/plain"));

	}

	@Test
	@DisplayName("入れた値が取れる")
	void getString () {

		cache.set("key-2", "値", "text/plain");

		assertEquals("値", cache.getString("key-2"));

	}

	@Test
	@DisplayName("has() がキャッシュの有無を判定する")
	void has () {

		cache.set("key-3", "値", "text/plain", "group");

		// 移送元は cacheGroupMap.containsKey(key) を見ており、まったく判定できていなかった
		assertTrue(cache.has("key-3", "group"));
		assertFalse(cache.has("key-nothing", "group"));
		assertFalse(cache.has("key-3", "other-group"), "違うグループで true になっている");

	}

	@Test
	@DisplayName("グループ単位で消せる（F-U-08）")
	void removeGroup () {

		// docs:begin cache-group
		cache.set("key-4", "値", "text/plain", "group");
		cache.set("key-5", "値", "text/plain", "group");
		cache.set("key-6", "値", "text/plain", "keep");

		cache.removeGroup("group");
		// docs:end

		assertFalse(cache.has("key-4", "group"));
		assertFalse(cache.has("key-5", "group"));
		assertTrue(cache.has("key-6", "keep"), "別グループまで消えている");

	}

	// endregion

	// region 期限

	@Test
	@DisplayName("期限を設定しても直後に消えない")
	void expireUsesSeconds () throws Exception {

		// 移送元はミリ秒から「秒」を引いていたので、60 を設定すると 60 ミリ秒で消えた
		conf(MemoryCache.KEY_EXPIRE + " = 60");

		cache.set("key-7", "値", "text/plain");

		Thread.sleep(120);

		// 掃除は set() のついでに走る
		cache.set("key-8", "値", "text/plain");

		assertEquals("値", cache.getString("key-7"), "期限内なのに消えている");

	}

	@Test
	@DisplayName("同時に書いても例外にならない")
	void concurrentSet () throws Exception {

		conf(MemoryCache.KEY_EXPIRE + " = 60");

		int threads = 16;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		List<Throwable> errors = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			int index = i;
			Thread.startVirtualThread(() -> {
				try {
					start.await();
					for (int j = 0; j < 200; j++) {
						// 移送元は HashMap ＋ tryLock 失敗でも unlock していたので、
						// IllegalMonitorStateException や壊れたマップになりえた
						cache.set("concurrent-%d-%d".formatted(index, j), "値", "text/plain");
						cache.getString("concurrent-%d-%d".formatted(index, j));
					}
				} catch (Throwable ex) {
					synchronized (errors) {
						errors.add(ex);
					}
				} finally {
					done.countDown();
				}
			});
		}

		start.countDown();
		assertTrue(done.await(30, TimeUnit.SECONDS), "終わらない");
		assertTrue(errors.isEmpty(), errors.toString());

	}

	// endregion

	// region 実装の選択（F-U-07）

	@Test
	@DisplayName("cache.type でメモリキャッシュを選べる")
	void selectMemory () {

		conf("cache.type = \"memory\"");

		assertInstanceOf(MemoryCache.class, Cache.instance(null));

	}

	@Test
	@DisplayName("既定は DB キャッシュ")
	void defaultIsDb () {

		assertEquals(Cache.TYPE_DB, Cache.type());

	}

	@Test
	@DisplayName("cache.temp_dir が無くてもクラスが使える")
	void tempDirWithoutSetting () {

		// 移送元は static 初期化で既定値なしの取得をしており、
		// 設定を書いていないとクラス初期化で落ちていた
		assertFalse(Cache.getTempDirPath().isEmpty());

	}

	// endregion

	// region Redis 未設定（F-U-10）

	@Test
	@DisplayName("Redis が未設定なら isConfigured() が false")
	void redisNotConfigured () {

		assertFalse(RedisClient.isConfigured());

	}

	@Test
	@DisplayName("Redis 未設定で使おうとしたら明確な例外")
	void redisThrowsWhenUnconfigured () {

		RedisNotConfiguredException ex =
			assertThrows(RedisNotConfiguredException.class, RedisClient::client);

		assertTrue(ex.getMessage().contains("redis.host"), ex.getMessage());

	}

	@Test
	@DisplayName("設定すれば isConfigured() が true")
	void redisConfigured () {

		conf("redis.host = \"127.0.0.1\"");

		assertTrue(RedisClient.isConfigured());

	}

	// endregion

}
