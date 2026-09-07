package io.jimble.db.sql.query.select;

import io.jimble.db.sql.query.dsl.IDsl;

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
	 * @param sb	StringBuilder
	 */
	void selectSql (StringBuilder sb);

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

}
