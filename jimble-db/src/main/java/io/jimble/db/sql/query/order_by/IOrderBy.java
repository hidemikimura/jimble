package io.jimble.db.sql.query.order_by;

import io.jimble.db.dialect.SqlWriter;
/**
 * order by
 */
public interface IOrderBy {

	/**
	 * asc
	 *
	 * @return	IOrderBy
	 */
	IOrderBy asc();

	/**
	 * desc
	 *
	 * @return	IOrderBy
	 */
	IOrderBy desc();

	/**
	 * SQLを出力する
	 *
	 * @param sb	書き出し先
	 */
	void orderBySql (SqlWriter sb);

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
