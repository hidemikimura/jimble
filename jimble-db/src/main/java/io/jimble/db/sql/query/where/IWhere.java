package io.jimble.db.sql.query.where;

/**
 * where
 */
public interface IWhere {

	/**
	 * and
	 *
	 * @param where	IWhere
	 * @return	IWhere
	 */
	IWhere and(IWhere where);

	/**
	 * or
	 *
	 * @param where	IWhere
	 * @return	IWhere
	 */
	IWhere or(IWhere where);

	/**
	 * =
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere eq(Object value);

	/**
	 * {@code <>}
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere not(Object value);

	/**
	 * >
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere gt(Object value);

	/**
	 * {@code <}
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere lt(Object value);

	/**
	 * >=
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere ge(Object value);

	/**
	 * {@code <=}
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere le(Object value);

	/**
	 * is null
	 *
	 * @return	IWhere
	 */
	IWhere is_null();

	/**
	 * is not null
	 *
	 * @return	IWhere
	 */
	IWhere is_not_null();

	/**
	 * between
	 *
	 * @param value1	値1
	 * @param value2	値2
	 * @return	IWhere
	 */
	IWhere between(Object value1, Object value2);

	/**
	 * like
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere like(Object value);

	/**
	 * not like
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere not_like(Object value);

	/**
	 * like(%?%)
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere contains(Object value);

	/**
	 * like(?%)
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere starts_with(Object value);

	/**
	 * like(%?)
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere ends_with(Object value);

	/**
	 * in
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere in(Object value);

	/**
	 * not in
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere not_in(Object value);

	/**
	 * exists
	 *
	 * @param value	値
	 * @return	IWhere
	 */
	IWhere exists(Object value);

	/**
	 * 結合演算子
	 *
	 * @return	結合演算子
	 */
	String logicalOperator();

	/**
	 * SQLを出力する
	 *
	 * @param sb	StringBuilder
	 */
	void whereSql (StringBuilder sb);

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
