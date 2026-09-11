package io.jimble.db;

import java.util.Set;

/**
 * jimble 自身が作るテーブルの名前（D-68）
 *
 * <p>
 * <b>名前はここにしか書かない。</b>
 * 作る側（{@code Migration} / {@code DBLock} / {@code BatchTables} …）も、
 * 生成対象から外す側（{@code GeneratorConf#excludeTables()}）も、
 * ここの定数を使う。
 * </p>
 *
 * <h2>なぜ1か所なのか</h2>
 * <p>
 * 以前は<b>作る側と外す側の2か所に同じ名前が並んでいた</b>。
 * テーブルを1つ増やしたときに<b>外す側を足し忘れる</b>と、
 * フレームワークの内部テーブルがアプリのテーブル定義クラスとして生成される。
 * 生成された時点では何も壊れないので、
 * <b>アプリのコードに紛れてから気づく</b>ことになる。
 * </p>
 *
 * <h2>ここに書けないもの</h2>
 * <p>
 * <b>MQ のテーブルは名前をアプリが決める</b>（{@code mq_blog} など）ので書けない。
 * DB セッションのテーブルも<b>設定で変えられる</b>ので、
 * ここにあるのは既定名だけである。どちらも、変えているなら
 * {@code codegen.exclude_tables} に足すこと。
 * </p>
 */
public final class FrameworkTables {

	/** マイグレーション情報 */
	public static final String MIGRATION = "migration";

	/** マイグレーション履歴 */
	public static final String MIGRATION_HISTORY = "migration_history";

	/** コードマイグレーション情報 */
	public static final String MIGRATION_CODE = "migration_code";

	/** ロック情報 */
	public static final String DB_LOCK = "db_lock";

	/** 汎用ログ情報 */
	public static final String DB_LOG = "db_log";

	/** 汎用値情報 */
	public static final String DB_VALUE = "db_value";

	/** 汎用キャッシュ情報 */
	public static final String DB_CACHE = "db_cache";

	/** DB sticky */
	public static final String DB_STICKY = "db_sticky";

	/** Redis ロック情報 */
	public static final String REDIS_LOCK = "redis_lock";

	/** SQL 結果キャッシュ（{@code sql_cache.store = "db"} のとき） */
	public static final String SQL_CACHE = "sql_cache";

	/** SQL 結果キャッシュのタグ（{@code sql_cache.store = "db"} のとき） */
	public static final String SQL_CACHE_TAG = "sql_cache_tag";

	/** 流量制限（{@code rate_limit.store = "db"} のとき） */
	public static final String RATE_LIMIT = "rate_limit";

	/** DB セッション（<b>既定名</b>。{@code session.table} で変えられる） */
	public static final String SESSION = "session";

	/** バッチマスタ */
	public static final String BATCH_MASTER = "batch_master";

	/** バッチ履歴 */
	public static final String BATCH_HISTORY = "batch_history";

	/** バッチ実行情報 */
	public static final String BATCH_EXECUTE_INFO = "batch_execute_info";

	/** ログインの失敗回数（要件 F-W-29） */
	public static final String AUTH_ATTEMPT = "auth_attempt";

	/**
	 * 全部
	 *
	 * <p>
	 * <b>定数を足したらここにも足すこと。</b>
	 * 足し忘れは {@code FrameworkTablesTest} が落ちて教える。
	 * </p>
	 */
	public static final Set<String> ALL = Set.of(
		MIGRATION
		, MIGRATION_HISTORY
		, MIGRATION_CODE
		, DB_LOCK
		, DB_LOG
		, DB_VALUE
		, DB_CACHE
		, DB_STICKY
		, REDIS_LOCK
		, SQL_CACHE
		, SQL_CACHE_TAG
		, RATE_LIMIT
		, SESSION
		, BATCH_MASTER
		, BATCH_HISTORY
		, BATCH_EXECUTE_INFO
		, AUTH_ATTEMPT
	);

	private FrameworkTables () {}

}
