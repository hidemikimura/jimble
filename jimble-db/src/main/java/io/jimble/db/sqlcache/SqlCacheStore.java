package io.jimble.db.sqlcache;

import java.time.Duration;
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
