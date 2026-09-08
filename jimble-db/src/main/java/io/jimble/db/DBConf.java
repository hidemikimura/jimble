package io.jimble.db;

/**
 * DB設定情報
 */
public class DBConf {

	public String driver;

	public String url;

	public String user;

	public String password;

	public int maximumPoolSize;

	public int minimumIdle;

	public long idleTimeout = 600000;

	public long maxLifetime = 1800000;

	public long connectionTimeout = 30000;

	public String connectionInitSql = null;

	public String connectionTestQuery = null;

	public long keepaliveTime = 30000;

	public int fetchSize = 100;

	public String schema;

	/** DB 製品（要件 F-D-30。mysql / postgresql） */
	public String product;

	public String createDatabaseSql;

	public String connectionPoolType;

	public String transactionIsolation;

	public boolean longConnectionLog = false;

	public long longConnectionTime = 0;

	public static DBConf from (DBConf from) {

		DBConf conf = new DBConf();

		conf.driver = from.driver;
		conf.url = from.url;
		conf.user = from.user;
		conf.password = from.password;
		conf.maximumPoolSize = from.maximumPoolSize;
		conf.minimumIdle = from.minimumIdle;
		conf.idleTimeout = from.idleTimeout;
		conf.maxLifetime = from.maxLifetime;
		conf.connectionTimeout = from.connectionTimeout;
		conf.connectionInitSql = from.connectionInitSql;
		conf.connectionTestQuery = from.connectionTestQuery;
		conf.keepaliveTime = from.keepaliveTime;
		conf.fetchSize = from.fetchSize;
		conf.schema = from.schema;
		conf.product = from.product;
		conf.createDatabaseSql = from.createDatabaseSql;
		conf.connectionPoolType = from.connectionPoolType;
		conf.transactionIsolation = from.transactionIsolation;
		conf.longConnectionLog = from.longConnectionLog;
		conf.longConnectionTime = from.longConnectionTime;

		return conf;

	}

}
