package io.jimble.db.sql.definition.table;

import io.jimble.util.data.definition.ISchema;

import io.jimble.db.sql.definition.schema.EmptyScheme;

/**
 * 仮テーブル
 */
public class TemporaryTable extends Table {

	/**
	 * コンストラクタ
	 *
	 * @param name   テーブル名
	 */
	public TemporaryTable(String name) {

		super(new EmptyScheme(), name);

	}

}
