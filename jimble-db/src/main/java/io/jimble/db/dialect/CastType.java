package io.jimble.db.dialect;

/**
 * 型変換の行き先（要件 F-D-31）
 *
 * <p>
 * <b>型名が製品でまるで違う。</b>
 * MySQL は {@code CHAR} / {@code SIGNED} / {@code DECIMAL}、
 * PostgreSQL は {@code text} / {@code integer} / {@code numeric}。
 * ビルダーはここの名前で書き、実際の型名は {@link Dialect} が決める。
 * </p>
 */
public enum CastType {

	/** 文字列（MySQL {@code CHAR} / PostgreSQL {@code text}） */
	STRING,

	/** 整数（MySQL {@code SIGNED} / PostgreSQL {@code integer}） */
	INT,

	/** 整数（MySQL {@code SIGNED} / PostgreSQL {@code bigint}） */
	BIGINT,

	/** 小数（桁を指定する。MySQL {@code DECIMAL(p,s)} / PostgreSQL {@code numeric(p,s)}） */
	DECIMAL,

	/** 日付 */
	DATE,

	/** 日時（MySQL {@code DATETIME} / PostgreSQL {@code timestamp}） */
	DATETIME,

	/**
	 * 真偽値
	 *
	 * <p>
	 * <b>MySQL に boolean 型は無い。</b>{@code SIGNED}（0/1）になる。
	 * PostgreSQL は本物の {@code boolean}。
	 * </p>
	 */
	BOOLEAN,

	/** JSON（MySQL {@code JSON} / PostgreSQL {@code jsonb}） */
	JSON,

}
