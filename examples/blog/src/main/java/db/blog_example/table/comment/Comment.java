/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example.table.comment;

import db.blog_example.BlogExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * コメント
 */
public class Comment extends Table {

	/* このテーブルの実体（1つだけ作る） */
	private static final Comment INSTANCE = new Comment(new BlogExample(), "comment");

	/* コメントID */
	public static final Column id = new Column(INSTANCE, "id", long.class, false, null, true);

	/* 記事ID */
	public static final Column post_id = new Column(INSTANCE, "post_id", long.class, false, null, false);

	/* 名前 */
	public static final Column name = new Column(INSTANCE, "name", java.lang.String.class, false, null, false);

	/* 本文 */
	public static final Column body = new Column(INSTANCE, "body", java.lang.String.class, false, null, false);

	/* 作成日時 */
	public static final Column created_at = new Column(INSTANCE, "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, post_id, name, body, created_at);

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

	public Comment (ISchema schema, String name) { super(schema, name); }

	public static Comment instance () { return INSTANCE; }

}
