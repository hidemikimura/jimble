/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example;

import db.blog_example.table.comment.Comment;
import db.blog_example.table.post.Post;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * blog_example
 */
public class BlogExample extends BlogExampleSchema {

	private static final long SQL_VERSION = 1;

	/* コメント */
	public static final Table comment = Comment.instance();

	/* 記事 */
	public static final Table post = Post.instance();


	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("blog_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = schemaSql();

	/* schema SQL を組み立てる（テーブルごとに分ける。1つの式にすると javac が深く再帰する） */
	private static String schemaSql () {

		StringBuilder sql = new StringBuilder();

		schemaSql_comment(sql);
		schemaSql_post(sql);

		return sql.toString();

	}

	/* comment（コメント） */
	private static void schemaSql_comment (StringBuilder sql) {

		sql.append("create table ").append(comment).append(" ( ");
		sql.append(Comment.id).append(" bigint(20) unsigned auto_increment not null comment 'コメントID',");
		sql.append(Comment.post_id).append(" bigint(20) unsigned not null comment '記事ID',");
		sql.append(Comment.name).append(" varchar(100) not null comment '名前',");
		sql.append(Comment.body).append(" text not null comment '本文',");
		sql.append(Comment.created_at).append(" datetime not null comment '作成日時'");
		sql.append(") comment 'コメント'; ");
		sql.append("alter table comment add primary key (id);");
		sql.append("alter table comment add index comment_post (post_id, created_at);");

	}

	/* post（記事） */
	private static void schemaSql_post (StringBuilder sql) {

		sql.append("create table ").append(post).append(" ( ");
		sql.append(Post.id).append(" bigint(20) unsigned auto_increment not null comment '記事ID',");
		sql.append(Post.title).append(" varchar(250) not null comment 'タイトル',");
		sql.append(Post.body).append(" text comment '本文',");
		sql.append(Post.image_name).append(" varchar(250) comment '画像ファイル名',");
		sql.append(Post.published).append(" tinyint(1) default '0' not null comment '公開',");
		sql.append(Post.created_at).append(" datetime not null comment '作成日時'");
		sql.append(") comment '記事'; ");
		sql.append("alter table post add primary key (id);");
		sql.append("alter table post add index post_published (published, created_at);");

	}
}
