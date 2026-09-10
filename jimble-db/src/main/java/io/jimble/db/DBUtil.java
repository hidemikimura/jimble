package io.jimble.db;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.jimble.util.conf.Conf;
import io.jimble.util.metrics.Metrics;
import io.jimble.util.io.IOUtil;
import io.jimble.util.data.Data;
import io.jimble.db.cache.DBCache;
import io.jimble.db.lock.DBLock;
import io.jimble.db.log.DBLog;
import io.jimble.db.value.DBValue;
import io.jimble.db.version.DBVersion;
import io.jimble.core.lifecycle.Shutdown;
import io.jimble.util.log.Log;
import io.jimble.db.redis.lock.RedisLock;

import javax.sql.DataSource;
import java.util.concurrent.CopyOnWriteArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBUtil {

	// region データソース

	/* データソース */
	private static final Map<String, DBSource> dataSources = new HashMap<>();

	/* メインデータソース */
	private static DBSource mainDataSource = null;

	/**
	 * データソース一覧を取得する
	 *
	 * @return  データソース一覧
	 */
	public static List<DBSource> getDataSourceList () {

		return new ArrayList<>(dataSources.values());

	}

	/**
	 * メインデータソースを取得する
	 *
	 * @return	メインデータソース
	 */
	public static DBSource getMainDataSource() {

		return mainDataSource;

	}

	/**
	 * データソースを取得する
	 *
	 * @param dbName	DB名
	 * @return	データソース
	 */
	public static DBSource getDataSource(String dbName) {

		return dataSources.get(dbName);

	}

	// endregion

	// region ヘルスチェック

	/**
	 * ヘルスチェック
	 *
	 * @return	DBが一つでも利用できない場合 = false
	 */
	public static boolean healthCheck () {

		if (!isUseDB()) {
			return true;
		}

		for (DBSource dbSource : dataSources.values()) {
			try (
				DB db = new DB(dbSource)
			) {
				Data row = db.select("SELECT 1");
				if (row == null || db.isError()) {
					return false;
				}
			} catch (Throwable ex) {
				Log.error("DB health check failed: " + dbSource.name, ex);
			}
		}

		return true;

	}

	// endregion

	// region DB

	/* READ存在 */
	private static boolean useRead = false;

	/**
	 * read DB存在判定
	 *
	 * @return	存在する場合 = true
	 */
	public static boolean isUseRead () {

		return useRead;

	}

	/**
	 * DB利用判定
	 *
	 * @return  利用する場合 = true
	 */
	public static boolean isUseDB () {

		return mainDataSource != null;

	}

	/**
	 * DB一覧を取得する
	 *
	 * @return  DB一覧
	 */
	public static List<DB> getDBList () {

		List<DB> list = new ArrayList<>();
		for (DBSource dbSource : dataSources.values()) {
			list.add(new DB(dbSource));
		}

		return list;

	}

	/**
	 * DBを取得する
	 *
	 * @return  DB
	 */
	public static DB getMainDB () {

		return new DB(require(getMainDataSource(), "main"));

	}

	/**
	 * データソースがあることを確かめる（要件 F-X-05 / D-130）
	 *
	 * <p>
	 * <b>null をそのまま {@link DB} に渡さない。</b>
	 * {@link #load} は繋がらなかったときに<b>原因をログに出して false を返す</b>が、
	 * 戻り値を見ずに先へ進むと、ここで {@code NullPointerException} になる——
	 * <b>プロセスを殺した例外は DB のことを何も言わず、本当の原因は何十行も上にある</b>。
	 * </p>
	 *
	 * @param dbSource	データソース
	 * @param name		名前（ログ用）
	 * @return	データソース
	 */
	private static DBSource require (DBSource dbSource, String name) {

		if (dbSource != null) {
			return dbSource;
		}

		throw new IllegalStateException(
			("DB「%s」が読み込めていません。DBUtil.load が失敗しています"
				+ "（このすぐ上のログに原因が出ています。設定は db.%s）")
				.formatted(name, name));

	}

	/**
	 * DBを取得する
	 *
	 * @param dbName    DB名
	 * @return  DB
	 */
	public static DB getDB (String dbName) {

		return new DB(require(getDataSource(dbName), dbName));

	}

	/**
	 * DBを取得する
	 *
	 * @param dbSource  DBソース
	 * @return  DB
	 */
	public static DB getDB (DBSource dbSource) {

		return new DB(dbSource);

	}

	// endregion


	// region DB設定を読み込む

	/**
	 * DB設定を読み込む
	 *
	 * <p>
	 * <b>繋がらなくても例外を投げない。</b>原因をログに出して {@code false} を返す。
	 * <b>戻り値を見ずに先へ進むと、サーバーは起動してしまい</b>、
	 * 最初にリクエストが来たところで落ちる（要件 F-X-05 / D-130）。
	 * 呼ぶ側で見て、その場で止めること。
	 * </p>
	 *
	 * @param conf		Conf
	 * @param appCls	クラスパスの起点（マイグレーション SQL をここから探す）
	 * @return	全部読み込めた場合 = true。設定に {@code db} が無いときも true
	 */
	public static boolean load (Config conf, Class<?> appCls) {

		if (!conf.hasPath("db")) {
			return true;
		}

		// 「全部止める」に預ける（要件 D-77）。プールは最後に閉じたい
		Shutdown.add("DB", DBUtil::stop);

		for (String dbName : conf.getConfig("db").root().keySet()) {

			Log.info("load DB: " + dbName);

			DBSource dbSource = new DBSource();
			dbSource.name = dbName;

			Config configDb;
			try {
				configDb = conf.getConfig("db").getConfig(dbName);
			} catch (Exception ignore) {
				continue;
			}

			long start = 0;
			long end = 0;

			// write
			DBConf writeDbConf = new DBConf();
			boolean isCreated = false;
			while (true) {
				try {
					start = System.currentTimeMillis();
					read(writeDbConf, configDb);
					dbSource.conf = writeDbConf;
					if (dbSource.conf.schema == null || dbSource.conf.schema.isEmpty()) {
						dbSource.conf.schema = dbName;
					}
					dbSource.dataSource = createDataSource(writeDbConf);
					end = System.currentTimeMillis();

					{
						/*
						 * 移送元はここで new Exception("Unknown database") に潰していた。
						 * <b>パスワード違いも「データベースが無い」に見えてしまい、</b>
						 * 作りにいって二度失敗する。元の例外をそのまま上げる。
						 */
						dbSource.dataSource.getConnection().close();
					}

					String version = "unknown";
					try (
						Connection connection = dbSource.dataSource.getConnection();
						PreparedStatement st = connection.prepareStatement(dbSource.dialect().versionSql());
						ResultSet resultSet = st.executeQuery()
					) {
						resultSet.next();
						version = resultSet.getString("version");
					} catch (Exception ex) {
						Log.error(ex);
					}
					Log.info("success create write datasource: " + dbName + ":" + version + " (" + (end - start) +"ms)");
					break;
				} catch (Exception ex) {
					/*
					 * 「そんなデータベースは無い」の文言は製品ごとに違う（要件 F-D-30）。
					 * MySQL は Unknown database、PostgreSQL は database "x" does not exist。
					 */
					if (dbSource.dialect().isUnknownDatabase(ex.getMessage())) {
						if (writeDbConf.createDatabaseSql == null || writeDbConf.createDatabaseSql.isEmpty()) {
							Log.error("failed create write datasource: " + dbName, ex);
							return false;
						}
						if (isCreated) {
							Log.error("failed create write datasource: " + dbName, ex);
							return false;
						}
						isCreated = true;
						String url = writeDbConf.url;
						writeDbConf.url = dbSource.dialect().maintenanceUrl(url);
						try {
							DataSource dataSource = createDataSource(writeDbConf);
							PreparedStatement st = null;
							try (
								Connection connection = dataSource.getConnection()
							) {
								st = connection.prepareStatement(writeDbConf.createDatabaseSql);
								st.execute();
							} catch (Exception ex3) {
								Log.error("failed create write datasource: " + dbName, ex);
								return false;
							} finally {
								writeDbConf.url = url;
								if (st != null) {
									IOUtil.close(st);
								}
							}
						} catch (Exception ex2) {
							Log.error("failed create write datasource: " + dbName, ex);
							return false;
						}
					} else {
						Log.error("failed create write datasource: " + dbName, ex);
						return false;
					}
				}
			}

			// read
			if (configDb.hasPath("read")) {
				try {
					start = System.currentTimeMillis();
					DBSource readDbSource = new DBSource();
					readDbSource.name = dbName;
					DBConf readDbConf = DBConf.from(writeDbConf);
					read(readDbConf, configDb.getConfig("read"));
					readDbSource.conf = readDbConf;
					if (readDbSource.conf.schema == null || readDbSource.conf.schema.isEmpty()) {
						readDbSource.conf.schema = dbName;
					}
					readDbSource.dataSource = createDataSource(readDbConf);
					dbSource.readSource = readDbSource;
					useRead = true;
					end = System.currentTimeMillis();
					Log.info("success create read datasource: " + dbName + " (" + (end - start) +"ms)");
				} catch (Exception ex) {
					Log.error("failed create read datasource: " + dbName, ex);
					return false;
				}
			}

			// subs
			if (configDb.hasPath("subs")) {
				for (String subDbName : configDb.getConfig("subs").root().keySet()) {
					Config subConfig = configDb.getConfig("subs").getConfig(subDbName);

					try {
						start = System.currentTimeMillis();
						DBSource subDbSource = new DBSource();
						subDbSource.parent = dbSource;
						subDbSource.name = dbName;
						DBConf subDbConf = DBConf.from(writeDbConf);
						read(subDbConf, subConfig);
						subDbSource.conf = subDbConf;
						if (subDbSource.conf.schema == null || subDbSource.conf.schema.isEmpty()) {
							subDbSource.conf.schema = dbName;
						}
						subDbSource.dataSource = createDataSource(subDbConf);

						if (subConfig.hasPath("read")) {
							DBSource readDbSource = new DBSource();
							readDbSource.name = dbName;
							DBConf readDbConf = DBConf.from(subDbConf);
							read(readDbConf, subConfig.getConfig("read"));
							readDbSource.conf = readDbConf;
							if (readDbSource.conf.schema == null || readDbSource.conf.schema.isEmpty()) {
								readDbSource.conf.schema = dbName;
							}
							readDbSource.dataSource = createDataSource(readDbConf);
							subDbSource.readSource = readDbSource;
						}

						dbSource.subsDbSourceMap.put(subDbName, subDbSource);
						end = System.currentTimeMillis();
						Log.info("success create sub datasource: " + dbName + " (" + (end - start) +"ms)");
					} catch (Exception ex) {
						Log.error("failed create subs datasource: " + dbName, ex);
						return false;
					}
				}
			}

			dataSources.put(dbName, dbSource);
			if (mainDataSource == null || (configDb.hasPath("main") && configDb.getBoolean("main"))) {
				mainDataSource = dbSource;

				/*
				 * データソースが分からないところで使う既定の方言（要件 F-D-30）。
				 * builder.sql() を引数なしで呼んだとき、主データソースの製品になる。
				 */
				io.jimble.db.dialect.Dialects.defaultDialect(dbSource.dialect());
			}

			// DB version
			DBVersion.load(getDB(dbName));

			// DB log
			DBLog.init(getDB(dbName));

			// DB value
			DBValue.init(getDB(dbName));

			// DB lock
			DBLock.init(getDB(dbName));

			// Redis Lock
			RedisLock.init(getDB(dbName));

			// DB cache
			DBCache.init(getDB(dbName));

			// データソースごとの追加処理
			// （マイグレーション・レートリミットなど、上位モジュールが登録する）
			for (DataSourceTask task : DATA_SOURCE_TASKS) {
				task.run(dbSource, getDB(dbName), appCls);
			}

		}

		// 接続プールの使用率をメトリクスへ（要件 NF-O-04）
		registerPoolMetrics();

		if (mainDataSource != null) {
			// DB sticky
			DBSticky.init();

			// 全データソースの初期化後の処理
			// （バッチ・スケジューラ・コードマイグレーションなど、上位モジュールが登録する）
			for (Runnable task : STARTUP_TASKS) {
				task.run();
			}
		}

		return true;

	}

	// endregion

	// region 起動タスク

	/**
	 * データソースごとに1回実行する処理
	 *
	 * <p>
	 * マイグレーションやレートリミットのテーブル作成など、<b>DB より上位のモジュールが登録する。</b>
	 * これにより jimble-db が上位モジュールを知らずに済む。
	 * </p>
	 */
	@FunctionalInterface
	public interface DataSourceTask {

		/**
		 * 実行する
		 *
		 * @param dbSource	データソース
		 * @param db		DB
		 * @param appClass	アプリケーションクラス
		 */
		void run (DBSource dbSource, DB db, Class<?> appClass);

	}

	/* データソースごとの処理 */
	private static final List<DataSourceTask> DATA_SOURCE_TASKS = new CopyOnWriteArrayList<>();

	/* 全データソース初期化後の処理 */
	private static final List<Runnable> STARTUP_TASKS = new CopyOnWriteArrayList<>();

	/**
	 * データソースごとの処理を登録する
	 *
	 * <p><b>{@link #load(Config, Class)} より前に呼ぶこと。</b></p>
	 *
	 * @param task	処理
	 */
	public static void addDataSourceTask (DataSourceTask task) {

		DATA_SOURCE_TASKS.add(task);

	}

	/**
	 * 全データソース初期化後の処理を登録する
	 *
	 * <p><b>{@link #load(Config, Class)} より前に呼ぶこと。</b></p>
	 *
	 * @param task	処理
	 */
	public static void addStartupTask (Runnable task) {

		STARTUP_TASKS.add(task);

	}

	// endregion

	// region 接続プールのメトリクス

	/** 接続プールのメトリクスの名前の頭 */
	private static final String POOL_METRICS_PREFIX = "db.pool.";

	/**
	 * 接続プールの使用率をメトリクスに登録する（要件 NF-O-04）
	 *
	 * <p>
	 * <b>読むだけで、SQL は打たない。</b>プールが自分で持っている数を引くだけである
	 * （{@code Metrics.snapshot()} が DB を触ると、<b>DB が詰まっているときに限って
	 * メトリクスも取れなくなる</b>）。
	 * </p>
	 */
	private static void registerPoolMetrics () {

		for (DBSource source : dataSources.values()) {

			registerPoolMetrics(source.name, source);

			for (Map.Entry<String, DBSource> sub : source.subsDbSourceMap.entrySet()) {
				registerPoolMetrics("%s.%s".formatted(source.name, sub.getKey()), sub.getValue());
			}

		}

	}

	/**
	 * 1つ登録する（読み取り用があればそれも）
	 *
	 * @param name		名前
	 * @param source	データソース
	 */
	private static void registerPoolMetrics (String name, DBSource source) {

		registerPoolMetrics(name, source.dataSource);

		if (source.readSource != null) {
			registerPoolMetrics("%s.read".formatted(name), source.readSource.dataSource);
		}

	}

	/**
	 * プールの数をゲージにする
	 *
	 * @param name			名前
	 * @param dataSource	データソース
	 */
	private static void registerPoolMetrics (String name, DataSource dataSource) {

		String prefix = POOL_METRICS_PREFIX + name + ".";

		if (dataSource instanceof HikariDataSource hikari) {

			Metrics.gauge(prefix + "active", () -> hikariValue(hikari, HikariPoolMXBean::getActiveConnections));
			Metrics.gauge(prefix + "idle", () -> hikariValue(hikari, HikariPoolMXBean::getIdleConnections));
			Metrics.gauge(prefix + "total", () -> hikariValue(hikari, HikariPoolMXBean::getTotalConnections));
			Metrics.gauge(prefix + "waiting", () -> hikariValue(hikari, HikariPoolMXBean::getThreadsAwaitingConnection));

			return;

		}

		if (dataSource instanceof AgroalDataSource agroal) {

			Metrics.gauge(prefix + "active", () -> agroal.getMetrics().activeCount());
			Metrics.gauge(prefix + "idle", () -> agroal.getMetrics().availableCount());
			Metrics.gauge(prefix + "total", () -> agroal.getMetrics().activeCount() + agroal.getMetrics().availableCount());
			Metrics.gauge(prefix + "waiting", () -> agroal.getMetrics().awaitingCount());

		}

	}

	/**
	 * Hikari から1つ引く
	 *
	 * <p>
	 * <b>プールが立ち上がる前と閉じたあとは MXBean が null になる。</b>
	 * そのまま呼ぶと、メトリクスを見るたびに警告が出る
	 * </p>
	 *
	 * @param hikari	データソース
	 * @param reader	引くもの
	 * @return 値。取れなければ 0
	 */
	private static long hikariValue (HikariDataSource hikari, java.util.function.ToIntFunction<HikariPoolMXBean> reader) {

		HikariPoolMXBean pool = hikari.getHikariPoolMXBean();

		return pool == null ? 0 : reader.applyAsInt(pool);

	}

	/**
	 * 登録したゲージを外す
	 */
	private static void removePoolMetrics () {

		for (String name : Metrics.gaugeNames()) {
			if (name.startsWith(POOL_METRICS_PREFIX)) {
				Metrics.removeGauge(name);
			}
		}

	}

	// endregion

	// region 終了処理

	/**
	 * 終了処理
	 */
	public static void stop () {

		for (DBSource source : dataSources.values()) {
			try {
				((HikariDataSource) source.dataSource).close();
			} catch (Exception ignore) {}
			try {
				((AgroalDataSource) source.dataSource).close();
			} catch (Exception ignore) {}
			if (source.readSource != null) {
				try {
					((HikariDataSource) source.readSource.dataSource).close();
				} catch (Exception ignore) {}
				try {
					((AgroalDataSource) source.readSource.dataSource).close();
				} catch (Exception ignore) {}
			}
			for (DBSource sub : source.subsDbSourceMap.values()) {
				try {
					((HikariDataSource) sub.dataSource).close();
				} catch (Exception ignore) {}
				try {
					((AgroalDataSource) sub.dataSource).close();
				} catch (Exception ignore) {}
				if (sub.readSource != null) {
					try {
						((HikariDataSource) sub.readSource.dataSource).close();
					} catch (Exception ignore) {}
					try {
						((AgroalDataSource) sub.readSource.dataSource).close();
					} catch (Exception ignore) {}
				}
			}
		}

		removePoolMetrics();

		dataSources.clear();

		Log.info("DB stoppped");

	}

	// endregion

	// region データソースを作成する

	/**
	 * データソースを作成する
	 *
	 * @param dbConf	DB設定
	 * @return	データソース
	 */
	private static DataSource createDataSource (DBConf dbConf) {

		if ("agroal".equalsIgnoreCase(dbConf.connectionPoolType)) {
			try {
				return AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
					.dataSourceImplementation(AgroalDataSourceConfiguration.DataSourceImplementation.AGROAL)
					/*
					 * 接続プールの使用率を出すため（要件 NF-O-04）。
					 * Agroal は<b>これを切ると getMetrics() が 0 しか返さない</b>。
					 * 中身は取得・返却のたびのカウンタで、費用は無視できる
					 */
					.metricsEnabled(true)
					.connectionPoolConfiguration(cp -> {
							cp
								.connectionValidator(AgroalConnectionPoolConfiguration.ConnectionValidator.defaultValidator())
								.connectionFactoryConfiguration(cf -> {
										cf
											.autoCommit(true)
										;
										if (dbConf.url != null && !dbConf.url.isEmpty()) {
											cf.jdbcUrl(dbConf.url);
										}
										if (dbConf.driver != null && !dbConf.driver.isEmpty()) {
											cf.connectionProviderClassName(dbConf.driver);
										}
										if (dbConf.user != null && !dbConf.user.isEmpty()) {
											cf.principal(new NamePrincipal(dbConf.user));
										}
										if (dbConf.password != null) {
											cf.credential(new SimplePassword(dbConf.password));
										}
										if (dbConf.transactionIsolation != null) {
											try {
												AgroalConnectionFactoryConfiguration.TransactionIsolation transactionIsolation = AgroalConnectionFactoryConfiguration.TransactionIsolation.valueOf(dbConf.transactionIsolation);
												cf.jdbcTransactionIsolation(transactionIsolation);
											} catch (Exception ignore) {}
										}
										if (dbConf.connectionTimeout > 0) {
											cf.loginTimeout(Duration.ofMillis(dbConf.connectionTimeout));
										}
										if (dbConf.connectionTestQuery != null && !dbConf.connectionTestQuery.isEmpty()) {
											cf.initialSql(dbConf.connectionTestQuery);
										} else if (dbConf.connectionInitSql != null && !dbConf.connectionInitSql.isEmpty()) {
											cf.initialSql(dbConf.connectionInitSql);
										}
										return cf;
									}
								)
							;
							if (dbConf.minimumIdle > 0) {
								cp.minSize(dbConf.minimumIdle);
								cp.initialSize(dbConf.minimumIdle);
							}
							if (dbConf.maximumPoolSize > 0) {
								cp.maxSize(dbConf.maximumPoolSize);
							}
							if (dbConf.connectionTimeout > 0) {
								cp.acquisitionTimeout(Duration.ofMillis(dbConf.connectionTimeout + 1000));
							}
							if (dbConf.keepaliveTime > 0) {
								cp.idleValidationTimeout(Duration.ofMillis(dbConf.idleTimeout));
							}
							if (dbConf.idleTimeout > 0) {
								cp.reapTimeout(Duration.ofMillis(dbConf.idleTimeout));
							}
							if (dbConf.maxLifetime > 0) {
								cp.maxLifetime(Duration.ofMillis(dbConf.maxLifetime));
							}
							return cp;
						}
					)
				);
			} catch (Exception ignore) {}
		}

		HikariConfig hikariConfig = new HikariConfig();
		if (dbConf.driver != null && !dbConf.driver.isEmpty()) {
			hikariConfig.setDriverClassName(dbConf.driver);
		}
		if (dbConf.url != null && !dbConf.url.isEmpty()) {
			hikariConfig.setJdbcUrl(dbConf.url);
		}
		if (dbConf.user != null && !dbConf.user.isEmpty()) {
			hikariConfig.setUsername(dbConf.user);
		}
		if (dbConf.password != null) {
			hikariConfig.setPassword(dbConf.password);
		}
		if (dbConf.maximumPoolSize > 0) {
			hikariConfig.setMaximumPoolSize(dbConf.maximumPoolSize);
		}
		if (dbConf.minimumIdle > 0) {
			hikariConfig.setMinimumIdle(dbConf.minimumIdle);
		}
		if (dbConf.idleTimeout > 0) {
			hikariConfig.setIdleTimeout(dbConf.idleTimeout);
		}
		if (dbConf.maxLifetime > 0) {
			hikariConfig.setMaxLifetime(dbConf.maxLifetime);
		}
		if (dbConf.connectionTimeout > 0) {
			hikariConfig.setConnectionTimeout(dbConf.connectionTimeout);
		}
		if (dbConf.connectionInitSql != null && !dbConf.connectionInitSql.isEmpty()) {
			hikariConfig.setConnectionInitSql(dbConf.connectionInitSql);
		}
		if (dbConf.connectionTestQuery != null && !dbConf.connectionTestQuery.isEmpty()) {
			hikariConfig.setConnectionTestQuery(dbConf.connectionTestQuery);
		}
		if (dbConf.keepaliveTime > 0) {
			hikariConfig.setKeepaliveTime(dbConf.keepaliveTime);
		}

		return new HikariDataSource(hikariConfig);

	}

	/**
	 * DB設定を読み込む
	 *
	 * @param dbConf	DB設定
	 * @param config	アプリ設定
	 */
	private static void read (DBConf dbConf, Config config) {

		Conf innerConf = new Conf(config);

		if (config.hasPath("driver")) {
			dbConf.driver = innerConf.getString("driver");
		}
		if (config.hasPath("url")) {
			dbConf.url = innerConf.getString("url");
		}
		if (config.hasPath("username")) {
			dbConf.user = innerConf.getString("username");
		}
		if (config.hasPath("password")) {
			dbConf.password = innerConf.getString("password");
		}
		if (config.hasPath("maximumPoolSize")) {
			dbConf.maximumPoolSize = innerConf.getInt("maximumPoolSize");
		}
		if (config.hasPath("minimumIdle")) {
			dbConf.minimumIdle = innerConf.getInt("minimumIdle");
		}
		if (config.hasPath("idleTimeout")) {
			dbConf.idleTimeout = innerConf.getLong("idleTimeout");
		}
		if (config.hasPath("maxLifetime")) {
			dbConf.maxLifetime = innerConf.getLong("maxLifetime");
		}
		if (config.hasPath("connectionTimeout")) {
			dbConf.connectionTimeout = innerConf.getLong("connectionTimeout");
		}
		if (config.hasPath("connectionInitSql")) {
			dbConf.connectionInitSql = innerConf.getString("connectionInitSql");
		}
		if (config.hasPath("connectionTestQuery")) {
			dbConf.connectionTestQuery = innerConf.getString("connectionTestQuery");
		}
		if (config.hasPath("keepaliveTime")) {
			dbConf.keepaliveTime = innerConf.getLong("keepaliveTime");
		}
		if (config.hasPath("fetchSize")) {
			dbConf.fetchSize = innerConf.getInt("fetchSize");
		}
		if (config.hasPath("scheme")) {
			dbConf.schema = innerConf.getString("scheme");
		}
		// DB 製品（要件 F-D-30）。書かなければ mysql
		if (config.hasPath(io.jimble.db.dialect.Dialects.KEY_PRODUCT)) {
			dbConf.product = innerConf.getString(io.jimble.db.dialect.Dialects.KEY_PRODUCT);
		}
		if (config.hasPath("create_database_sql")) {
			dbConf.createDatabaseSql = innerConf.getString("create_database_sql");
		}
		if (config.hasPath("connection_pool_type")) {
			dbConf.connectionPoolType = innerConf.getString("connection_pool_type");
		}
		if (config.hasPath("transaction_isolation")) {
			dbConf.transactionIsolation = innerConf.getString("transaction_isolation");
		}
		if (config.hasPath("long_connection_log")) {
			dbConf.longConnectionLog = innerConf.getBoolean("long_connection_log");
		}
		if (config.hasPath("long_connection_time_ms")) {
			dbConf.longConnectionTime = innerConf.getLong("long_connection_time_ms");
		}

	}

	// endregion

}
