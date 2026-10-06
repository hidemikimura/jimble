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
public class ApprovalDataExample extends ApprovalDataExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 通知 */
	public static final Table notice = Notice.instance();

	/* レート */
	public static final Table rate = Rate.instance();

	/* 申請 */
	public static final Table request = Request.instance();


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
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_notice(sql);
		schemaSql_rate(sql);
		schemaSql_request(sql);

		return sql.toString();

	}

	/* notice（通知） */
	private static void schemaSql_notice (StringBuilder sql) {

		sql.append("create table ").append(notice).append(" ( ");
		sql.append(Notice.id).append(" bigserial not null,");
		sql.append(Notice.request_id).append(" bigint not null,");
		sql.append(Notice.to_staff_id).append(" bigint not null,");
		sql.append(Notice.kind).append(" character varying(30) not null,");
		sql.append(Notice.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table notice add primary key (id);");
		sql.append("alter table notice add constraint notice__request_kind unique (request_id, kind);");
		sql.append("comment on table notice is '通知';");
		sql.append("comment on column notice.id is 'id';");
		sql.append("comment on column notice.request_id is 'request_id';");
		sql.append("comment on column notice.to_staff_id is 'to_staff_id';");
		sql.append("comment on column notice.kind is 'kind';");
		sql.append("comment on column notice.created_at is 'created_at';");

	}

	/* rate（レート） */
	private static void schemaSql_rate (StringBuilder sql) {

		sql.append("create table ").append(rate).append(" ( ");
		sql.append(Rate.id).append(" bigserial not null,");
		sql.append(Rate.code).append(" character varying(20) not null,");
		sql.append(Rate.value).append(" bigint not null,");
		sql.append(Rate.updated_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table rate add primary key (id);");
		sql.append("alter table rate add constraint rate_code_key unique (code);");
		sql.append("comment on table rate is 'レート';");
		sql.append("comment on column rate.id is 'id';");
		sql.append("comment on column rate.code is 'code';");
		sql.append("comment on column rate.value is 'value';");
		sql.append("comment on column rate.updated_at is 'updated_at';");

	}

	/* request（申請） */
	private static void schemaSql_request (StringBuilder sql) {

		sql.append("create table ").append(request).append(" ( ");
		sql.append(Request.id).append(" bigserial not null,");
		sql.append(Request.staff_id).append(" bigint not null,");
		sql.append(Request.amount).append(" bigint not null,");
		sql.append(Request.status).append(" character varying(20) not null,");
		sql.append(Request.decided_by).append(" bigint,");
		sql.append(Request.decided_at).append(" timestamp without time zone,");
		sql.append(Request.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table request add primary key (id);");
		sql.append("comment on table request is '申請';");
		sql.append("comment on column request.id is 'id';");
		sql.append("comment on column request.staff_id is 'staff_id';");
		sql.append("comment on column request.amount is 'amount';");
		sql.append("comment on column request.status is 'status';");
		sql.append("comment on column request.decided_by is 'decided_by';");
		sql.append("comment on column request.decided_at is 'decided_at';");
		sql.append("comment on column request.created_at is 'created_at';");

	}
}
