package io.jimble.db.sql.query.dsl;

/**
 * DSL
 */
public interface IDsl {

	/**
	 * DSL SQL
	 *
	 * @param sb	StringBuilder
	 */
	void dslSql(StringBuilder sb);

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
