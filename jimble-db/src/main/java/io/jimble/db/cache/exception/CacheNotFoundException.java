package io.jimble.db.cache.exception;

import io.jimble.db.cache.CacheData;

/**
 * キャッシュが存在しない
 */
public class CacheNotFoundException extends RuntimeException {

	/**
	 * コンストラクタ
	 *
	 * @param cacheData キャッシュデータ
	 */
	public CacheNotFoundException (CacheData cacheData) {

		super("cache not found: [key=%s][group=%s]".formatted(cacheData.cacheKey(), cacheData.groupKey()));

	}

}
