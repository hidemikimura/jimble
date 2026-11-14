package io.jimble.db.internal.sql.select;

import io.jimble.util.conf.ConfFlag;

/**
 * SELECT 句の列に {@code AS `表__列`} を付けるか（{@value #KEY}。3.0.0）
 *
 * <p>
 * 3.0.0 から既定は付けない（false）。結果は、ビルダーが書いた位置ごとのキーで表名の入れ子にする。
 * true にすると 2.x と同じ SQL になる（{@code builder.sql()} の文字列を自分で投げ、列名で入れ子にしているアプリ向け）。
 * </p>
 */
public final class SelectAlias {

	/** 設定のキー */
	public static final String KEY = "sql.select_alias";

	/* SQL を組み立てるたびに見るので、読み込んだ設定 1 つにつき 1 回だけ読む */
	private static final ConfFlag FLAG = ConfFlag.of(KEY, false);

	private SelectAlias () {}

	/**
	 * 付けるか
	 *
	 * @return	付けるなら true
	 */
	public static boolean enabled () {

		return FLAG.get();

	}

}
