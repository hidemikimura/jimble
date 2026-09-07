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
public class BlogExample extends AbstractSchema {

	private static final long SQL_VERSION = 1;

	/* コメント */
	public static final Table comment = Comment.instance();

	/* 記事 */
	public static final Table post = Post.instance();


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "blog_example"; }

	/**
	 * get DB instance
	 *
	 * @return DB
	 */
	public static DB db () { return DBUtil.getDB("blog_example"); }

	/**
	 * schema SQL
	 */
	public static final String SchemaSQL = 

		/* comment（コメント） */
		"create table " + comment + " ( " + 
			Comment.id + " bigint(20) unsigned auto_increment not null comment 'コメントID'," + 
			Comment.post_id + " bigint(20) unsigned not null comment '記事ID'," + 
			Comment.name + " varchar(100) not null comment '名前'," + 
			Comment.body + " text not null comment '本文'," + 
			Comment.created_at + " datetime not null comment '作成日時'" + 
		") comment 'コメント'; " + 
		"alter table comment add primary key (id);" +
		"alter table comment add index comment_post (post_id, created_at);" +

		/* post（記事） */
		"create table " + post + " ( " + 
			Post.id + " bigint(20) unsigned auto_increment not null comment '記事ID'," + 
			Post.title + " varchar(250) not null comment 'タイトル'," + 
			Post.body + " text comment '本文'," + 
			Post.image_name + " varchar(250) comment '画像ファイル名'," + 
			Post.published + " tinyint(1) default '0' not null comment '公開'," + 
			Post.created_at + " datetime not null comment '作成日時'" + 
		") comment '記事'; " + 
		"alter table post add primary key (id);" +
		"alter table post add index post_published (published, created_at);" +
	"";
}
