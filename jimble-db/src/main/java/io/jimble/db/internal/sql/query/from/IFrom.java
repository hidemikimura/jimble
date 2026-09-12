package io.jimble.db.internal.sql.query.from;

import io.jimble.db.dialect.SqlWriter;
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
	 * @param sb	書き出し先
	 */
	void fromSql (SqlWriter sb);

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


	/**
	 * 結合条件を読み取る（要件 F-D-28）
	 *
	 * <p>
	 * {@code ON (customer.shop_id = shop.id)} を読む。
	 * SQL 結果のキャッシュが「結合先の行が1つに決まるか」を判断するのに使う。
	 * </p>
	 *
	 * @param terms	読み取り先
	 */
	default void onTerms (io.jimble.db.sql.query.where.WhereTerms terms) {

	}

}
