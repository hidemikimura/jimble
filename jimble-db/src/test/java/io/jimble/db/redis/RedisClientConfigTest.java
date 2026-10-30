package io.jimble.db.redis;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SingleServerConfig;
import org.redisson.config.SslVerificationMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 設定から Redisson の設定を組み立てる（単体とクラスター）
 *
 * <p>Redis は要らない（繋がない）。</p>
 */
class RedisClientConfigTest {

	@AfterEach
	void tearDown () {

		Conf.reload();

	}

	@Test
	@DisplayName("host だけなら単体で、これまでどおりの値になる")
	void singleServer () {

		use("""
			redis.host = "cache.local"
			redis.port = 6380
			redis.password = "pw"
			""");

		Config config = RedisClient.redissonConfig();

		assertTrue(config.isSingleConfig());
		SingleServerConfig server = config.useSingleServer();
		assertEquals("redis://cache.local:6380", server.getAddress());
		assertEquals("pw", server.getPassword());
		assertEquals(64, server.getConnectionPoolSize());

	}

	@Test
	@DisplayName("cluster.nodes を書けばクラスターになり、マスターだけから読む")
	void cluster () {

		use("""
			redis.cluster.nodes = ["10.0.0.1:6379", "10.0.0.2"]
			redis.username = "app"
			redis.password = "pw"
			redis.settings.connection_pool_size = 32
			redis.settings.connection_minimum_idle = 4
			redis.cluster.scan_interval = 2s
			""");

		assertTrue(RedisClient.isConfigured());
		assertEquals(RedisClient.Mode.CLUSTER, RedisClient.mode());

		Config config = RedisClient.redissonConfig();

		assertTrue(config.isClusterConfig());
		ClusterServersConfig cluster = config.useClusterServers();
		assertEquals(List.of("redis://10.0.0.1:6379", "redis://10.0.0.2:6379"), cluster.getNodeAddresses());
		assertEquals(ReadMode.MASTER, cluster.getReadMode());
		assertEquals("app", cluster.getUsername());
		assertEquals("pw", cluster.getPassword());
		assertEquals(32, cluster.getMasterConnectionPoolSize());
		assertEquals(4, cluster.getMasterConnectionMinimumIdleSize());
		assertEquals(2000, cluster.getScanInterval());

	}

	@Test
	@DisplayName("ssl = true なら rediss で繋ぎ、証明書の名前まで確かめる（既定）")
	void clusterWithTls () {

		use("""
			redis.cluster.nodes = ["clustercfg.example.cache.amazonaws.com:6379"]
			redis.ssl = true
			""");

		ClusterServersConfig cluster = RedisClient.redissonConfig().useClusterServers();

		assertEquals(List.of("rediss://clustercfg.example.cache.amazonaws.com:6379"), cluster.getNodeAddresses());
		assertEquals(SslVerificationMode.STRICT, cluster.getSslVerificationMode());

	}

	@Test
	@DisplayName("ssl_verification = ca_only なら、証明書の名前は確かめない")
	void caOnly () {

		use("""
			redis.cluster.nodes = ["10.0.0.1:6379"]
			redis.ssl = true
			redis.ssl_verification = "ca_only"
			""");

		assertEquals(SslVerificationMode.CA_ONLY, RedisClient.redissonConfig().useClusterServers().getSslVerificationMode());

	}

	@Test
	@DisplayName("ssl_verification に知らない値（none も）を書くと止める")
	void unknownVerification () {

		use("""
			redis.host = "cache.local"
			redis.ssl_verification = "none"
			""");

		IllegalStateException ex = assertThrows(IllegalStateException.class, RedisClient::redissonConfig);
		assertTrue(ex.getMessage().contains("redis.ssl_verification"), ex.getMessage());

	}

	@Test
	@DisplayName("host と cluster.nodes を両方書くと止める（どちらに繋ぐか分からない）")
	void bothIsError () {

		use("""
			redis.host = "cache.local"
			redis.cluster.nodes = ["10.0.0.1:6379"]
			""");

		IllegalStateException ex = assertThrows(IllegalStateException.class, RedisClient::redissonConfig);
		assertTrue(ex.getMessage().contains("redis.host") && ex.getMessage().contains("redis.cluster.nodes"), ex.getMessage());

	}

	@Test
	@DisplayName("ノードに redis:// を書いたり、空の値を書いたりすると止める（ssl は redis.ssl で決める）")
	void badNode () {

		use("""
			redis.cluster.nodes = ["redis://10.0.0.1:6379"]
			""");

		assertThrows(IllegalStateException.class, RedisClient::redissonConfig);

		use("""
			redis.cluster.nodes = ["10.0.0.1:port"]
			""");

		assertThrows(IllegalStateException.class, RedisClient::redissonConfig);

		use("""
			redis.cluster.nodes = [""]
			""");

		assertThrows(IllegalStateException.class, RedisClient::redissonConfig);

	}

	@Test
	@DisplayName("nodes は文字列（, 区切り）でも書ける（環境変数から渡すとき）")
	void nodesAsString () {

		use("""
			redis.cluster.nodes = "10.0.0.1:6379, 10.0.0.2:6380"
			""");

		assertEquals(RedisClient.Mode.CLUSTER, RedisClient.mode());
		assertEquals(List.of("redis://10.0.0.1:6379", "redis://10.0.0.2:6380"),
			RedisClient.redissonConfig().useClusterServers().getNodeAddresses());

	}

	@Test
	@DisplayName("ポートが 1〜65535 でなければ止める")
	void portOutOfRange () {

		use("""
			redis.cluster.nodes = ["10.0.0.1:70000"]
			""");

		assertThrows(IllegalStateException.class, RedisClient::redissonConfig);

		use("""
			redis.cluster.nodes = ["10.0.0.1:0"]
			""");

		assertThrows(IllegalStateException.class, RedisClient::redissonConfig);

	}

	@Test
	@DisplayName("validate は繋がずに設定の誤りを見つける（起動のときに呼ぶ）。設定が無ければ何もしない")
	void validate () {

		use("""
			redis.host = "cache.local"
			redis.cluster.nodes = ["10.0.0.1:6379"]
			""");

		assertThrows(IllegalStateException.class, RedisClient::validate);

		use("""
			redis.host = ""
			""");

		RedisClient.validate();

	}

	@Test
	@DisplayName("どちらも書かなければ、設定されていない")
	void notConfigured () {

		use("""
			redis.host = ""
			redis.cluster.nodes = []
			""");

		assertFalse(RedisClient.isConfigured());
		assertEquals(RedisClient.Mode.NONE, RedisClient.mode());

	}

	private static void use (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon));

	}

}
