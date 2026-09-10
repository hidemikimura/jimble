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
public class ApprovalFormsExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* 添付 */
	public static final Table attachment = Attachment.instance();

	/* 申請 */
	public static final Table request = Request.instance();

	/* 申請の明細 */
	public static final Table request_item = RequestItem.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_forms_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("approval_forms_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* attachment（添付） */
		"create table " + attachment + " ( " + 
			Attachment.id + " bigserial not null," + 
			Attachment.request_id + " bigint not null," + 
			Attachment.file_name + " character varying(250) not null," + 
			Attachment.content_type + " character varying(100) not null," + 
			Attachment.bytes + " bigint not null," + 
			Attachment.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table attachment add primary key (id);" +
		"comment on table attachment is '添付';" +
		"comment on column attachment.id is '添付ID';" +
		"comment on column attachment.request_id is '申請ID';" +
		"comment on column attachment.file_name is '保存したファイル名';" +
		"comment on column attachment.content_type is '種類';" +
		"comment on column attachment.bytes is '大きさ';" +
		"comment on column attachment.created_at is '作成日時';" +

		/* request（申請） */
		"create table " + request + " ( " + 
			Request.id + " bigserial not null," + 
			Request.kind + " character varying(20) not null," + 
			Request.amount + " bigint not null," + 
			Request.needed_on + " date," + 
			Request.note + " text," + 
			Request.status + " character varying(20) not null," + 
			Request.created_at + " timestamp without time zone not null" + 
		"); " + 
		"alter table request add primary key (id);" +
		"comment on table request is '申請';" +
		"comment on column request.id is '申請ID';" +
		"comment on column request.kind is '種別（travel / supply / book）';" +
		"comment on column request.amount is '金額（円）';" +
		"comment on column request.needed_on is '希望日';" +
		"comment on column request.note is '備考';" +
		"comment on column request.status is '状態（draft / pending）';" +
		"comment on column request.created_at is '作成日時';" +

		/* request_item（申請の明細） */
		"create table " + request_item + " ( " + 
			RequestItem.id + " bigserial not null," + 
			RequestItem.request_id + " bigint not null," + 
			RequestItem.name + " character varying(100) not null," + 
			RequestItem.amount + " bigint not null," + 
			RequestItem.sort_no + " integer not null" + 
		"); " + 
		"alter table request_item add primary key (id);" +
		"create index request_item_request on request_item (request_id, sort_no);" +
		"comment on table request_item is '申請の明細';" +
		"comment on column request_item.id is '明細ID';" +
		"comment on column request_item.request_id is '申請ID';" +
		"comment on column request_item.name is '品目';" +
		"comment on column request_item.amount is '金額（円）';" +
		"comment on column request_item.sort_no is '並び順';" +
	"";
}
