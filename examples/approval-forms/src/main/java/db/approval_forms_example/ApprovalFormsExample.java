/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example;

import db.approval_forms_example.table.attachment.Attachment;
import db.approval_forms_example.table.request.Request;
import db.approval_forms_example.table.request_item.RequestItem;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * approval_forms_example
 */
public class ApprovalFormsExample extends ApprovalFormsExampleSchema {

	private static final long SQL_VERSION = 1;

	/* 添付 */
	public static final Table attachment = Attachment.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 申請の明細 */
	public static final Table request_item = RequestItem.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_forms_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_attachment(sql);
		schemaSql_request(sql);
		schemaSql_request_item(sql);

		return sql.toString();

	}

	/* attachment（添付） */
	private static void schemaSql_attachment (StringBuilder sql) {

		sql.append("create table ").append(attachment).append(" ( ");
		sql.append(Attachment.id).append(" bigserial not null,");
		sql.append(Attachment.request_id).append(" bigint not null,");
		sql.append(Attachment.file_name).append(" character varying(250) not null,");
		sql.append(Attachment.content_type).append(" character varying(100) not null,");
		sql.append(Attachment.bytes).append(" bigint not null,");
		sql.append(Attachment.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table attachment add primary key (id);");
		sql.append("comment on table attachment is '添付';");
		sql.append("comment on column attachment.id is '添付ID';");
		sql.append("comment on column attachment.request_id is '申請ID';");
		sql.append("comment on column attachment.file_name is '保存したファイル名';");
		sql.append("comment on column attachment.content_type is '種類';");
		sql.append("comment on column attachment.bytes is '大きさ';");
		sql.append("comment on column attachment.created_at is '作成日時';");

	}

	/* request（申請） */
	private static void schemaSql_request (StringBuilder sql) {

		sql.append("create table ").append(request).append(" ( ");
		sql.append(Request.id).append(" bigserial not null,");
		sql.append(Request.kind).append(" character varying(20) not null,");
		sql.append(Request.amount).append(" bigint not null,");
		sql.append(Request.needed_on).append(" date,");
		sql.append(Request.note).append(" text,");
		sql.append(Request.status).append(" character varying(20) not null,");
		sql.append(Request.created_at).append(" timestamp without time zone not null");
		sql.append("); ");
		sql.append("alter table request add primary key (id);");
		sql.append("comment on table request is '申請';");
		sql.append("comment on column request.id is '申請ID';");
		sql.append("comment on column request.kind is '種別（travel / supply / book）';");
		sql.append("comment on column request.amount is '金額（円）';");
		sql.append("comment on column request.needed_on is '希望日';");
		sql.append("comment on column request.note is '備考';");
		sql.append("comment on column request.status is '状態（draft / pending）';");
		sql.append("comment on column request.created_at is '作成日時';");

	}

	/* request_item（申請の明細） */
	private static void schemaSql_request_item (StringBuilder sql) {

		sql.append("create table ").append(request_item).append(" ( ");
		sql.append(RequestItem.id).append(" bigserial not null,");
		sql.append(RequestItem.request_id).append(" bigint not null,");
		sql.append(RequestItem.name).append(" character varying(100) not null,");
		sql.append(RequestItem.amount).append(" bigint not null,");
		sql.append(RequestItem.sort_no).append(" integer not null");
		sql.append("); ");
		sql.append("alter table request_item add primary key (id);");
		sql.append("create index request_item_request on request_item (request_id, sort_no);");
		sql.append("comment on table request_item is '申請の明細';");
		sql.append("comment on column request_item.id is '明細ID';");
		sql.append("comment on column request_item.request_id is '申請ID';");
		sql.append("comment on column request_item.name is '品目';");
		sql.append("comment on column request_item.amount is '金額（円）';");
		sql.append("comment on column request_item.sort_no is '並び順';");

	}
}
