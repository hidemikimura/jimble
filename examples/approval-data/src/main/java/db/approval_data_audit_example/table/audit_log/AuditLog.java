/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_audit_example.table.audit_log;

import db.approval_data_audit_example.ApprovalDataAuditExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 監査ログ
 */
public class AuditLog extends Table {

	/* id */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* staff_id */
	public static final Column staff_id = new Column(instance(), "staff_id", long.class, false, null, false);

	/* action */
	public static final Column action = new Column(instance(), "action", java.lang.String.class, false, null, false);

	/* target */
	public static final Column target = new Column(instance(), "target", java.lang.String.class, false, null, false);

	/* created_at */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, staff_id, action, target, created_at);

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

	public AuditLog (ISchema schema, String name) { super(schema, name); }

	public static AuditLog instance () { return new AuditLog(new ApprovalDataAuditExample(), "audit_log"); }

}
