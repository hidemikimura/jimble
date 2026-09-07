package io.jimble.db.cache;

import java.util.List;

/**
 * キャッシュ
 */
public interface ICache {

	/**
	 * 取得
	 *
	 * @param key   キー
	 * @return  文字列
	 */
	String getString(String key);

	/**
	 * 取得
	 *
	 * @param key   キー
	 * @param group グループ
	 * @return  文字列
	 */
	String getString(String key, String group);

	/**
	 * グループ内の値一覧取得
	 *
	 * @param group グループ
	 * @return  一覧
	 */
	List<String> getStringGroup(String group);

	/**
	 * 取得
	 *
	 * @param key   キー
	 * @return  キャッシュデータ
	 */
	CacheData get(String key);

	/**
	 * 取得
	 *
	 * @param key   キー
	 * @param group グループキー
	 * @return  キャッシュデータ
	 */
	CacheData get(String key, String group);

	/**
	 * グループ内のキャッシュデータ一覧取得
	 *
	 * @param group グループ
	 * @return  キャッシュデータ一覧取得
	 */
	List<CacheData> getGroup(String group);

	/**
	 * 設定
	 *
	 * @param key           キー
	 * @param value         値
	 * @param contentType   コンテンツタイプ
	 * @return  正常に終了した場合 = true
	 */
	boolean set(String key, String value, String contentType);

	/**
	 * 設定
	 *
	 * @param key           キー
	 * @param value         値
	 * @param contentType   コンテンツタイプ
	 * @param group         グループキー
	 * @return  正常に終了した場合 = true
	 */
	boolean set(String key, String value, String contentType, String group);

	/**
	 * 削除
	 *
	 * @param key   キー
	 */
	void remove(String key);

	/**
	 * 削除
	 *
	 * @param group     グループキー
	 */
	void removeGroup(String group);

	/**
	 * 存在判定
	 *
	 * @param key   キー
	 * @param group グループキー
	 * @return  存在する場合 = true
	 */
	boolean has(String key, String group);

}
