package io.jimble.db;

/**
 * DB の接続設定
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられないので、<b>検証も、既定値の入れ替えも、
 * パスワードをログに出さない細工も、あとから入れられなくなる</b>。
 * </p>
 *
 * <p>
 * <b>書き込みはこのパッケージの中だけである。</b>設定は
 * {@code DBUtil} が段（全体 → 読み取り用 → サブ）に分けて重ね塗りするので、
 * 中身は組み立て中だけ動く。外から見えるのは読み取りだけにしてある。
 * </p>
 */
public final class DBConf {

	/** JDBC ドライバのクラス名 */
	private String driver;

	/** 接続先の URL */
	private String url;

	/** 接続する利用者 */
	private String user;

	/** 接続する利用者のパスワード */
	private String password;

	/** プールの上限 */
	private int maximumPoolSize;

	/** 遊ばせておく本数 */
	private int minimumIdle;

	/** 遊んでいる接続を切るまで（ミリ秒） */
	private long idleTimeout = 600000;

	/** 1本の接続の寿命（ミリ秒） */
	private long maxLifetime = 1800000;

	/** 接続を待つ上限（ミリ秒） */
	private long connectionTimeout = 30000;

	/** 接続した直後に流す SQL */
	private String connectionInitSql = null;

	/** 生きているか確かめる SQL */
	private String connectionTestQuery = null;

	/** 生存確認の間隔（ミリ秒） */
	private long keepaliveTime = 30000;

	/** 1回に取り寄せる行数 */
	private int fetchSize = 100;

	/** スキーマ名 */
	private String schema;

	/** DB 製品（要件 F-D-30。mysql / postgresql） */
	private String product;

	/** データベースを作る SQL */
	private String createDatabaseSql;

	/** 接続プールの種類 */
	private String connectionPoolType;

	/** トランザクション分離レベル */
	private String transactionIsolation;

	/** 長く掴んだ接続をログに出すか */
	private boolean longConnectionLog = false;

	/** 「長い」とみなす時間（ミリ秒） */
	private long longConnectionTime = 0;

	/**
	 * JDBC ドライバのクラス名
	 *
	 * @return	JDBC ドライバのクラス名
	 */
	public String driver () {

		return driver;

	}

	/**
	 * 接続先の URL
	 *
	 * @return	接続先の URL
	 */
	public String url () {

		return url;

	}

	/**
	 * 接続する利用者
	 *
	 * @return	接続する利用者
	 */
	public String user () {

		return user;

	}

	/**
	 * 接続する利用者のパスワード
	 *
	 * @return	接続する利用者のパスワード
	 */
	public String password () {

		return password;

	}

	/**
	 * プールの上限
	 *
	 * @return	プールの上限
	 */
	public int maximumPoolSize () {

		return maximumPoolSize;

	}

	/**
	 * 遊ばせておく本数
	 *
	 * @return	遊ばせておく本数
	 */
	public int minimumIdle () {

		return minimumIdle;

	}

	/**
	 * 遊んでいる接続を切るまで（ミリ秒）
	 *
	 * @return	遊んでいる接続を切るまで（ミリ秒）
	 */
	public long idleTimeout () {

		return idleTimeout;

	}

	/**
	 * 1本の接続の寿命（ミリ秒）
	 *
	 * @return	1本の接続の寿命（ミリ秒）
	 */
	public long maxLifetime () {

		return maxLifetime;

	}

	/**
	 * 接続を待つ上限（ミリ秒）
	 *
	 * @return	接続を待つ上限（ミリ秒）
	 */
	public long connectionTimeout () {

		return connectionTimeout;

	}

	/**
	 * 接続した直後に流す SQL
	 *
	 * @return	接続した直後に流す SQL
	 */
	public String connectionInitSql () {

		return connectionInitSql;

	}

	/**
	 * 生きているか確かめる SQL
	 *
	 * @return	生きているか確かめる SQL
	 */
	public String connectionTestQuery () {

		return connectionTestQuery;

	}

	/**
	 * 生存確認の間隔（ミリ秒）
	 *
	 * @return	生存確認の間隔（ミリ秒）
	 */
	public long keepaliveTime () {

		return keepaliveTime;

	}

	/**
	 * 1回に取り寄せる行数
	 *
	 * @return	1回に取り寄せる行数
	 */
	public int fetchSize () {

		return fetchSize;

	}

	/**
	 * スキーマ名
	 *
	 * @return	スキーマ名
	 */
	public String schema () {

		return schema;

	}

	/**
	 * DB 製品（要件 F-D-30。mysql / postgresql）
	 *
	 * @return	DB 製品（要件 F-D-30。mysql / postgresql）
	 */
	public String product () {

		return product;

	}

	/**
	 * データベースを作る SQL
	 *
	 * @return	データベースを作る SQL
	 */
	public String createDatabaseSql () {

		return createDatabaseSql;

	}

	/**
	 * 接続プールの種類
	 *
	 * @return	接続プールの種類
	 */
	public String connectionPoolType () {

		return connectionPoolType;

	}

	/**
	 * トランザクション分離レベル
	 *
	 * @return	トランザクション分離レベル
	 */
	public String transactionIsolation () {

		return transactionIsolation;

	}

	/**
	 * 長く掴んだ接続をログに出すか
	 *
	 * @return	長く掴んだ接続をログに出すか
	 */
	public boolean longConnectionLog () {

		return longConnectionLog;

	}

