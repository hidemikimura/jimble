package io.jimble.db.sql.query.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

/**
 * select句
 */
public interface ISelect {

	/**
	 * 別名を設定する
	 *
	 * @param as	別名
	 * @return	ISelect
	 */
	ISelect as(String as);

	/**
	 * DSLを設定する
	 *
	 * @param dsl	DSL
	 * @return	ISelect
	 */
	ISelect dsl(IDsl dsl);

	/**
	 * プラス
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	ISelect plus(Object value);

	/**
	 * マイナス
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	ISelect minus(Object value);

	/**
	 * 掛け算
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	ISelect multiply(Object value);

	/**
	 * 割り算
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	ISelect subtract(Object value);

	/**
	 * SQLを出力する
	 *
	 * @param sb	書き出し先
	 */
	void selectSql (SqlWriter sb);

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

	// region 比べる（要件 F-D-09 / D-135）

	/*
	 * <b>式を左辺にした条件を作る。</b>
	 *
	 * これがあると {@code Dsl.sum(Request.amount).ge(500_000L)} と書けて、
	 * <b>列のとき（{@code Request.amount.ge(...)}）と同じ形</b>になる。
	 *
	 * <b>無かったころは HAVING が書けなかった。</b>集計を左辺にする道が無く、
	 * 無理に組むと <b>{@code HAVING ( >= ?)} という壊れた SQL が
	 * 例外も警告も無しに組み上がっていた</b>。
	 *
	 * <b>比較だけにしてある。</b>{@code like} や {@code contains} も足せるが、
	 * <b>集計に対しては、書けるけれど意味の無い組み合わせが増えるだけ</b>である。
	 *
	 * {@link io.jimble.db.sql.definition.column.Column} と
	 * {@link io.jimble.db.sql.query.dsl.AbstractDsl} は
	 * これらを自分で持っているので、ここの既定は使われない。
	 */

	/**
	 * 等しい
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere eq (Object value) {

		return WhereQuery.ofExpression(this).eq(value);

	}

	/**
	 * 等しくない
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere not (Object value) {

		return WhereQuery.ofExpression(this).not(value);

	}

	/**
	 * より大きい
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere gt (Object value) {

		return WhereQuery.ofExpression(this).gt(value);

	}

	/**
	 * より小さい
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere lt (Object value) {

		return WhereQuery.ofExpression(this).lt(value);

	}

	/**
	 * 以上
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere ge (Object value) {

		return WhereQuery.ofExpression(this).ge(value);

	}

	/**
	 * 以下
	 *
	 * @param value	値
	 * @return	条件
	 */
	default IWhere le (Object value) {

		return WhereQuery.ofExpression(this).le(value);

	}

	/**
	 * 範囲の中
	 *
	 * @param value1	下限
	 * @param value2	上限
	 * @return	条件
	 */
	default IWhere between (Object value1, Object value2) {

		return WhereQuery.ofExpression(this).between(value1, value2);

	}

	/**
	 * null である
	 *
	 * @return	条件
	 */
	default IWhere is_null () {

		return WhereQuery.ofExpression(this).is_null();

	}

	/**
	 * null でない
	 *
	 * @return	条件
	 */
	default IWhere is_not_null () {

		return WhereQuery.ofExpression(this).is_not_null();

	}

	// endregion

}
