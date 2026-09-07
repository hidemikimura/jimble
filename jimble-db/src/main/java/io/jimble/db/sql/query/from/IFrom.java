package io.jimble.db.sql.query.from;

import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.where.IWhere;

import java.util.List;

/**
 * from句
 */
public interface IFrom {

	/**
	 * inner join
	 *
	 * @param from	From
	 * @return	IFrom
	 */
	IFrom inner(IFrom from);

	/**
	 * left join
	 *
	 * @param from	From
	 * @return	IFrom
	 */
	IFrom left(IFrom from);

	/**
	 * on
	 *
	 * @param where	Where
	 * @return	IWhere
	 */
	IFrom on(IWhere...where);

	/**
	 * SQLを出力する
	 *
	 * @param sb	StringBuilder
	 */
	void fromSql (StringBuilder sb);

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

	/**
	 * テーブル一覧取得
	 *
	 * @return	テーブル一覧
	 */
	List<ITable> getTableList();

}
