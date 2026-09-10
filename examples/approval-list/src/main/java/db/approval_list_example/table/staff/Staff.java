/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_list_example.table.staff;

import db.approval_list_example.ApprovalListExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 社員
 */
public class Staff extends Table {

	/* id */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* department_id */
	public static final Column department_id = new Column(instance(), "department_id", long.class, false, null, false);

	/* name */
	public static final Column name = new Column(instance(), "name", java.lang.String.class, false, null, false);

	/* created_at */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, department_id, name, created_at);

	/* 一意キー（生成時に確定。要件 F-D-28） */
	private static final List<List<Column>> UNIQUE_KEYS = List.of();

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

	public Staff (ISchema schema, String name) { super(schema, name); }

	public static Staff instance () { return new Staff(new ApprovalListExample(), "staff"); }

}
