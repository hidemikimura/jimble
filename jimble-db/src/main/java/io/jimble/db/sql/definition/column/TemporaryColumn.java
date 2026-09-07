package io.jimble.db.sql.definition.column;

import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.definition.table.TemporaryTable;

/**
 * 仮列
 */
public class TemporaryColumn extends Column {

	/**
	 * コンストラクタ
	 *
	 * @param table     テーブル名
	 * @param column    列名
	 */
	public TemporaryColumn(String table, String column) {

		super(new TemporaryTable(table), column, Object.class);

	}

	/**
	 * コンストラクタ
	 *
	 * @param table テーブル
	 * @param name  列名
	 */
	public TemporaryColumn(ITable table, String name) {

		super(table, name, Object.class);

	}

}
