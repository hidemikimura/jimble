package io.jimble.db.sql.query.where.condition;

/**
 * condition
 */
public interface ICondition {

	/**
	 * SQLを出力する
	 *
	 * @param sb	StringBuilder
	 */
	void conditionSql(StringBuilder sb);

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
