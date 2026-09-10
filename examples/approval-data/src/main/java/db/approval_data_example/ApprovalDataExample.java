/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_example;

import db.approval_data_example.table.notice.Notice;
import db.approval_data_example.table.rate.Rate;
import db.approval_data_example.table.request.Request;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_data_example
 */
public class ApprovalDataExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 通知 */
	public static final Table notice = Notice.instance();

	/* レート */
	public static final Table rate = Rate.instance();

	/* 申請 */
	public static final Table request = Request.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_data_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_data_example"); }

	/**
	 * get sub DB instance
	 *
	 * @return sub DB
	 */
	public static DB scratchDB () { return DBUtil.getDB("approval_data_example").newSubDB("scratch"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* notice（通知） */
		"create table " + notice + " ( " + 
			Notice.id + " bigserial not null," + 
			Notice.request_id + " bigint not null," + 
			Notice.to_staff_id + " bigint not null," + 
			Notice.kind + " character varying(30) not null," + 
			Notice.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table notice add primary key (id);" +
		"alter table notice add constraint notice__request_kind unique (request_id, kind);" +
		"comment on table notice is '通知';" +
		"comment on column notice.id is 'id';" +
		"comment on column notice.request_id is 'request_id';" +
		"comment on column notice.to_staff_id is 'to_staff_id';" +
		"comment on column notice.kind is 'kind';" +
		"comment on column notice.created_at is 'created_at';" +

		/* rate（レート） */
		"create table " + rate + " ( " + 
			Rate.id + " bigserial not null," + 
			Rate.code + " character varying(20) not null," + 
			Rate.value + " bigint not null," + 
			Rate.updated_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table rate add primary key (id);" +
		"alter table rate add constraint rate_code_key unique (code);" +
		"comment on table rate is 'レート';" +
		"comment on column rate.id is 'id';" +
		"comment on column rate.code is 'code';" +
		"comment on column rate.value is 'value';" +
		"comment on column rate.updated_at is 'updated_at';" +

		/* request（申請） */
		"create table " + request + " ( " + 
			Request.id + " bigserial not null," + 
			Request.staff_id + " bigint not null," + 
			Request.amount + " bigint not null," + 
			Request.status + " character varying(20) not null," + 
			Request.decided_by + " bigint," + 
			Request.decided_at + " timestamp without time zone," + 
			Request.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table request add primary key (id);" +
		"comment on table request is '申請';" +
		"comment on column request.id is 'id';" +
		"comment on column request.staff_id is 'staff_id';" +
		"comment on column request.amount is 'amount';" +
		"comment on column request.status is 'status';" +
		"comment on column request.decided_by is 'decided_by';" +
		"comment on column request.decided_at is 'decided_at';" +
		"comment on column request.created_at is 'created_at';" +
	"";
}
