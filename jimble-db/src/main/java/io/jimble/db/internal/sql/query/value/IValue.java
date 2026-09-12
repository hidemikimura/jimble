package io.jimble.db.internal.sql.query.value;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;

/**
 * value
 */
public interface IValue {

	/**
	 * column
	 *
	 * @return	column
	 */
	IColumn column();

	/**
	 * value
	 *
	 * @return	value
	 */
	Object value();

	/**
	 * column sql
	 *
	 * @param sb	書き出し先
	 */
	void columnSql (SqlWriter sb);

	/**
	 * value sql
	 *
	 * @param sb	書き出し先
	 */
	void valueSql (SqlWriter sb);

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
