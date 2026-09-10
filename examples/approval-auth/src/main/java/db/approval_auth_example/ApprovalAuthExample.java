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
public class ApprovalAuthExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 社員 */
	public static final Table staff = Staff.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_auth_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_auth_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* staff（社員） */
		"create table " + staff + " ( " + 
			Staff.id + " bigserial not null," + 
			Staff.login_id + " character varying(100) not null," + 
			Staff.password_hash + " character varying(255) not null," + 
			Staff.name + " character varying(100) not null," + 
			Staff.role + " character varying(20) not null," + 
			Staff.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table staff add primary key (id);" +
		"alter table staff add constraint staff_login_id unique (login_id);" +
		"comment on table staff is '社員';" +
		"comment on column staff.id is '社員ID';" +
		"comment on column staff.login_id is 'ログインID';" +
		"comment on column staff.password_hash is 'パスワード（ハッシュ済み）';" +
		"comment on column staff.name is '氏名';" +
		"comment on column staff.role is '役割（member / approver）';" +
		"comment on column staff.created_at is '作成日時';" +
	"";
}
