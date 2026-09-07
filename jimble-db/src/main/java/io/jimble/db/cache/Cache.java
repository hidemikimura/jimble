package io.jimble.db.cache;

import io.jimble.util.conf.Conf;
import io.jimble.util.io.FileUtil;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.DBCache;
import io.jimble.db.redis.RedisCache;
import io.jimble.util.log.Log;

import java.time.Duration;

/**
 * キャッシュ
 */
public class Cache {

	/** キャッシュ実装：DB */
	public static final String TYPE_DB = "db";

	/** キャッシュ実装：Redis */
	public static final String TYPE_REDIS = "redis";

	/** キャッシュ実装：メモリ */
	public static final String TYPE_MEMORY = "memory";

	/** 設定キー：キャッシュ実装 */
	public static final String KEY_TYPE = "cache.type";

	/** 設定キー：ファイル配置ディレクトリ */
	public static final String KEY_TEMP_DIR = "cache.temp_dir";

	/** 既定のキャッシュ実装 */
	public static final String DEFAULT_TYPE = TYPE_DB;

	/**
	 * キャッシュ実装の名前
	 *
	 * @return	{@code db} / {@code redis} / {@code memory}
	 */
	public static String type () {

		return Conf.conf().getString(KEY_TYPE, DEFAULT_TYPE);

	}

	/**
	 * キャッシュインスタンスを取得する
	 *
	 * @return  キャッシュインスタンス
	 */
	public static ICache instance () {

		return instance(DBUtil.getMainDB());

	}

	/**
	 * キャッシュインスタンスを取得する
	 *
	 * @param db    DB
	 * @return  キャッシュインスタンス
	 */
	public static ICache instance (DB db) {

		return switch (type().toLowerCase()) {
			case TYPE_REDIS -> new RedisCache();
			case TYPE_MEMORY -> new MemoryCache();
			case TYPE_DB -> new DBCache(db);
			default -> {
				// 設定の書き間違いで黙って別の実装になるより、気づける形にする
				Log.warn("不明なキャッシュ実装です。DB キャッシュで動かします: " + type());
				yield new DBCache(db);
			}
		};

	}

	/**
	 * tempディレクトリパスを取得する
	 *
	 * @return  tempディレクトリパス
	 */
	public static String getTempDirPath () {

		/*
		 * 移送元は static 初期化で Conf.conf().getString("cache.tmpDir") を呼んでいた。
		 * 既定値なしの取得なので、設定を書いていないと
		 * このクラスに触れた瞬間にクラス初期化で落ちていた。
		 */
		String tempDir = Conf.conf().getString(KEY_TEMP_DIR, "");

		return tempDir.isEmpty() ? FileUtil.getTempDir().getAbsolutePath() : tempDir;

	}

	/**
	 * ファイルでレスポンスするか判定する
	 *
	 * @param contentLength Content-Length
	 * @return  ファイルでレスポンスする場合 = true
	 */
	public static boolean isFileResponse (long contentLength) {

		return contentLength >= 256 * 1024;

	}

	/**
	 * ローディングキャッシュ
	 *
	 * @param key       キー
	 * @param db        DB
	 * @param loader    ローダー
	 * @return  ローディングキャッシュ
	 * @param <V>   キャッシュ値型
	 */
	public static <V> LoadingCache<V> loadingCache (
		String key
		, DB db
		, LoadingCache.LoadingCacheLoader<V> loader
	) {

		return loadingCache(key, db, null, loader);

	}

	/**
	 * ローディングキャッシュ
	 *
	 * @param key       キー
	 * @param db        DB
	 * @param duration  期限
	 * @param loader    ローダー
	 * @return  ローディングキャッシュ
	 * @param <V>   キャッシュ値型
	 */
	public static <V> LoadingCache<V> loadingCache (
		String key
		, DB db
		, Duration duration
		, LoadingCache.LoadingCacheLoader<V> loader
	) {

		return new LoadingCache<V>(key, loader, db, duration);

	}

	/**
	 * ローディングキャッシュ（複数キー）
	 *
	 * @param key		ベースキー
	 * @param db		DB
	 * @param loader	ローダー
	 * @return	ローディングキャッシュ
	 * @param <K>	キー型
	 * @param <V>	値型
	 */
	public static <K, V> LoadingCacheMulti<K, V> loadingCacheMultiKey (
		String key
		, DB db
		, LoadingCacheMulti.LoadingCacheMultiLoader<K, V> loader
	) {

		return loadingCacheMultiKey(key, db, null, loader);

	}

	/**
	 * ローディングキャッシュ（複数キー）
	 *
	 * @param key		ベースキー
	 * @param db		DB
	 * @param duration	期間
	 * @param loader	ローダー
	 * @return	ローディングキャッシュ
	 * @param <K>	キー型
	 * @param <V>	値型
	 */
	public static <K, V> LoadingCacheMulti<K, V> loadingCacheMultiKey (
		String key
		, DB db
		, Duration duration
		, LoadingCacheMulti.LoadingCacheMultiLoader<K, V> loader
	) {

		return new LoadingCacheMulti<>(key, loader, db, duration);

	}

}
