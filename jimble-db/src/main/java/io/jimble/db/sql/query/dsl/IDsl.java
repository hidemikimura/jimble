package io.jimble.db.sql.query.dsl;

import io.jimble.db.dialect.SqlWriter;
/**
 * DSL
 */
public interface IDsl {

	/**
	 * DSL SQL
	 *
	 * @param sb	書き出し先
	 */
	void dslSql (SqlWriter sb);

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
