package io.jimble.db.internal.sql.query.set;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;

/**
 * set
 */
public interface ISet {

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
	 * set sql
	 *
	 * @param sb	書き出し先
	 */
	void setSql (SqlWriter sb);

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
