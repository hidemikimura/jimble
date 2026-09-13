/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_example.table.request;

import db.approval_data_example.ApprovalDataExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 申請
 */
public class Request extends Table {

	/* id */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* staff_id */
	public static final Column staff_id = new Column(instance(), "staff_id", long.class, false, null, false);

	/* amount */
	public static final Column amount = new Column(instance(), "amount", long.class, false, null, false);

	/* status */
	public static final Column status = new Column(instance(), "status", java.lang.String.class, false, null, false);

	/* decided_by */
	public static final Column decided_by = new Column(instance(), "decided_by", long.class, true, null, false);

	/* decided_at */
	public static final Column decided_at = new Column(instance(), "decided_at", java.util.Date.class, true, null, false);

	/* created_at */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, staff_id, amount, status, decided_by, decided_at, created_at);

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

	public Request (ISchema schema, String name) { super(schema, name); }

	public static Request instance () { return new Request(new ApprovalDataExample(), "request"); }

}
