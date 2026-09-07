package io.jimble.db.sql;

import java.util.List;

/**
 * builder
 */
public interface IBuilder {

	/**
	 * SQLを作成する
	 *
	 * @return	SQL
	 */
	public String sql();

	/**
	 * パラメータを取得する
	 *
	 * @return	パラメータ
	 */
	public List<Object> params();

}
