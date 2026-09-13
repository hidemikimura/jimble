/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example.table.attachment;

import db.approval_forms_example.ApprovalFormsExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 添付
 */
public class Attachment extends Table {

	/* このテーブルの実体（1つだけ作る） */
	private static final Attachment INSTANCE = new Attachment(new ApprovalFormsExample(), "attachment");

	/* 添付ID */
	public static final Column id = new Column(INSTANCE, "id", long.class, false, null, true);

	/* 申請ID */
	public static final Column request_id = new Column(INSTANCE, "request_id", long.class, false, null, false);

	/* 保存したファイル名 */
	public static final Column file_name = new Column(INSTANCE, "file_name", java.lang.String.class, false, null, false);

	/* 種類 */
	public static final Column content_type = new Column(INSTANCE, "content_type", java.lang.String.class, false, null, false);

	/* 大きさ */
	public static final Column bytes = new Column(INSTANCE, "bytes", long.class, false, null, false);

	/* 作成日時 */
	public static final Column created_at = new Column(INSTANCE, "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, request_id, file_name, content_type, bytes, created_at);

	/* 一意キー（生成時に確定。要件 F-D-28） */
	private static final List<List<Column>> UNIQUE_KEYS = List.of();

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<Column> declareColumns () { return COLUMNS; }

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<List<Column>> declareUniqueKeys () { return UNIQUE_KEYS; }

	public static List<Column> columns () { return COLUMNS; }

	public static List<List<Column>> uniqueKeys () { return UNIQUE_KEYS; }

	public Attachment (ISchema schema, String name) { super(schema, name); }

	public static Attachment instance () { return INSTANCE; }

}
