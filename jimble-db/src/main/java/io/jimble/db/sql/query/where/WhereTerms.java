package io.jimble.db.sql.query.where;

import io.jimble.util.data.definition.IColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * WHERE から読み取れた条件（要件 F-D-28）
 *
 * <p>
 * <b>読めたものだけを集める。</b>読めなかった条件は黙って落とす。
 * ここに集まるのは「AND で繋がった、列と演算子と値が分かる条件」だけである。
 * </p>
 *
 * <h2>OR が混ざったら、その括弧の中は使わない</h2>
 * <p>
 * {@code a = 1 OR b = 2} は<b>「a = 1 の行」に絞り込めていない</b>。
 * ここから {@code a = 1} だけを取り出して「この更新は a = 1 の行だけに当たる」と
 * 判断すると、<b>b = 2 の行のキャッシュが古いまま残る</b>。
 * </p>
 *
 * <p>
 * だから OR を見つけたら<b>その括弧の中身をまるごと捨てる</b>。
 * {@code a = 1 AND (b = 2 OR c = 3)} なら、外側の {@code a = 1} は生きる
 * （AND は絞り込みを緩めないため）。
 * </p>
 */
public final class WhereTerms {

	/* 読めた条件 */
	private final List<WhereTerm> terms = new ArrayList<>();

	/* OR が混ざっているか */
	private boolean hasOr = false;

	/**
	 * 条件を足す
	 *
	 * @param column	列
	 * @param operator	演算子
	 * @param value		値
	 */
	public void add (IColumn column, String operator, Object value) {

		if (column == null || operator == null) {
			return;
		}

		terms.add(new WhereTerm(column, operator, value));

	}

	/**
	 * 条件をまとめて足す
	 *
	 * @param values	条件
	 */
	public void addAll (List<WhereTerm> values) {

		terms.addAll(values);

	}

	/**
	 * OR が混ざっていた印をつける
	 */
	public void markOr () {

		this.hasOr = true;

	}

	/**
	 * OR が混ざっているか
	 *
	 * @return	混ざっていれば true
	 */
	public boolean hasOr () {

		return hasOr;

	}

	/**
	 * 読めた条件
	 *
	 * @return	条件
	 */
	public List<WhereTerm> terms () {

		return List.copyOf(terms);

	}

	/**
	 * 1つも読めていないか
	 *
	 * @return	空なら true
	 */
	public boolean isEmpty () {

		return terms.isEmpty();

	}

}
