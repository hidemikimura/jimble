/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example.table.notice;

import db.approval_jobs_example.ApprovalJobsExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 通知
 */
public class Notice extends Table {

	/* id */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* request_id */
	public static final Column request_id = new Column(instance(), "request_id", long.class, false, null, false);

	/* to_staff_id */
	public static final Column to_staff_id = new Column(instance(), "to_staff_id", long.class, false, null, false);

	/* kind */
	public static final Column kind = new Column(instance(), "kind", java.lang.String.class, false, null, false);

	/* sent_at */
	public static final Column sent_at = new Column(instance(), "sent_at", java.util.Date.class, true, null, false);

	/* created_at */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, request_id, to_staff_id, kind, sent_at, created_at);

	/* 一意キー（生成時に確定。要件 F-D-28） */
	private static final List<List<Column>> UNIQUE_KEYS = List.of(List.of(request_id, kind));

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

	public Notice (ISchema schema, String name) { super(schema, name); }

	public static Notice instance () { return new Notice(new ApprovalJobsExample(), "notice"); }

}
