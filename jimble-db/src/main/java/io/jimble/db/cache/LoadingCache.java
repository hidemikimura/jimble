package io.jimble.db.cache;

import io.jimble.db.DB;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ローディングキャッシュ
 *
 * @param <V>   値型
 */
public class LoadingCache<V> {

	/* ロック */
	private final ReentrantLock lock = new ReentrantLock();

	/* ロック */
	private final ReentrantLock lockServerCheck = new ReentrantLock();

	/* キー */
	private final String key;

	/* DB */
	private final DB db;

	/* ローダー */
	private final LoadingCacheLoader<V> loader;

	/* 期限 */
	private Duration duration = null;

	/* キャッシュデータ */
	private V cache = null;

	/* 最終取得日時 */
	private Date lastGetAt = null;

	/* ロード中 */
	private boolean isLoading = false;

	/**
	 * コンストラクタ
	 *
	 * @param key       キー
	 * @param loader    ローダー
	 * @param db        DB
	 * @param duration  期限
	 */
	public LoadingCache (String key, LoadingCacheLoader<V> loader, DB db, Duration duration) {
		this.key = "loading_cache_" + key;
		this.db = db;
		this.loader = loader;
		this.duration = duration;
	}

	/**
	 * キャッシュインスタンス
	 *
	 * @return  キャッシュインスタンス
	 */
	private ICache cacheInstance () {

		return Cache.instance(db());

	}

	/**
	 * DB
	 *
	 * @return  DB
	 */
	private DB db () {

		return db.newDB();

	}

	/**
	 * 取得する
	 *
	 * @return  値
	 */
	public V get () throws Exception {

		V result = cache;
		if (isUseCache(true)) {
			return result;
		}

		try {
			lock.lock();

			if (isUseCache(false)) {
				return cache;
			}

			isLoading = true;

			cache = loader.load(db());
			cacheInstance().set(this.key, "1", "application/text");
			lastGetAt = new Date();
		} finally {
			isLoading = false;
			lock.unlock();
		}

		return cache;

	}

	/* 前回サーバーチェック時刻 */
	private Date lastCheckServerAt = null;

	/* サーバーチェック中 */
	private boolean isServerChecking = false;

	/**
	 * キャッシュ利用判定
	 *
	 * @param isCheckServer 要サーバーチェック
	 * @return  キャッシュを利用する場合 = true
	 */
	private boolean isUseCache (boolean isCheckServer) {

		// ローディング中でキャッシュが存在する場合、キャッシュを利用する
		if (isLoading && cache != null) {
			return true;
		}

		// 要リフレッシュ判定
		boolean isNeedRefresh = true;

		Date _lastGetAt = lastGetAt;
		if (_lastGetAt != null) {
			// 一度以上取得している場合
			if (duration == null) {
				// 無期限の場合、リフレッシュ不要
				isNeedRefresh = false;
			} else if (System.currentTimeMillis() <= _lastGetAt.getTime() + duration.toMillis()) {
				// 期限内の場合、リフレッシュ不要
				isNeedRefresh = false;
			}
		}

		if (isCheckServer && !isNeedRefresh) {
			// リフレッシュ不要判定の場合1分に1回だけサーバーチェックを行う
			if (lastCheckServerAt == null || System.currentTimeMillis() > lastCheckServerAt.getTime() + 60000) {
				if (!isServerChecking) {
					try {
						lockServerCheck.lock();

						if (isServerChecking
							|| (lastCheckServerAt != null && System.currentTimeMillis() <= lastCheckServerAt.getTime() + 60000)) {
							return true;
						}

						isServerChecking = true;

						lastCheckServerAt = new Date();
						CacheData cacheData = cacheInstance().get(key);
						return cacheData != null && !cacheData.isError() && cacheData.objectCreatedAt().getTime() <= _lastGetAt.getTime() + 5000;
					} finally {
						isServerChecking = false;
						lockServerCheck.unlock();
					}
				}
			}
		}

		return !isNeedRefresh;

	}

	/**
	 * 削除する
	 */
	public void clear () {

		try {
			lock.lock();

			lastGetAt = null;
			cacheInstance().remove(key);
		} finally {
			lock.unlock();
		}

	}

	/**
	 * ローダー
	 *
	 * @param <V>	値型
	 */
	public interface LoadingCacheLoader<V> {

		/**
		 * ロード
		 *
		 * @param db	DB
		 * @return	結果
		 * @throws Exception	例外
		 */
		V load(DB db) throws Exception;

	}

}
