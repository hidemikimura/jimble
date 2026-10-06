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
public class ApprovalJobsExample extends ApprovalJobsExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 通知 */
	public static final Table notice = Notice.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 申請（書庫） */
	public static final Table request_archive = RequestArchive.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_jobs_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_notice(sql);
		schemaSql_request(sql);
		schemaSql_request_archive(sql);

		return sql.toString();

	}

	/* notice（通知） */
	private static void schemaSql_notice (StringBuilder sql) {

		sql.append("create table ").append(notice).append(" ( ");
		sql.append(Notice.id).append(" bigserial not null,");
		sql.append(Notice.request_id).append(" bigint not null,");
		sql.append(Notice.to_staff_id).append(" bigint not null,");
		sql.append(Notice.kind).append(" character varying(30) not null,");
		sql.append(Notice.sent_at).append(" timestamp without time zone,");
		sql.append(Notice.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table notice add primary key (id);");
		sql.append("alter table notice add constraint notice__request_kind unique (request_id, kind);");
		sql.append("comment on table notice is '通知';");
		sql.append("comment on column notice.id is 'id';");
		sql.append("comment on column notice.request_id is 'request_id';");
		sql.append("comment on column notice.to_staff_id is 'to_staff_id';");
		sql.append("comment on column notice.kind is 'kind';");
		sql.append("comment on column notice.sent_at is 'sent_at';");
		sql.append("comment on column notice.created_at is 'created_at';");

	}

	/* request（申請） */
	private static void schemaSql_request (StringBuilder sql) {

		sql.append("create table ").append(request).append(" ( ");
		sql.append(Request.id).append(" bigserial not null,");
		sql.append(Request.staff_id).append(" bigint not null,");
		sql.append(Request.amount).append(" bigint not null,");
		sql.append(Request.needed_on).append(" date not null,");
		sql.append(Request.status).append(" character varying(20) not null,");
		sql.append(Request.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table request add primary key (id);");
		sql.append("create index request__status_needed_on on request (status, needed_on);");
		sql.append("comment on table request is '申請';");
		sql.append("comment on column request.id is 'id';");
		sql.append("comment on column request.staff_id is 'staff_id';");
		sql.append("comment on column request.amount is 'amount';");
		sql.append("comment on column request.needed_on is 'needed_on';");
		sql.append("comment on column request.status is 'status';");
		sql.append("comment on column request.created_at is 'created_at';");

	}

	/* request_archive（申請（書庫）） */
	private static void schemaSql_request_archive (StringBuilder sql) {

		sql.append("create table ").append(request_archive).append(" ( ");
		sql.append(RequestArchive.id).append(" bigint not null,");
		sql.append(RequestArchive.staff_id).append(" bigint not null,");
		sql.append(RequestArchive.amount).append(" bigint not null,");
		sql.append(RequestArchive.needed_on).append(" date not null,");
		sql.append(RequestArchive.status).append(" character varying(20) not null,");
		sql.append(RequestArchive.created_at).append(" timestamp without time zone not null,");
		sql.append(RequestArchive.archived_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table request_archive add primary key (id);");
		sql.append("comment on table request_archive is '申請（書庫）';");
		sql.append("comment on column request_archive.id is 'id';");
		sql.append("comment on column request_archive.staff_id is 'staff_id';");
		sql.append("comment on column request_archive.amount is 'amount';");
		sql.append("comment on column request_archive.needed_on is 'needed_on';");
		sql.append("comment on column request_archive.status is 'status';");
		sql.append("comment on column request_archive.created_at is 'created_at';");
		sql.append("comment on column request_archive.archived_at is 'archived_at';");

	}
}
