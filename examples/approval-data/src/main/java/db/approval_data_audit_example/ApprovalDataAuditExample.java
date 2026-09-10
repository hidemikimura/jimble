/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_audit_example;

import db.approval_data_audit_example.table.audit_log.AuditLog;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_data_audit_example
 */
public class ApprovalDataAuditExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 監査ログ */
	public static final Table audit_log = AuditLog.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_data_audit_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_data_audit_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* audit_log（監査ログ） */
		"create table " + audit_log + " ( " + 
			AuditLog.id + " bigserial not null," + 
			AuditLog.staff_id + " bigint not null," + 
			AuditLog.action + " character varying(50) not null," + 
			AuditLog.target + " character varying(100) not null," + 
			AuditLog.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table audit_log add primary key (id);" +
		"create index audit_log__created_at on audit_log (created_at);" +
		"comment on table audit_log is '監査ログ';" +
		"comment on column audit_log.id is 'id';" +
		"comment on column audit_log.staff_id is 'staff_id';" +
		"comment on column audit_log.action is 'action';" +
		"comment on column audit_log.target is 'target';" +
		"comment on column audit_log.created_at is 'created_at';" +
	"";
}
