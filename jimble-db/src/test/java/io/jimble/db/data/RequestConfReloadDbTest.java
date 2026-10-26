package io.jimble.db.data;

import com.typesafe.config.ConfigFactory;
import io.jimble.db.redis.RedisClient;
import io.jimble.db.sqlcache.SqlCacheConf;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 要求ごとに読む DB まわりの設定は 1 回だけ解釈して持つが、差し替えたら読み直す（2.7.5）
 */
class RequestConfReloadDbTest {

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("SQL キャッシュ・Redis・全部 null のテーブルを落とすか、が差し替えのたびに変わる")
	void picksUpEveryReplace () {

		Conf.replace(ConfigFactory.parseString("""
			sql_cache { enabled = true, namespace = tenant-a }
			redis.host = "127.0.0.1"
			db.remove_all_null_table_data = true
			"""));

		assertTrue(SqlCacheConf.enabled());
		assertEquals("tenant-a", SqlCacheConf.namespace());
		assertTrue(RedisClient.isConfigured());
		assertTrue(ResultSetConverter.removesAllNullTableData());

		Conf.replace(ConfigFactory.parseString("sql_cache { enabled = true, namespace = tenant-b }"));

		// 名前空間が古いままだと、別のテナントのキャッシュが返る（D-222）
		assertEquals("tenant-b", SqlCacheConf.namespace());
		assertFalse(RedisClient.isConfigured());
		assertFalse(ResultSetConverter.removesAllNullTableData());

		Conf.replace(ConfigFactory.parseString(""));

		assertFalse(SqlCacheConf.enabled());
		assertEquals("", SqlCacheConf.namespace());

	}

}
