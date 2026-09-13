/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_example.table.rate;

import db.approval_data_example.ApprovalDataExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * レート
 */
public class Rate extends Table {

	/* このテーブルの実体（1つだけ作る） */
	private static final Rate INSTANCE = new Rate(new ApprovalDataExample(), "rate");

	/* id */
	public static final Column id = new Column(INSTANCE, "id", long.class, false, null, true);

	/* code */
	public static final Column code = new Column(INSTANCE, "code", java.lang.String.class, false, null, false);

	/* value */
	public static final Column value = new Column(INSTANCE, "value", long.class, false, null, false);

	/* updated_at */
	public static final Column updated_at = new Column(INSTANCE, "updated_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, code, value, updated_at);

	/* 一意キー（生成時に確定。要件 F-D-28） */
	private static final List<List<Column>> UNIQUE_KEYS = List.of(List.of(code));

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<Column> declareColumns () { return COLUMNS; }

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<List<Column>> declareUniqueKeys () { return UNIQUE_KEYS; }

	public static List<Column> columns () { return COLUMNS; }

	public static List<List<Column>> uniqueKeys () { return UNIQUE_KEYS; }

	public Rate (ISchema schema, String name) { super(schema, name); }

	public static Rate instance () { return INSTANCE; }

}
