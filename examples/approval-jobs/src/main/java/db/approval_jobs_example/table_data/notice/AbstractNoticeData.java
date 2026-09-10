/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example.table_data.notice;

import db.approval_jobs_example.table.notice.Notice;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 通知
 */
public abstract class AbstractNoticeData<T extends AbstractNoticeData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Notice.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Notice.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Notice.id);
	}

	/**
	 * get request_id
	 * 
	 * @return request_id
	 */
	public long requestId () {
		return getLong(Notice.request_id);
	}

	/**
	 * set request_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T requestId (long request_id) {
		putData(Notice.request_id, request_id);
		return (T) this;
	}

	/**
	 * contains request_id
	 * 
	 * @return boolean
	 */
	public boolean containsRequestId () {
		return containsKey(Notice.request_id);
	}

	/**
	 * get to_staff_id
	 * 
	 * @return to_staff_id
	 */
	public long toStaffId () {
		return getLong(Notice.to_staff_id);
	}

	/**
	 * set to_staff_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T toStaffId (long to_staff_id) {
		putData(Notice.to_staff_id, to_staff_id);
		return (T) this;
	}

	/**
	 * contains to_staff_id
	 * 
	 * @return boolean
	 */
	public boolean containsToStaffId () {
		return containsKey(Notice.to_staff_id);
	}

	/**
	 * get kind
	 * 
	 * @return kind
	 */
	public java.lang.String kind () {
		return getString(Notice.kind);
	}

	/**
	 * set kind
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T kind (java.lang.String kind) {
		putData(Notice.kind, kind);
		return (T) this;
	}

	/**
	 * contains kind
	 * 
	 * @return boolean
	 */
	public boolean containsKind () {
		return containsKey(Notice.kind);
	}

	/**
	 * get sent_at
	 * 
	 * @return sent_at
	 */
	public java.util.Date sentAt () {
		return getDate(Notice.sent_at);
	}

	/**
	 * set sent_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T sentAt (java.util.Date sent_at) {
		putData(Notice.sent_at, sent_at);
		return (T) this;
	}

	/**
	 * contains sent_at
	 * 
	 * @return boolean
	 */
	public boolean containsSentAt () {
		return containsKey(Notice.sent_at);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Notice.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Notice.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Notice.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Notice.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Notice.instance(), req, excludeColumns);
	}

}
