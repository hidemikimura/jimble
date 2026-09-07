/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example.table_data.comment;

import db.blog_example.table.comment.Comment;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * コメント
 */
public abstract class AbstractCommentData<T extends AbstractCommentData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Comment.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Comment.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Comment.id);
	}

	/**
	 * get post_id
	 * 
	 * @return post_id
	 */
	public long postId () {
		return getLong(Comment.post_id);
	}

	/**
	 * set post_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T postId (long post_id) {
		putData(Comment.post_id, post_id);
		return (T) this;
	}

	/**
	 * contains post_id
	 * 
	 * @return boolean
	 */
	public boolean containsPostId () {
		return containsKey(Comment.post_id);
	}

	/**
	 * get name
	 * 
	 * @return name
	 */
	public java.lang.String name () {
		return getString(Comment.name);
	}

	/**
	 * set name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T name (java.lang.String name) {
		putData(Comment.name, name);
		return (T) this;
	}

	/**
	 * contains name
	 * 
	 * @return boolean
	 */
	public boolean containsName () {
		return containsKey(Comment.name);
	}

	/**
	 * get body
	 * 
	 * @return body
	 */
	public java.lang.String body () {
		return getString(Comment.body);
	}

	/**
	 * set body
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T body (java.lang.String body) {
		putData(Comment.body, body);
		return (T) this;
	}

	/**
	 * contains body
	 * 
	 * @return boolean
	 */
	public boolean containsBody () {
		return containsKey(Comment.body);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Comment.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Comment.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Comment.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Comment.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Comment.instance(), req, excludeColumns);
	}

}
