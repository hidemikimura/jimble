/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_auth_example.table.staff;

import db.approval_auth_example.ApprovalAuthExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 社員
 */
public class Staff extends Table {

	/* このテーブルの実体（1つだけ作る） */
	private static final Staff INSTANCE = new Staff(new ApprovalAuthExample(), "staff");

	/* 社員ID */
	public static final Column id = new Column(INSTANCE, "id", long.class, false, null, true);

	/* ログインID */
	public static final Column login_id = new Column(INSTANCE, "login_id", java.lang.String.class, false, null, false);

	/* パスワード（ハッシュ済み） */
	public static final Column password_hash = new Column(INSTANCE, "password_hash", java.lang.String.class, false, null, false);

	/* 氏名 */
	public static final Column name = new Column(INSTANCE, "name", java.lang.String.class, false, null, false);

	/* 役割（member / approver） */
	public static final Column role = new Column(INSTANCE, "role", java.lang.String.class, false, null, false);

	/* 作成日時 */
	public static final Column created_at = new Column(INSTANCE, "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, login_id, password_hash, name, role, created_at);

	/* 一意キー（生成時に確定。要件 F-D-28） */
	private static final List<List<Column>> UNIQUE_KEYS = List.of(List.of(login_id));

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

	public Staff (ISchema schema, String name) { super(schema, name); }

	public static Staff instance () { return INSTANCE; }

}
