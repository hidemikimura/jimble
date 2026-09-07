package io.jimble.db.cache;

import io.jimble.db.DB;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ローディングキャッシュ
 *
 * @param <V>   値型
 */
public class LoadingCacheMulti<K, V> {

	/* ロック */
	private final ReentrantLock lock = new ReentrantLock();

	/* ロック */
	private final ReentrantLock lockServerCheck = new ReentrantLock();

	/* キー */
	private final String key;

	/* DB */
	private final DB db;

	/* ローダー */
	private final LoadingCacheMultiLoader<K, V> loader;

	/* 期限 */
	private Duration duration = null;

	/* キャッシュデータ */
	private final Map<K, V> cache = new HashMap<>();

	/* 最終取得日時 */
	private final Map<K, Date> lastGetAt = new HashMap<>();

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
	public LoadingCacheMulti(String key, LoadingCacheMultiLoader<K, V> loader, DB db, Duration duration) {
		this.key = "loading_cache_" + key + "_";
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
	 * @param k	キー
	 * @return  値
	 */
	public V get (K k) throws Exception {

		V result = cache.get(k);
		if (isUseCache(k, true)) {
			return result;
		}

		try {
			lock.lock();

			if (isUseCache(k, false)) {
				return cache.get(k);
			}

			isLoading = true;

			result = loader.load(k, db());
			cache.put(k, result);
			cacheInstance().set(this.key + k, "1", "application/text");
			lastGetAt.put(k, new Date());
		} finally {
			isLoading = false;
			lock.unlock();
		}

		return result;

	}

	/* 前回サーバーチェック時刻 */
	private final Map<K, Date> lastCheckServerAt = new HashMap<>();

	/* サーバーチェック中 */
	private boolean isServerChecking = false;

	/**
	 * キャッシュ利用判定
	 *
	 * @param k				キー
	 * @param isCheckServer 要サーバーチェック
	 * @return  キャッシュを利用する場合 = true
	 */
	private boolean isUseCache (K k, boolean isCheckServer) {

		// ローディング中でキャッシュが存在する場合、キャッシュを利用する
		if (isLoading && cache.containsKey(k)) {
			return true;
		}

		// 要リフレッシュ判定
		boolean isNeedRefresh = true;

		Date _lastGetAt = lastGetAt.get(k);
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
			Date _lastCheckServerAt = this.lastCheckServerAt.get(k);
			if (_lastCheckServerAt == null || System.currentTimeMillis() > _lastCheckServerAt.getTime() + 60000) {
				if (!isServerChecking) {
					try {
						lockServerCheck.lock();

						if (isServerChecking
							|| (_lastCheckServerAt != null && System.currentTimeMillis() <= _lastCheckServerAt.getTime() + 60000)) {
							return true;
						}

						isServerChecking = true;

						this.lastCheckServerAt.put(k, new Date());
						CacheData cacheData = cacheInstance().get(this.key + k);
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
	 *
	 * @param k	キー
	 */
	public void clear (K k) {

		try {
			lock.lock();

			lastGetAt.remove(k);
			cacheInstance().remove(this.key + k);
		} finally {
			lock.unlock();
		}

	}

	/**
	 * ローダー
	 *
	 * @param <V>	値型
	 */
	public interface LoadingCacheMultiLoader<K, V> {

		/**
		 * ロード
		 *
		 * @param k		キー
		 * @param db	DB
		 * @return	値
		 * @throws Exception	例外
		 */
		V load(K k, DB db) throws Exception;

	}

}
