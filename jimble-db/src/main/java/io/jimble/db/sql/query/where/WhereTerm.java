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
 * @param operator	演算子（{@code "="} / {@code "in"} / {@code "not in"}）
 * @param value		値
 */
public record WhereTerm(IColumn column, String operator, Object value) {

	/** 等値 */
	public static final String EQ = "=";

	/** IN */
	public static final String IN = "in";

	/**
	 * NOT IN
	 *
	 * <p>
	 * <b>{@link #IN} と同じ扱いにしてはいけない。</b>
	 * {@code IN (1, 2)} は「1 か 2 の行」だが、
	 * {@code NOT IN (1, 2)} は<b>それ以外の全部</b>である。
	 * 1 と 2 で絞れているつもりでキャッシュに印を付けると、
	 * <b>本当に当たっている行のキャッシュが消えずに残る</b>（古い値が返る）。
	 * </p>
	 * <p>
	 * だから {@link #isIn()} はここで true にならない。
	 * 読めることに意味があるだけで、
	 * <b>絞り込みには使わない（安全側＝テーブルごと消す）</b>。
	 * </p>
	 */
	public static final String NOT_IN = "not in";

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
	 * <p><b>{@code NOT IN} はここに含めない</b>（{@link #NOT_IN} を見ること）。</p>
	 *
	 * @return	IN なら true
	 */
	public boolean isIn () {

		return IN.equals(operator);

	}

	/**
	 * NOT IN か
	 *
	 * @return	NOT IN なら true
	 */
	public boolean isNotIn () {

		return NOT_IN.equals(operator);

	}

}
