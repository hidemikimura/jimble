/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example.table.request_archive;

import db.approval_jobs_example.ApprovalJobsExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 申請（書庫）
 */
public class RequestArchive extends Table {

	/* id */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* staff_id */
	public static final Column staff_id = new Column(instance(), "staff_id", long.class, false, null, false);

	/* amount */
	public static final Column amount = new Column(instance(), "amount", long.class, false, null, false);

	/* needed_on */
	public static final Column needed_on = new Column(instance(), "needed_on", java.util.Date.class, false, null, false);

	/* status */
	public static final Column status = new Column(instance(), "status", java.lang.String.class, false, null, false);

	/* created_at */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);

	/* archived_at */
	public static final Column archived_at = new Column(instance(), "archived_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, staff_id, amount, needed_on, status, created_at, archived_at);

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

	public RequestArchive (ISchema schema, String name) { super(schema, name); }

	public static RequestArchive instance () { return new RequestArchive(new ApprovalJobsExample(), "request_archive"); }

}
