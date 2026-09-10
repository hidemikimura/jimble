/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example.table.request_item;

import db.approval_forms_example.ApprovalFormsExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 申請の明細
 */
public class RequestItem extends Table {

	/* 明細ID */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* 申請ID */
	public static final Column request_id = new Column(instance(), "request_id", long.class, false, null, false);

	/* 品目 */
	public static final Column name = new Column(instance(), "name", java.lang.String.class, false, null, false);

	/* 金額（円） */
	public static final Column amount = new Column(instance(), "amount", long.class, false, null, false);

	/* 並び順 */
	public static final Column sort_no = new Column(instance(), "sort_no", int.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, request_id, name, amount, sort_no);

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

	public RequestItem (ISchema schema, String name) { super(schema, name); }

	public static RequestItem instance () { return new RequestItem(new ApprovalFormsExample(), "request_item"); }

}
