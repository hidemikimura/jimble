package io.jimble.db.sqlcache;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB の置き場の、期限切れの掃除（D-282）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class DbSqlCacheStorePurgeIntegrationTest {

	@BeforeAll
	static void load () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbSqlCacheStorePurgeIntegrationTest.class);

	}

	@AfterAll
	static void stop () {

		DBUtil.stop();

	}

	@Test
	@DisplayName("D-282 期限切れの行とそのタグだけを消す。期限の無いもの・まだのものは残る")
	void purgesOnlyExpired () throws Exception {

		DbSqlCacheStore store = new DbSqlCacheStore();
		String run = UUID.randomUUID().toString().substring(0, 8);

		store.put(run + "old", Set.of(run + "/t#*"), "old", Duration.ofMillis(1));
		store.put(run + "live", Set.of(run + "/t#*"), "live", Duration.ofMinutes(5));
		store.put(run + "forever", Set.of(run + "/u#*"), "forever", Duration.ZERO);

		Thread.sleep(20);

		assertTrue(DbSqlCacheStore.purgeExpired(System.currentTimeMillis()) >= 1, "消していない");

		assertEquals(0, count("SELECT count(*) AS n FROM sql_cache WHERE cache_key = ?", run + "old"));
		assertEquals(0, count("SELECT count(*) AS n FROM sql_cache_tag WHERE cache_key = ?", run + "old"), "期限切れのタグが残っている");

		assertEquals("live", store.get(run + "live"));
		assertEquals("forever", store.get(run + "forever"));
		assertEquals(1, count("SELECT count(*) AS n FROM sql_cache_tag WHERE cache_key = ?", run + "live"), "残す行のタグまで消えた");

		// 残ったものは、これまでどおりタグで消える
		store.invalidate(Set.of(run + "/t#*"));
		assertNull(store.get(run + "live"));

		store.invalidate(Set.of(run + "/u#*"));

	}

	private static long count (String sql, Object... params) {

		try (DB db = DBUtil.getMainDB().withoutSqlCache()) {
			return db.select(sql, params).map(row -> row.getLong("n")).orElse(-1L);
		}

	}

}
