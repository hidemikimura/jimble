package io.jimble.db.sqlcache;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 結果キャッシュの設定（要件 F-D-28 / D-95）
 */
class SqlCacheConfTest {

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("既定は無効（書かないと動かない）")
	void disabledByDefault () {

		Conf.replace(ConfigFactory.empty());

		/*
		 * 有効だと、selectCached を1度も呼んでいないアプリでも
		 * すべての更新で「どの行に当たるか」を組み立てることになる。
		 */
		assertFalse(SqlCacheConf.enabled());

	}

	@Test
	@DisplayName("書けば有効になる")
	void enabledWhenSet () {

		Conf.replace(ConfigFactory.empty()
			.withValue(SqlCacheConf.KEY_ENABLED, ConfigValueFactory.fromAnyRef(true)));

		assertTrue(SqlCacheConf.enabled());

	}

	@Test
	@DisplayName("設定を読み直したら結果も変わる（覚えたまま古い答えを返さない）")
	void followsReplace () {

		Conf.replace(ConfigFactory.empty()
			.withValue(SqlCacheConf.KEY_ENABLED, ConfigValueFactory.fromAnyRef(true)));

		assertTrue(SqlCacheConf.enabled());

		Conf.replace(ConfigFactory.empty()
			.withValue(SqlCacheConf.KEY_ENABLED, ConfigValueFactory.fromAnyRef(false)));

		assertFalse(SqlCacheConf.enabled(), "設定を替えたのに前の答えを返している");

	}

	@Test
	@DisplayName("既定値")
	void defaults () {

		Conf.replace(ConfigFactory.empty());

		assertEquals(SqlCacheConf.STORE_MEMORY, SqlCacheConf.store());
		assertEquals(SqlCacheConf.DEFAULT_TTL_SECONDS, SqlCacheConf.ttl().toSeconds());
		assertEquals(SqlCacheConf.DEFAULT_MAX, SqlCacheConf.max());

	}

}
