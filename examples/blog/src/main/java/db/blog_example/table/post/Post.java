/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example.table.post;

import db.blog_example.BlogExample;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * 記事
 */
public class Post extends Table {

	/* 記事ID */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* タイトル */
	public static final Column title = new Column(instance(), "title", java.lang.String.class, false, null, false);

	/* 本文 */
	public static final Column body = new Column(instance(), "body", java.lang.String.class, true, null, false);

	/* 画像ファイル名 */
	public static final Column image_name = new Column(instance(), "image_name", java.lang.String.class, true, null, false);

	/* 公開 */
	public static final Column published = new Column(instance(), "published", boolean.class, false, false, false);

	/* 作成日時 */
	public static final Column created_at = new Column(instance(), "created_at", java.util.Date.class, false, null, false);


	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, title, body, image_name, published, created_at);

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

	public Post (ISchema schema, String name) { super(schema, name); }

	public static Post instance () { return new Post(new BlogExample(), "post"); }

}
