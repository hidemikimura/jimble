/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_auth_example;

import db.approval_auth_example.table.staff.Staff;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_auth_example
 */
public class ApprovalAuthExample extends ApprovalAuthExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 社員 */
	public static final Table staff = Staff.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_auth_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_staff(sql);

		return sql.toString();

	}

	/* staff（社員） */
	private static void schemaSql_staff (StringBuilder sql) {

		sql.append("create table ").append(staff).append(" ( ");
		sql.append(Staff.id).append(" bigserial not null,");
		sql.append(Staff.login_id).append(" character varying(100) not null,");
		sql.append(Staff.password_hash).append(" character varying(255) not null,");
		sql.append(Staff.name).append(" character varying(100) not null,");
		sql.append(Staff.role).append(" character varying(20) not null,");
		sql.append(Staff.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table staff add primary key (id);");
		sql.append("alter table staff add constraint staff_login_id unique (login_id);");
		sql.append("comment on table staff is '社員';");
		sql.append("comment on column staff.id is '社員ID';");
		sql.append("comment on column staff.login_id is 'ログインID';");
		sql.append("comment on column staff.password_hash is 'パスワード（ハッシュ済み）';");
		sql.append("comment on column staff.name is '氏名';");
		sql.append("comment on column staff.role is '役割（member / approver）';");
		sql.append("comment on column staff.created_at is '作成日時';");

	}
}
