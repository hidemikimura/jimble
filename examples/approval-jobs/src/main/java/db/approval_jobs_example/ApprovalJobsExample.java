/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example;

import db.approval_jobs_example.table.notice.Notice;
import db.approval_jobs_example.table.request.Request;
import db.approval_jobs_example.table.request_archive.RequestArchive;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_jobs_example
 */
public class ApprovalJobsExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 通知 */
	public static final Table notice = Notice.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 申請（書庫） */
	public static final Table request_archive = RequestArchive.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_jobs_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_jobs_example"); }

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
			Notice.sent_at + " timestamp without time zone," + 
			Notice.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table notice add primary key (id);" +
		"alter table notice add constraint notice__request_kind unique (request_id, kind);" +
		"comment on table notice is '通知';" +
		"comment on column notice.id is 'id';" +
		"comment on column notice.request_id is 'request_id';" +
		"comment on column notice.to_staff_id is 'to_staff_id';" +
		"comment on column notice.kind is 'kind';" +
		"comment on column notice.sent_at is 'sent_at';" +
		"comment on column notice.created_at is 'created_at';" +

		/* request（申請） */
		"create table " + request + " ( " + 
			Request.id + " bigserial not null," + 
			Request.staff_id + " bigint not null," + 
			Request.amount + " bigint not null," + 
			Request.needed_on + " date not null," + 
			Request.status + " character varying(20) not null," + 
			Request.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table request add primary key (id);" +
		"create index request__status_needed_on on request (status, needed_on);" +
		"comment on table request is '申請';" +
		"comment on column request.id is 'id';" +
		"comment on column request.staff_id is 'staff_id';" +
		"comment on column request.amount is 'amount';" +
		"comment on column request.needed_on is 'needed_on';" +
		"comment on column request.status is 'status';" +
		"comment on column request.created_at is 'created_at';" +

		/* request_archive（申請（書庫）） */
		"create table " + request_archive + " ( " + 
			RequestArchive.id + " bigint not null," + 
			RequestArchive.staff_id + " bigint not null," + 
			RequestArchive.amount + " bigint not null," + 
			RequestArchive.needed_on + " date not null," + 
			RequestArchive.status + " character varying(20) not null," + 
			RequestArchive.created_at + " timestamp without time zone not null," + 
			RequestArchive.archived_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table request_archive add primary key (id);" +
		"comment on table request_archive is '申請（書庫）';" +
		"comment on column request_archive.id is 'id';" +
		"comment on column request_archive.staff_id is 'staff_id';" +
		"comment on column request_archive.amount is 'amount';" +
		"comment on column request_archive.needed_on is 'needed_on';" +
		"comment on column request_archive.status is 'status';" +
		"comment on column request_archive.created_at is 'created_at';" +
		"comment on column request_archive.archived_at is 'archived_at';" +
	"";
}
