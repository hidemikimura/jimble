/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example.table_data.post;

import db.blog_example.table.post.Post;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 記事
 */
public abstract class AbstractPostData<T extends AbstractPostData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Post.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Post.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Post.id);
	}

	/**
	 * get title
	 * 
	 * @return title
	 */
	public java.lang.String title () {
		return getString(Post.title);
	}

	/**
	 * set title
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T title (java.lang.String title) {
		putData(Post.title, title);
		return (T) this;
	}

	/**
	 * contains title
	 * 
	 * @return boolean
	 */
	public boolean containsTitle () {
		return containsKey(Post.title);
	}

	/**
	 * get body
	 * 
	 * @return body
	 */
	public java.lang.String body () {
		return getString(Post.body);
	}

	/**
	 * set body
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T body (java.lang.String body) {
		putData(Post.body, body);
		return (T) this;
	}

	/**
	 * contains body
	 * 
	 * @return boolean
	 */
	public boolean containsBody () {
		return containsKey(Post.body);
	}

	/**
	 * get image_name
	 * 
	 * @return image_name
	 */
	public java.lang.String imageName () {
		return getString(Post.image_name);
	}

	/**
	 * set image_name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T imageName (java.lang.String image_name) {
		putData(Post.image_name, image_name);
		return (T) this;
	}

	/**
	 * contains image_name
	 * 
	 * @return boolean
	 */
	public boolean containsImageName () {
		return containsKey(Post.image_name);
	}

	/**
	 * get published
	 * 
	 * @return published
	 */
	public boolean published () {
		return getBoolean(Post.published);
	}

	/**
	 * set published
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T published (boolean published) {
		putData(Post.published, published);
		return (T) this;
	}

	/**
	 * contains published
	 * 
	 * @return boolean
	 */
	public boolean containsPublished () {
		return containsKey(Post.published);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Post.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Post.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Post.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Post.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Post.instance(), req, excludeColumns);
	}

}
