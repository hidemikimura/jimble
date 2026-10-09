package io.jimble.db.sqlcache;

import io.jimble.util.data.Data;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * SQL 結果キャッシュの置き場（要件 F-D-28）
 *
 * <p>
 * <b>キー1つに、依存するタグが複数つく。</b>
 * 既存の {@code ICache} はキー1つにグループ1つ（要件 F-U-08）なので、
 * <b>「customer の1行と shop の1行の両方に依存している」を表せない</b>。
 * ここは別に持つ。
 * </p>
 */
public interface SqlCacheStore {

	/**
	 * 取り出す
	 *
	 * @param key	キー
	 * @return	値。無ければ null
	 * @throws Exception	取り出せなかった場合
	 */
	String get (String key) throws Exception;

	/**
	 * 入れる
	 *
	 * @param key	キー
	 * @param tags	依存するタグ
	 * @param value	値
	 * @param ttl	期限。0 なら無期限
	 * @throws Exception	入れられなかった場合
	 */
	void put (String key, Set<String> tags, String value, Duration ttl) throws Exception;

	/**
	 * 結果を取り出す
	 *
	 * <p>
	 * 既定は {@link #get(String)} の文字列を読み戻す。<b>返すのは呼んだ側だけのもの</b>で、
	 * 書き換えても置き場の中身は変わらない。
	 * </p>
	 *
	 * @param key	キー
	 * @return	結果。無ければ null
	 * @throws Exception	取り出せなかった場合
	 */
	default List<Data> getRows (String key) throws Exception {

		String value = get(key);

		return value == null ? null : SqlCache.deserialize(value);

	}

	/**
	 * 結果を入れる
	 *
	 * <p>
	 * 既定は書き出した文字列を {@link #put(String, Set, String, Duration)} に渡す。
	 * <b>入れたあとで呼んだ側が {@code rows} を書き換えても、置き場の中身は変わらない</b>こと。
	 * </p>
	 *
	 * @param key	キー
	 * @param tags	依存するタグ
	 * @param rows	結果
	 * @param ttl	期限。0 なら無期限
	 * @throws Exception	入れられなかった場合
	 */
	default void putRows (String key, Set<String> tags, List<Data> rows, Duration ttl) throws Exception {

		put(key, tags, SqlCache.serialize(rows), ttl);

	}

	/**
	 * タグのついたものを消す
	 *
	 * @param tags	タグ
	 * @throws Exception	消せなかった場合
	 */
	void invalidate (Set<String> tags) throws Exception;

	/**
	 * 全部消す
	 *
	 * <p>
	 * 生 SQL の更新（{@code db.execute("UPDATE ...")}）のように
	 * <b>どこに当たるか読めない</b>ときに呼ぶ。
	 * </p>
	 *
	 * @throws Exception	消せなかった場合
	 */
	void clear () throws Exception;

}
