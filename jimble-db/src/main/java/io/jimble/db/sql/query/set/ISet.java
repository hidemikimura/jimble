package io.jimble.db.sql.query.set;

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
	 * @param sb	StringBuilder
	 */
	void setSql(StringBuilder sb);

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
