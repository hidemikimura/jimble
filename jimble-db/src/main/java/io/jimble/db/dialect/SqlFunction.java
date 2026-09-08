package io.jimble.db.dialect;

/**
 * SQL の関数（要件 F-D-30）
 *
 * <p>
 * <b>製品によって名前が違うものを、ここで1つの名前にまとめる。</b>
 * ビルダーはこの名前で書き、実際の名前は {@link Dialect} が決める。
 * </p>
 *
 * <p>
 * 名前が同じものもここに並べてある。<b>「同じだと確かめた」ことを残す</b>ためで、
 * 並んでいないものはビルダーが直接書いているという意味になる。
 * </p>
 */
public enum SqlFunction {

	/** 現在日時 */
	NOW,

	/** 乱数（MySQL {@code RAND} / PostgreSQL {@code RANDOM}） */
	RAND,

	/** 件数 */
	COUNT,

	/** 合計 */
	SUM,

	/** 最小 */
	MIN,

	/** 最大 */
	MAX,

	/** 平均 */
	AVG,

	/** 切り上げ */
	CEILING,

	/** 切り捨て（小数点以下） */
	FLOOR,

	/** 四捨五入 */
	ROUND,

	/** 桁を落とす（MySQL {@code TRUNCATE} / PostgreSQL {@code TRUNC}） */
	TRUNCATE,

	/**
	 * 連結
	 *
	 * <p>
	 * <b>直接使わないこと。</b>MySQL と PostgreSQL で NULL の扱いが逆なので、
	 * {@code Dialect#concat} を通す（要件 F-D-30）。
	 * </p>
	 */
	CONCAT,

	/** null なら別の値（MySQL {@code IFNULL} / PostgreSQL {@code COALESCE}） */
	IFNULL,

	/**
	 * 日時の書式化
	 *
	 * <p>
	 * <b>直接使わないこと。</b>{@code to_char} は書式の言語が違うので、
	 * {@code Dialect#dateFormat} を通す（要件 F-D-30）。
	 * </p>
	 */
	DATE_FORMAT,

	/** 座標の生成 */
	ST_GEOM_FROM_TEXT,

	/** 球面距離（MySQL {@code ST_Distance_Sphere} / PostGIS {@code ST_DistanceSphere}） */
	ST_DISTANCE_SPHERE,

	/** 内包判定 */
	ST_WITHIN,

	// region 文字列（要件 F-D-31）

	/** 小文字にする */
	LOWER,

	/** 大文字にする */
	UPPER,

	/** 前後の空白を落とす */
	TRIM,

	/** 前の空白を落とす */
	LTRIM,

	/** 後ろの空白を落とす */
	RTRIM,

	/**
	 * 文字数
	 *
	 * <p>
	 * <b>MySQL の {@code LENGTH} はバイト数</b>で、文字数は {@code CHAR_LENGTH}。
	 * PostgreSQL の {@code LENGTH} は文字数。名前が同じで意味が違うので、
	 * ここでは<b>文字数</b>に揃える。
	 * </p>
	 */
	CHAR_LENGTH,

	/** バイト数（MySQL {@code LENGTH} / PostgreSQL {@code OCTET_LENGTH}） */
	BYTE_LENGTH,

	/** 部分文字列 */
	SUBSTRING,

	/** 置き換え */
	REPLACE,

	/** 左から n 文字 */
	LEFT,

	/** 右から n 文字 */
	RIGHT,

	/** 左を埋める */
	LPAD,

	/** 右を埋める */
	RPAD,

	/** 逆順にする */
	REVERSE,

	/** 繰り返す */
	REPEAT,

	/** 区切り文字を挟んで連結する */
	CONCAT_WS,

	/** MD5 */
	MD5,

	// endregion

	// region 数値（要件 F-D-31）

	/** 絶対値 */
	ABS,

	/** 剰余 */
	MOD,

	/** べき乗 */
	POWER,

	/** 平方根 */
	SQRT,

	/** 符号 */
	SIGN,

	/** 指数 */
	EXP,

	/** 自然対数 */
	LN,

	/** 常用対数 */
	LOG10,

	/**
	 * いちばん大きい値
	 *
	 * <p>
	 * <b>NULL の扱いが製品で違う。</b>MySQL は1つでも NULL なら NULL、
	 * PostgreSQL は NULL を無視する。吸収していないので、
	 * NULL が入りうる列に使うときは {@code ifnull} で埋めてから渡すこと。
	 * </p>
	 */
	GREATEST,

	/** いちばん小さい値（NULL の扱いは {@link #GREATEST} と同じ） */
	LEAST,

	// endregion

	// region 集約（要件 F-D-31）

	/** 標本標準偏差 */
	STDDEV,

	/** 標本分散 */
	VARIANCE,

	// endregion

	// region ウィンドウ関数（要件 F-D-31）

	/** 通し番号 */
	ROW_NUMBER,

	/** 順位（同順位のあとは飛ぶ） */
	RANK,

	/** 順位（同順位のあとも飛ばない） */
	DENSE_RANK,

	/** n 等分したときの何番目か */
	NTILE,

	/** 前の行の値 */
	LAG,

	/** 次の行の値 */
	LEAD,

	/** 範囲の最初の値 */
	FIRST_VALUE,

	/** 範囲の最後の値 */
	LAST_VALUE,

	// endregion

}
