package io.jimble.db.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
/**
 * condition
 */
public interface ICondition {

	/**
	 * SQLを出力する
	 *
	 * @param sb	書き出し先
	 */
	void conditionSql (SqlWriter sb);

	/**
	 * パラメータ存在判定
	 *
	 * @return	存在する場合 = true
	 */
	boolean hasParameter();

	/**
	 * パラメータ取得
	 *
	 * @return	パラメータ
	 */
	Object getParameter();


	/**
	 * 演算子（要件 F-D-28）
	 *
	 * <p>
	 * <b>あとから条件を読むためにある。</b>
	 * 既定は {@code null}（読めない）。読めない条件は使われないだけで、
	 * <b>安全側（テーブルごと消す）に倒れる</b>。
	 * </p>
	 *
	 * @return	{@link io.jimble.db.sql.query.where.WhereTerm#EQ} / {@link io.jimble.db.sql.query.where.WhereTerm#IN}。読めなければ null
	 */
	default String operator () {

		return null;

	}

	/**
	 * 比べている相手（要件 F-D-28）
	 *
	 * <p>
	 * <b>{@code getParameter()} とは違う。</b>
	 * あちらは<b>バインドするもの</b>を返すので、
	 * 結合条件（{@code = shop.id}）のように<b>バインドしないもの</b>は返らない。
	 * ここは書いたものをそのまま返す。
	 * </p>
	 *
	 * @return	相手。読めなければ null
	 */
	default Object conditionValue () {

		return getParameter();

	}

}
