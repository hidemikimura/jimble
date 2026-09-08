package io.jimble.db.dialect;

/**
 * 日時の単位（要件 F-D-31）
 *
 * <p>
 * <b>文字列で受け取らない。</b>SQL にそのまま埋め込むところなので、
 * 外から来た文字列を通すと SQL を書き換えられる。
 * 受け付ける単位を<b>両製品にあるものだけ</b>に絞る意味もある
 * （MySQL の {@code QUARTER} は PostgreSQL の {@code INTERVAL} には無い）。
 * </p>
 */
public enum DateUnit {

	/** 秒 */
	SECOND,

	/** 分 */
	MINUTE,

	/** 時 */
	HOUR,

	/** 日 */
	DAY,

	/** 週 */
	WEEK,

	/** 月 */
	MONTH,

	/** 年 */
	YEAR,

}
