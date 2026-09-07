package io.jimble.db.redis;

import io.jimble.util.conf.Conf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.EqualJitterDelay;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis クライアント
 *
 * <p>
 * <b>Redis が設定されていなくてもアプリは起動できる</b>（要件 F-U-10）。
 * 設定が無いまま {@link #client()} を呼んだときだけ
 * {@link RedisNotConfiguredException} を投げる。
 * </p>
 */
public class RedisClient {

	/** 設定キー：ホスト */
	public static final String KEY_HOST = "redis.host";

	/** 設定キー：ポート */
	public static final String KEY_PORT = "redis.port";

	/** 既定のポート */
	public static final int DEFAULT_PORT = 6379;

	/* クライアント */
	private static RedissonClient redissonClient;

	/* クライアント作成ロック */
	private static final ReentrantLock clientLock = new ReentrantLock();

	/**
	 * RedisClient
	 *
	 * @return	RedisClient
	 */
	public static RedissonClient client () {

		if (!isConfigured()) {
			throw new RedisNotConfiguredException(
				"Redis が設定されていません（%s / %s）。Redis を使う機能は利用できません".formatted(KEY_HOST, KEY_PORT));
		}

		createClient();
		return redissonClient;

	}

	/**
	 * Redis が設定されているか（要件 F-U-10）
	 *
	 * @return	設定されていれば true
	 */
	public static boolean isConfigured () {

		return !Conf.conf().getString(KEY_HOST, "").isEmpty();

	}

	/**
	 * クローズ
	 */
	public static void close () {

		if (redissonClient != null && !redissonClient.isShutdown()) {
			redissonClient.shutdown();
		}

		redissonClient = null;

	}

	/**
	 * クライアント作成
	 */
	private static void createClient () {

		if (redissonClient != null) {
			return;
		}

		try {

			clientLock.lock();

			if (redissonClient != null) {
				return;
			}

			Config config = new Config();
			config.useSingleServer()
				.setAddress(
					"%s://%s:%s".formatted(
						Conf.conf().getBoolean("redis.ssl", false) ? "rediss" : "redis"
						, Conf.conf().getString(KEY_HOST, "")
						, Conf.conf().getInt(KEY_PORT, DEFAULT_PORT)
					)
				)
				.setConnectTimeout(Conf.conf().getInt("redis.settings.connection_timeout", 10000))
				.setTimeout(Conf.conf().getInt("redis.settings.timeout", 3000))
				.setConnectionMinimumIdleSize(Conf.conf().getInt("redis.settings.connection_minimum_idle", 24))
				.setConnectionPoolSize(Conf.conf().getInt("redis.settings.connection_pool_size", 64))
				.setRetryAttempts(Conf.conf().getInt("redis.settings.retry_attempts", 3))
				.setRetryDelay(new EqualJitterDelay(Duration.ofMillis(Conf.conf().getInt("redis.settings.retry_minimum_interval", 500)), Duration.ofMillis(Conf.conf().getInt("redis.settings.retry_maximum_interval", 2000))))
				.setIdleConnectionTimeout(Conf.conf().getInt("redis.settings.idle_connection_timeout", 10000))
				.setSubscriptionConnectionMinimumIdleSize(Conf.conf().getInt("redis.settings.subscription_connection_minimum_idle_size", 1))
				.setSubscriptionConnectionPoolSize(Conf.conf().getInt("redis.settings.subscription_connection_pool_size", 50))
			;

			redissonClient = Redisson.create(config);

		} finally {

			clientLock.unlock();

		}

	}

}
