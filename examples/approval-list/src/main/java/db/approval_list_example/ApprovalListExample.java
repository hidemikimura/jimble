/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_list_example;

import db.approval_list_example.table.department.Department;
import db.approval_list_example.table.request.Request;
import db.approval_list_example.table.staff.Staff;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_list_example
 */
public class ApprovalListExample extends ApprovalListExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 部署 */
	public static final Table department = Department.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 社員 */
	public static final Table staff = Staff.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_list_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_department(sql);
		schemaSql_request(sql);
		schemaSql_staff(sql);

		return sql.toString();

	}

	/* department（部署） */
	private static void schemaSql_department (StringBuilder sql) {

		sql.append("create table ").append(department).append(" ( ");
		sql.append(Department.id).append(" bigserial not null,");
		sql.append(Department.name).append(" character varying(100) not null,");
		sql.append(Department.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table department add primary key (id);");
		sql.append("comment on table department is '部署';");
		sql.append("comment on column department.id is 'id';");
		sql.append("comment on column department.name is 'name';");
		sql.append("comment on column department.created_at is 'created_at';");

	}

	/* request（申請） */
	private static void schemaSql_request (StringBuilder sql) {

		sql.append("create table ").append(request).append(" ( ");
		sql.append(Request.id).append(" bigserial not null,");
		sql.append(Request.staff_id).append(" bigint not null,");
		sql.append(Request.kind).append(" character varying(20) not null,");
		sql.append(Request.amount).append(" bigint not null,");
		sql.append(Request.needed_on).append(" date not null,");
		sql.append(Request.status).append(" character varying(20) not null,");
		sql.append(Request.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table request add primary key (id);");
		sql.append("create index request_staff on request (staff_id, created_at);");
		sql.append("create index request_status on request (status, created_at);");
		sql.append("comment on table request is '申請';");
		sql.append("comment on column request.id is 'id';");
		sql.append("comment on column request.staff_id is 'staff_id';");
		sql.append("comment on column request.kind is 'kind';");
		sql.append("comment on column request.amount is 'amount';");
		sql.append("comment on column request.needed_on is 'needed_on';");
		sql.append("comment on column request.status is 'status';");
		sql.append("comment on column request.created_at is 'created_at';");

	}

	/* staff（社員） */
	private static void schemaSql_staff (StringBuilder sql) {

		sql.append("create table ").append(staff).append(" ( ");
		sql.append(Staff.id).append(" bigserial not null,");
		sql.append(Staff.department_id).append(" bigint not null,");
		sql.append(Staff.name).append(" character varying(100) not null,");
		sql.append(Staff.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table staff add primary key (id);");
		sql.append("create index staff_department on staff (department_id);");
		sql.append("comment on table staff is '社員';");
		sql.append("comment on column staff.id is 'id';");
		sql.append("comment on column staff.department_id is 'department_id';");
		sql.append("comment on column staff.name is 'name';");
		sql.append("comment on column staff.created_at is 'created_at';");

	}
}
