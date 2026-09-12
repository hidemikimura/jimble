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

	/** 設定キー：SSL を使うか */
	public static final String KEY_SSL = "redis.ssl";

	/** 設定キー：繋ぐまでの上限 */
	public static final String KEY_CONNECTION_TIMEOUT = "redis.settings.connection_timeout";

	/** 設定キー：コマンドの応答を待つ上限 */
	public static final String KEY_TIMEOUT = "redis.settings.timeout";

	/** 設定キー：常に開けておく接続の数 */
	public static final String KEY_CONNECTION_MINIMUM_IDLE = "redis.settings.connection_minimum_idle";

	/** 設定キー：接続プールの大きさ */
	public static final String KEY_CONNECTION_POOL_SIZE = "redis.settings.connection_pool_size";

	/** 設定キー：やり直す回数 */
	public static final String KEY_RETRY_ATTEMPTS = "redis.settings.retry_attempts";

	/** 設定キー：やり直しまでの待ちの下限 */
	public static final String KEY_RETRY_MINIMUM_INTERVAL = "redis.settings.retry_minimum_interval";

	/** 設定キー：やり直しまでの待ちの上限 */
	public static final String KEY_RETRY_MAXIMUM_INTERVAL = "redis.settings.retry_maximum_interval";

	/** 設定キー：使っていない接続を閉じるまでの時間 */
	public static final String KEY_IDLE_CONNECTION_TIMEOUT = "redis.settings.idle_connection_timeout";

	/** 設定キー：購読用に常に開けておく接続の数 */
	public static final String KEY_SUBSCRIPTION_CONNECTION_MINIMUM_IDLE_SIZE
		= "redis.settings.subscription_connection_minimum_idle_size";

	/** 設定キー：購読用の接続プールの大きさ */
	public static final String KEY_SUBSCRIPTION_CONNECTION_POOL_SIZE
		= "redis.settings.subscription_connection_pool_size";

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
						Conf.conf().getBoolean(KEY_SSL, false) ? "rediss" : "redis"
						, Conf.conf().getString(KEY_HOST, "")
						, Conf.conf().getInt(KEY_PORT, DEFAULT_PORT)
					)
				)
				.setConnectTimeout(millis(KEY_CONNECTION_TIMEOUT, Duration.ofSeconds(10)))
				.setTimeout(millis(KEY_TIMEOUT, Duration.ofSeconds(3)))
				.setConnectionMinimumIdleSize(Conf.conf().getInt(KEY_CONNECTION_MINIMUM_IDLE, 24))
				.setConnectionPoolSize(Conf.conf().getInt(KEY_CONNECTION_POOL_SIZE, 64))
				.setRetryAttempts(Conf.conf().getInt(KEY_RETRY_ATTEMPTS, 3))
				.setRetryDelay(new EqualJitterDelay(
					Conf.conf().getDuration(KEY_RETRY_MINIMUM_INTERVAL, Duration.ofMillis(500))
					, Conf.conf().getDuration(KEY_RETRY_MAXIMUM_INTERVAL, Duration.ofSeconds(2))))
				.setIdleConnectionTimeout(millis(KEY_IDLE_CONNECTION_TIMEOUT, Duration.ofSeconds(10)))
				.setSubscriptionConnectionMinimumIdleSize(
					Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_MINIMUM_IDLE_SIZE, 1))
				.setSubscriptionConnectionPoolSize(
					Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_POOL_SIZE, 50))
			;

			redissonClient = Redisson.create(config);

		} finally {

			clientLock.unlock();

		}

	}


	/**
	 * 時間をミリ秒で読む（Redisson の設定が int のミリ秒しか受けないため）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	ミリ秒
	 */
	private static int millis (String key, Duration defaultValue) {

		return (int) Conf.conf().getDuration(key, defaultValue).toMillis();

	}

}
