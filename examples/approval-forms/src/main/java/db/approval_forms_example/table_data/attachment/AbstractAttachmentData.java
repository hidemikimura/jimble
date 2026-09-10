/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example.table_data.attachment;

import db.approval_forms_example.table.attachment.Attachment;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 添付
 */
public abstract class AbstractAttachmentData<T extends AbstractAttachmentData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Attachment.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Attachment.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Attachment.id);
	}

	/**
	 * get request_id
	 * 
	 * @return request_id
	 */
	public long requestId () {
		return getLong(Attachment.request_id);
	}

	/**
	 * set request_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T requestId (long request_id) {
		putData(Attachment.request_id, request_id);
		return (T) this;
	}

	/**
	 * contains request_id
	 * 
	 * @return boolean
	 */
	public boolean containsRequestId () {
		return containsKey(Attachment.request_id);
	}

	/**
	 * get file_name
	 * 
	 * @return file_name
	 */
	public java.lang.String fileName () {
		return getString(Attachment.file_name);
	}

	/**
	 * set file_name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T fileName (java.lang.String file_name) {
		putData(Attachment.file_name, file_name);
		return (T) this;
	}

	/**
	 * contains file_name
	 * 
	 * @return boolean
	 */
	public boolean containsFileName () {
		return containsKey(Attachment.file_name);
	}

	/**
	 * get content_type
	 * 
	 * @return content_type
	 */
	public java.lang.String contentType () {
		return getString(Attachment.content_type);
	}

	/**
	 * set content_type
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T contentType (java.lang.String content_type) {
		putData(Attachment.content_type, content_type);
		return (T) this;
	}

	/**
	 * contains content_type
	 * 
	 * @return boolean
	 */
	public boolean containsContentType () {
		return containsKey(Attachment.content_type);
	}

	/**
	 * get bytes
	 * 
	 * @return bytes
	 */
	public long bytes () {
		return getLong(Attachment.bytes);
	}

	/**
	 * set bytes
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T bytes (long bytes) {
		putData(Attachment.bytes, bytes);
		return (T) this;
	}

	/**
	 * contains bytes
	 * 
	 * @return boolean
	 */
	public boolean containsBytes () {
		return containsKey(Attachment.bytes);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Attachment.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Attachment.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Attachment.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Attachment.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Attachment.instance(), req, excludeColumns);
	}

}
