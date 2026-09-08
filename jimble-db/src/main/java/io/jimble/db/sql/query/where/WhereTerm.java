package io.jimble.db.sql.query.where;

import io.jimble.util.data.definition.IColumn;

/**
 * WHERE の条件1つ（要件 F-D-28）
 *
 * <p>
 * <b>組み立てた WHERE を、あとから読むためにある。</b>
 * SQL 結果のキャッシュが「この更新はどの行に当たるか」を知るのに要る。
 * </p>
 *
 * <p>
 * {@code value} は<b>リテラルとは限らない</b>。
 * {@code ON (customer.shop_id = shop.id)} のような結合条件では
 * {@link IColumn} が入る。
 * </p>
 *
 * @param column	列
 * @param operator	演算子（{@code "="} / {@code "in"}）
 * @param value		値
 */
public record WhereTerm(IColumn column, String operator, Object value) {

	/** 等値 */
	public static final String EQ = "=";

	/** IN */
	public static final String IN = "in";

	/**
	 * 等値か
	 *
	 * @return	等値なら true
	 */
	public boolean isEq () {

		return EQ.equals(operator);

	}

	/**
	 * IN か
	 *
	 * @return	IN なら true
	 */
	public boolean isIn () {

		return IN.equals(operator);

	}

}
