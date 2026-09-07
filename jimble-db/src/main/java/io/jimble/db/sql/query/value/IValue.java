package io.jimble.db.sql.query.value;

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
	 * @param sb	StringBuilder
	 */
	void columnSql(StringBuilder sb);

	/**
	 * value sql
	 *
	 * @param sb	StringBuilder
	 */
	void valueSql(StringBuilder sb);

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
