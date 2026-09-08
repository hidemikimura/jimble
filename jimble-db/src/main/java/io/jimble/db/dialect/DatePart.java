package io.jimble.db.dialect;

/**
 * 日時から取り出す部分（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code YEAR(x)} のような専用の関数、
 * PostgreSQL は {@code EXTRACT(YEAR FROM x)}。
 * <b>返る値まで揃える</b>ので、曜日のように起点が違うものはここで合わせる。
 * </p>
 */
public enum DatePart {

	/** 年 */
	YEAR,

	/** 月（1-12） */
	MONTH,

	/** 日（1-31） */
	DAY,

	/** 時（0-23） */
	HOUR,

	/** 分（0-59） */
	MINUTE,

	/** 秒（0-59） */
	SECOND,

	/** 四半期（1-4） */
	QUARTER,

	/**
	 * 曜日（1-7。日曜が 1）
	 *
	 * <p>
	 * <b>起点が違う。</b>MySQL の {@code DAYOFWEEK} は日曜が 1、
	 * PostgreSQL の {@code EXTRACT(DOW)} は日曜が 0。
	 * PostgreSQL 側で 1 を足して揃える。
	 * </p>
	 */
	DAY_OF_WEEK,

	/** 年の通算日（1-366） */
	DAY_OF_YEAR,

	/**
	 * 週（ISO-8601。1-53）
	 *
	 * <p>
	 * MySQL の {@code WEEK} は既定が ISO ではないので、
	 * ISO の {@code WEEKOFYEAR} を使う。
	 * </p>
	 */
	WEEK,

}
