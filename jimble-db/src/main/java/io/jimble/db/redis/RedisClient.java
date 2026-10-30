package io.jimble.db.redis;

import com.typesafe.config.ConfigValueType;
import io.jimble.util.conf.Conf;
import io.jimble.util.conf.ConfValue;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.BaseConfig;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.EqualJitterDelay;
import org.redisson.config.ReadMode;
import org.redisson.config.SingleServerConfig;
import org.redisson.config.SslVerificationMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Redis クライアント
 *
 * <p>
 * <b>Redis が設定されていなくてもアプリは起動できる</b>（要件 F-U-10）。
 * 設定が無いまま {@link #client()} を呼んだときだけ
 * {@link RedisNotConfiguredException} を投げる。
 * </p>
 *
 * <p>
 * <b>単体（{@code redis.host}）とクラスター（{@code redis.cluster.nodes}）のどちらかに繋ぐ</b>（2.7.12）。
 * 両方書くと、どちらに繋ぐか分からないので止める。認証・TLS・{@code redis.settings.*} は両方で同じものを使う。
 * クラスターでは<b>マスターだけから読む</b>（Redisson の既定はレプリカから読む。
 * 書いた直後に読むセッション・ロック・SQL 結果キャッシュの消し込みが、古い値を見ないように）。
 * </p>
 */
public class RedisClient {

	/**
	 * どう繋ぐか
	 */
	public enum Mode {

		/** 設定されていない */
		NONE,

		/** 単体（{@code redis.host}） */
		SINGLE,

		/** クラスター（{@code redis.cluster.nodes}） */
		CLUSTER

	}

	/** 設定キー：ホスト */
	public static final String KEY_HOST = "redis.host";

	/** 設定キー：クラスターのノード（{@code "host:port"} の並び。ElastiCache / OCI Cache なら入口を1つ書けばよい） */
	public static final String KEY_CLUSTER_NODES = "redis.cluster.nodes";

	/** 設定キー：クラスターの構成（ノードの増減・フェイルオーバー）を見直す間隔 */
	public static final String KEY_CLUSTER_SCAN_INTERVAL = "redis.cluster.scan_interval";

	/** 設定キー：TLS の証明書をどこまで確かめるか（{@code strict} | {@code ca_only}） */
	public static final String KEY_SSL_VERIFICATION = "redis.ssl_verification";

	/* どう繋ぐか（要求のたびに見るので、読み込んだ設定 1 つにつき 1 回だけ読む。2.7.5） */
	private static final ConfValue<Mode> MODE = ConfValue.of(c -> {
		if (!clusterNodes(c).isEmpty()) {
			return Mode.CLUSTER;
		}
		return c.getString(KEY_HOST, "").isEmpty() ? Mode.NONE : Mode.SINGLE;
	});

	/* ポートの上限 */
	private static final int MAX_PORT = 65535;

	/* ノードの書き方（host:port。port は省ける） */
	private static final Pattern NODE = Pattern.compile("[A-Za-z0-9._\\-]+(:[0-9]{1,5})?|\\[[0-9A-Fa-f:]+\\](:[0-9]{1,5})?");

	/** 設定キー：ポート */
	public static final String KEY_PORT = "redis.port";

	/** 既定のポート */
	public static final int DEFAULT_PORT = 6379;

	/** 設定キー：SSL を使うか */
	public static final String KEY_SSL = "redis.ssl";

	/** 設定キー：利用者（Redis 6 の ACL。空なら送らない） */
	public static final String KEY_USERNAME = "redis.username";

	/**
	 * 設定キー：パスワード（D-208。空なら送らない）
	 *
	 * <p>かつては書く場所が無く、<b>Redis に認証を掛けられなかった</b>。</p>
	 */
	public static final String KEY_PASSWORD = "redis.password";

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

		return MODE.get() != Mode.NONE;

	}

	/**
	 * どう繋ぐか
	 *
	 * @return	単体・クラスター・設定されていない
	 */
	public static Mode mode () {

		return MODE.get();

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

			Config config = redissonConfig();

			redissonClient = Redisson.create(config);

		} finally {

			clientLock.unlock();

		}

	}


	/**
	 * 設定の誤りを、繋がずに見つける（起動のときに {@code DBUtil.load} が呼ぶ）
	 *
	 * <p>
	 * <b>Redis は最初に使うときに繋ぐので、ここで見ないと誤りが起動のあとまで残る</b>。
	 * そのあいだ、セッションと流量制限は要求のたびに失敗し、キャッシュは黙って効かない。
	 * 設定が無ければ何もしない。
	 * </p>
	 *
	 * @throws IllegalStateException	設定が誤っている場合（両方書いた・ノードの書き方・知らない値）
	 */
	public static void validate () {

		if (Conf.conf().getString(KEY_HOST, "").isEmpty() && clusterNodes(Conf.conf()).isEmpty()) {
			return;
		}

		redissonConfig();

	}

	/**
	 * クラスターのノード（リストでも、, で区切った文字列でもよい。文字列は環境変数から渡すときのため）
	 *
	 * @param conf	設定
	 * @return	ノード（空の値は除く）
	 */
	private static List<String> clusterNodes (Conf conf) {

		if (!conf.has(KEY_CLUSTER_NODES)) {
			return List.of();
		}

		// リストはそのまま（[""] のような書き損じは、組み立てるときに止める）
		if (conf.config().getValue(KEY_CLUSTER_NODES).valueType() != ConfigValueType.STRING) {
			return conf.getStringListOptional(KEY_CLUSTER_NODES);
		}

		// 文字列は , で区切り、空の値は捨てる（空の文字列なら、クラスターではない）
		List<String> nodes = new ArrayList<>();

		for (String node : conf.getString(KEY_CLUSTER_NODES, "").split(",")) {
			if (!node.isBlank()) {
				nodes.add(node.trim());
			}
		}

		return nodes;

	}

	/**
	 * 設定から Redisson の設定を組み立てる（繋がない）
	 *
	 * @return	Redisson の設定
	 * @throws IllegalStateException	設定が誤っている場合（両方書いた・ノードの書き方・知らない値）
	 */
	static Config redissonConfig () {

		Config config = new Config();

		// 文字列として読み書きする（既定の Kryo は、読むときに何のクラスでも作る。D-208）
		config.setCodec(StringValueCodec.INSTANCE);

		List<String> nodes = clusterNodes(Conf.conf());
		String host = Conf.conf().getString(KEY_HOST, "");

		if (!nodes.isEmpty() && !host.isEmpty()) {
			throw new IllegalStateException("%s と %s が両方書いてあります。単体なら %s だけ、クラスターなら %s だけを書いてください"
				.formatted(KEY_HOST, KEY_CLUSTER_NODES, KEY_HOST, KEY_CLUSTER_NODES));
		}

		if (nodes.isEmpty()) {
			single(config.useSingleServer(), host);
		} else {
			cluster(config.useClusterServers(), nodes);
		}

		return config;

	}

	private static void single (SingleServerConfig server, String host) {

		common(server);

		server
			.setAddress("%s://%s:%s".formatted(scheme(), host, Conf.conf().getInt(KEY_PORT, DEFAULT_PORT)))
			.setConnectionMinimumIdleSize(Conf.conf().getInt(KEY_CONNECTION_MINIMUM_IDLE, 24))
			.setConnectionPoolSize(Conf.conf().getInt(KEY_CONNECTION_POOL_SIZE, 64))
			.setSubscriptionConnectionMinimumIdleSize(Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_MINIMUM_IDLE_SIZE, 1))
			.setSubscriptionConnectionPoolSize(Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_POOL_SIZE, 50));

	}

	/*
	 * 接続の数はマスター1つごと（マスターが3つなら、最小 24 × 3 本を開けておく）。
	 * レプリカからは読まないので、レプリカには開けておかない（フェイルオーバーで昇格すれば、マスターとして開く）
	 */
	private static void cluster (ClusterServersConfig cluster, List<String> nodes) {

		common(cluster);

		List<String> addresses = new ArrayList<>(nodes.size());

		for (String node : nodes) {

			String trimmed = node.trim();

			if (!NODE.matcher(trimmed).matches()) {
				throw new IllegalStateException(("%s の「%s」が読めません。\"host:port\" の形で書いてください"
					+ "（redis:// は付けません。TLS は redis.ssl = true で決めます）").formatted(KEY_CLUSTER_NODES, node));
			}

			boolean hasPort = trimmed.matches(".*:[0-9]{1,5}");
			int port = hasPort ? Integer.parseInt(trimmed.substring(trimmed.lastIndexOf(':') + 1)) : DEFAULT_PORT;

			if (port < 1 || port > MAX_PORT) {
				throw new IllegalStateException("%s の「%s」のポートが 1〜%d ではありません".formatted(KEY_CLUSTER_NODES, node, MAX_PORT));
			}

			addresses.add(scheme() + "://" + (hasPort ? trimmed : trimmed + ":" + DEFAULT_PORT));

		}

		cluster.setNodeAddresses(addresses);

		cluster
			.setScanInterval(millis(KEY_CLUSTER_SCAN_INTERVAL, Duration.ofSeconds(5)))
			.setReadMode(ReadMode.MASTER)
			.setMasterConnectionMinimumIdleSize(Conf.conf().getInt(KEY_CONNECTION_MINIMUM_IDLE, 24))
			.setMasterConnectionPoolSize(Conf.conf().getInt(KEY_CONNECTION_POOL_SIZE, 64))
			.setSlaveConnectionMinimumIdleSize(0)
			.setSubscriptionConnectionMinimumIdleSize(Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_MINIMUM_IDLE_SIZE, 1))
			.setSubscriptionConnectionPoolSize(Conf.conf().getInt(KEY_SUBSCRIPTION_CONNECTION_POOL_SIZE, 50));

	}

	/* 単体とクラスターで同じもの（認証・TLS・待ち時間・やり直し） */
	private static <T extends BaseConfig<T>> void common (T config) {

		String username = Conf.conf().getString(KEY_USERNAME, "");
		String password = Conf.conf().getString(KEY_PASSWORD, "");

		if (!username.isEmpty()) {
			config.setUsername(username);
		}
		if (!password.isEmpty()) {
			config.setPassword(password);
		}

		config
			.setSslVerificationMode(sslVerification())
			.setConnectTimeout(millis(KEY_CONNECTION_TIMEOUT, Duration.ofSeconds(10)))
			.setTimeout(millis(KEY_TIMEOUT, Duration.ofSeconds(3)))
			.setRetryAttempts(Conf.conf().getInt(KEY_RETRY_ATTEMPTS, 3))
			.setRetryDelay(new EqualJitterDelay(
				Conf.conf().getDuration(KEY_RETRY_MINIMUM_INTERVAL, Duration.ofMillis(500))
				, Conf.conf().getDuration(KEY_RETRY_MAXIMUM_INTERVAL, Duration.ofSeconds(2))))
			.setIdleConnectionTimeout(millis(KEY_IDLE_CONNECTION_TIMEOUT, Duration.ofSeconds(10)));

	}

	private static String scheme () {

		return Conf.conf().getBoolean(KEY_SSL, false) ? "rediss" : "redis";

	}

	/*
	 * 既定は strict（CA と証明書の名前の両方）。<b>none は受け付けない</b>（盗み見されても気づけない）。
	 * ca_only は、クラスターがノードを IP で返し、証明書の名前と合わないときのためのもの
	 */
	private static SslVerificationMode sslVerification () {

		String value = Conf.conf().getString(KEY_SSL_VERIFICATION, "strict").trim().toLowerCase(Locale.ROOT);

		return switch (value) {
			case "strict" -> SslVerificationMode.STRICT;
			case "ca_only" -> SslVerificationMode.CA_ONLY;
			default -> throw new IllegalStateException("%s = \"%s\" は使えません。strict か ca_only を書いてください"
				.formatted(KEY_SSL_VERIFICATION, value));
		};

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