	/**
	 * 「長い」とみなす時間（ミリ秒）
	 *
	 * @return	「長い」とみなす時間（ミリ秒）
	 */
	public long longConnectionTime () {

		return longConnectionTime;

	}

	/**
	 * JDBC ドライバのクラス名 を決める
	 *
	 * @param driver	JDBC ドライバのクラス名
	 */
	void driver (String driver) {

		this.driver = driver;

	}

	/**
	 * 接続先の URL を決める
	 *
	 * @param url	接続先の URL
	 */
	void url (String url) {

		this.url = url;

	}

	/**
	 * 接続する利用者 を決める
	 *
	 * @param user	接続する利用者
	 */
	void user (String user) {

		this.user = user;

	}

	/**
	 * 接続する利用者のパスワード を決める
	 *
	 * @param password	接続する利用者のパスワード
	 */
	void password (String password) {

		this.password = password;

	}

	/**
	 * プールの上限 を決める
	 *
	 * @param maximumPoolSize	プールの上限
	 */
	void maximumPoolSize (int maximumPoolSize) {

		this.maximumPoolSize = maximumPoolSize;

	}

	/**
	 * 遊ばせておく本数 を決める
	 *
	 * @param minimumIdle	遊ばせておく本数
	 */
	void minimumIdle (int minimumIdle) {

		this.minimumIdle = minimumIdle;

	}

	/**
	 * 遊んでいる接続を切るまで（ミリ秒） を決める
	 *
	 * @param idleTimeout	遊んでいる接続を切るまで（ミリ秒）
	 */
	void idleTimeout (long idleTimeout) {

		this.idleTimeout = idleTimeout;

	}

	/**
	 * 1本の接続の寿命（ミリ秒） を決める
	 *
	 * @param maxLifetime	1本の接続の寿命（ミリ秒）
	 */
	void maxLifetime (long maxLifetime) {

		this.maxLifetime = maxLifetime;

	}

	/**
	 * 接続を待つ上限（ミリ秒） を決める
	 *
	 * @param connectionTimeout	接続を待つ上限（ミリ秒）
	 */
	void connectionTimeout (long connectionTimeout) {

		this.connectionTimeout = connectionTimeout;

	}

	/**
	 * 接続した直後に流す SQL を決める
	 *
	 * @param connectionInitSql	接続した直後に流す SQL
	 */
	void connectionInitSql (String connectionInitSql) {

		this.connectionInitSql = connectionInitSql;

	}

	/**
	 * 生きているか確かめる SQL を決める
	 *
	 * @param connectionTestQuery	生きているか確かめる SQL
	 */
	void connectionTestQuery (String connectionTestQuery) {

		this.connectionTestQuery = connectionTestQuery;

	}

	/**
	 * 生存確認の間隔（ミリ秒） を決める
	 *
	 * @param keepaliveTime	生存確認の間隔（ミリ秒）
	 */
	void keepaliveTime (long keepaliveTime) {

		this.keepaliveTime = keepaliveTime;

	}

	/**
	 * 1回に取り寄せる行数 を決める
	 *
	 * @param fetchSize	1回に取り寄せる行数
	 */
	void fetchSize (int fetchSize) {

		this.fetchSize = fetchSize;

	}

	/**
	 * スキーマ名 を決める
	 *
	 * @param schema	スキーマ名
	 */
	void schema (String schema) {

		this.schema = schema;

	}

	/**
	 * DB 製品（要件 F-D-30。mysql / postgresql） を決める
	 *
	 * @param product	DB 製品（要件 F-D-30。mysql / postgresql）
	 */
	void product (String product) {

		this.product = product;

	}

	/**
	 * データベースを作る SQL を決める
	 *
	 * @param createDatabaseSql	データベースを作る SQL
	 */
	void createDatabaseSql (String createDatabaseSql) {

		this.createDatabaseSql = createDatabaseSql;

	}

	/**
	 * 接続プールの種類 を決める
	 *
	 * @param connectionPoolType	接続プールの種類
	 */
	void connectionPoolType (String connectionPoolType) {

		this.connectionPoolType = connectionPoolType;

	}

	/**
	 * トランザクション分離レベル を決める
	 *
	 * @param transactionIsolation	トランザクション分離レベル
	 */
	void transactionIsolation (String transactionIsolation) {

		this.transactionIsolation = transactionIsolation;

	}

	/**
	 * 長く掴んだ接続をログに出すか を決める
	 *
	 * @param longConnectionLog	長く掴んだ接続をログに出すか
	 */
	void longConnectionLog (boolean longConnectionLog) {

		this.longConnectionLog = longConnectionLog;

	}

	/**
	 * 「長い」とみなす時間（ミリ秒） を決める
	 *
	 * @param longConnectionTime	「長い」とみなす時間（ミリ秒）
	 */
	void longConnectionTime (long longConnectionTime) {

		this.longConnectionTime = longConnectionTime;

	}

	/**
	 * 写しを作る
	 *
	 * <p>読み取り用やサブの設定は、全体の設定を写してから上書きする。</p>
	 *
	 * @param from	写す元
	 * @return	写し
	 */
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

	/**
	 * 文字列にする
	 *
	 * <p><b>パスワードは出さない。</b>ログや例外に乗って外へ出る。</p>
	 *
	 * @return	接続先が分かるだけのもの
	 */
	@Override
	public String toString () {

		return "DBConf(" + product + " " + url + " user=" + user + ")";

	}

}
