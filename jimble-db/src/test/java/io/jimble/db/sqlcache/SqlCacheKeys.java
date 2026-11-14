package io.jimble.db.sqlcache;

import io.jimble.util.hash.Hash;

import java.util.HexFormat;
import java.util.List;

/**
 * テスト用：2.7.19 までのキーの形（SipHash-2-4 + 長さ）で、今のキーのもと（{@link SqlCache#keySource}）を表す
 *
 * <p>
 * 3.0.0 でキーのハッシュを XXH3-128 に替えた。<b>ハッシュにかける中身は 2.7.4 から変えていない</b>ことを、
 * 2.7.4 の公開版で書き出した答え（{@code golden/sql-2.7.4.txt} のキー）と比べて確かめるために使う。
 * </p>
 */
public final class SqlCacheKeys {

	private static final HexFormat HEX = HexFormat.of();

	private SqlCacheKeys () {
	}

	/**
	 * 2.7.19 までの形のキー
	 *
	 * @param dataSource	データソースの名前
	 * @param sql			SQL
	 * @param params		バインドパラメータ
	 * @return	キー
	 */
	public static String legacy (String dataSource, String sql, List<Object> params) {

		byte[] bytes = SqlCache.keySource(dataSource, sql, params);
		return HEX.toHexDigits(Hash.sipHashOf(bytes)) + HEX.toHexDigits(bytes.length);

	}

	/**
	 * 2.7.19 までの形のキー（データソースなし）
	 *
	 * @param sql		SQL
	 * @param params	バインドパラメータ
	 * @return	キー
	 */
	public static String legacy (String sql, List<Object> params) {

		return legacy("", sql, params);

	}

}
