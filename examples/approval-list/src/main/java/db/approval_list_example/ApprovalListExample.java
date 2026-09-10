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
public class ApprovalListExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 部署 */
	public static final Table department = Department.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 社員 */
	public static final Table staff = Staff.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_list_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_list_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* department（部署） */
		"create table " + department + " ( " + 
			Department.id + " bigserial not null," + 
			Department.name + " character varying(100) not null," + 
			Department.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table department add primary key (id);" +
		"comment on table department is '部署';" +
		"comment on column department.id is 'id';" +
		"comment on column department.name is 'name';" +
		"comment on column department.created_at is 'created_at';" +

		/* request（申請） */
		"create table " + request + " ( " + 
			Request.id + " bigserial not null," + 
			Request.staff_id + " bigint not null," + 
			Request.kind + " character varying(20) not null," + 
			Request.amount + " bigint not null," + 
			Request.needed_on + " date not null," + 
			Request.status + " character varying(20) not null," + 
			Request.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table request add primary key (id);" +
		"create index request_staff on request (staff_id, created_at);" +
		"create index request_status on request (status, created_at);" +
		"comment on table request is '申請';" +
		"comment on column request.id is 'id';" +
		"comment on column request.staff_id is 'staff_id';" +
		"comment on column request.kind is 'kind';" +
		"comment on column request.amount is 'amount';" +
		"comment on column request.needed_on is 'needed_on';" +
		"comment on column request.status is 'status';" +
		"comment on column request.created_at is 'created_at';" +

		/* staff（社員） */
		"create table " + staff + " ( " + 
			Staff.id + " bigserial not null," + 
			Staff.department_id + " bigint not null," + 
			Staff.name + " character varying(100) not null," + 
			Staff.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table staff add primary key (id);" +
		"create index staff_department on staff (department_id);" +
		"comment on table staff is '社員';" +
		"comment on column staff.id is 'id';" +
		"comment on column staff.department_id is 'department_id';" +
		"comment on column staff.name is 'name';" +
		"comment on column staff.created_at is 'created_at';" +
	"";
}
