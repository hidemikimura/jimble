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
public class ApprovalDataAuditExample extends ApprovalDataAuditExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 監査ログ */
	public static final Table audit_log = AuditLog.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_data_audit_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_audit_log(sql);

		return sql.toString();

	}

	/* audit_log（監査ログ） */
	private static void schemaSql_audit_log (StringBuilder sql) {

		sql.append("create table ").append(audit_log).append(" ( ");
		sql.append(AuditLog.id).append(" bigserial not null,");
		sql.append(AuditLog.staff_id).append(" bigint not null,");
		sql.append(AuditLog.action).append(" character varying(50) not null,");
		sql.append(AuditLog.target).append(" character varying(100) not null,");
		sql.append(AuditLog.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table audit_log add primary key (id);");
		sql.append("create index audit_log__created_at on audit_log (created_at);");
		sql.append("comment on table audit_log is '監査ログ';");
		sql.append("comment on column audit_log.id is 'id';");
		sql.append("comment on column audit_log.staff_id is 'staff_id';");
		sql.append("comment on column audit_log.action is 'action';");
		sql.append("comment on column audit_log.target is 'target';");
		sql.append("comment on column audit_log.created_at is 'created_at';");

	}
}
