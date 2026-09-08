package io.jimble.db.sql.query.dsl.select;

/**
 * ウィンドウの範囲（要件 F-D-31）
 *
 * <p>
 * {@code ROWS BETWEEN ... AND ...} の端。
 * MySQL 8 と PostgreSQL で書き方は同じ。
 * </p>
 *
 * <pre>
 * // 累計
 * Dsl.over(Dsl.sum(Sale.amount))
 *     .orderBy(Sale.sold_at.asc())
 *     .rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
 * </pre>
 */
public final class WindowFrame {

	/* SQL */
	private final String sql;

	/**
	 * コンストラクタ
	 *
	 * @param sql	SQL
	 */
	private WindowFrame (String sql) {

		this.sql = sql;

	}

	/**
	 * いちばん前から
	 *
	 * @return	範囲の端
	 */
	public static WindowFrame unboundedPreceding () {

		return new WindowFrame("UNBOUNDED PRECEDING");

	}

	/**
	 * いちばん後ろまで
	 *
	 * @return	範囲の端
	 */
	public static WindowFrame unboundedFollowing () {

		return new WindowFrame("UNBOUNDED FOLLOWING");

	}

	/**
	 * いまの行
	 *
	 * @return	範囲の端
	 */
	public static WindowFrame currentRow () {

		return new WindowFrame("CURRENT ROW");

	}

	/**
	 * n 行前
	 *
	 * @param rows	行数
	 * @return	範囲の端
	 */
	public static WindowFrame preceding (int rows) {

		return new WindowFrame(rows + " PRECEDING");

	}

	/**
	 * n 行後
	 *
	 * @param rows	行数
	 * @return	範囲の端
	 */
	public static WindowFrame following (int rows) {

		return new WindowFrame(rows + " FOLLOWING");

	}

	/**
	 * SQL
	 *
	 * @return	SQL
	 */
	public String sql () {

		return sql;

	}

}
