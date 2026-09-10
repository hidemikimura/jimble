/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example.table_data.request_archive;

import db.approval_jobs_example.table.request_archive.RequestArchive;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 申請（書庫）
 */
public abstract class AbstractRequestArchiveData<T extends AbstractRequestArchiveData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(RequestArchive.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(RequestArchive.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(RequestArchive.id);
	}

	/**
	 * get staff_id
	 * 
	 * @return staff_id
	 */
	public long staffId () {
		return getLong(RequestArchive.staff_id);
	}

	/**
	 * set staff_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T staffId (long staff_id) {
		putData(RequestArchive.staff_id, staff_id);
		return (T) this;
	}

	/**
	 * contains staff_id
	 * 
	 * @return boolean
	 */
	public boolean containsStaffId () {
		return containsKey(RequestArchive.staff_id);
	}

	/**
	 * get amount
	 * 
	 * @return amount
	 */
	public long amount () {
		return getLong(RequestArchive.amount);
	}

	/**
	 * set amount
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T amount (long amount) {
		putData(RequestArchive.amount, amount);
		return (T) this;
	}

	/**
	 * contains amount
	 * 
	 * @return boolean
	 */
	public boolean containsAmount () {
		return containsKey(RequestArchive.amount);
	}

	/**
	 * get needed_on
	 * 
	 * @return needed_on
	 */
	public java.util.Date neededOn () {
		return getDate(RequestArchive.needed_on);
	}

	/**
	 * set needed_on
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T neededOn (java.util.Date needed_on) {
		putData(RequestArchive.needed_on, needed_on);
		return (T) this;
	}

	/**
	 * contains needed_on
	 * 
	 * @return boolean
	 */
	public boolean containsNeededOn () {
		return containsKey(RequestArchive.needed_on);
	}

	/**
	 * get status
	 * 
	 * @return status
	 */
	public java.lang.String status () {
		return getString(RequestArchive.status);
	}

	/**
	 * set status
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T status (java.lang.String status) {
		putData(RequestArchive.status, status);
		return (T) this;
	}

	/**
	 * contains status
	 * 
	 * @return boolean
	 */
	public boolean containsStatus () {
		return containsKey(RequestArchive.status);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(RequestArchive.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(RequestArchive.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(RequestArchive.created_at);
	}

	/**
	 * get archived_at
	 * 
	 * @return archived_at
	 */
	public java.util.Date archivedAt () {
		return getDate(RequestArchive.archived_at);
	}

	/**
	 * set archived_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T archivedAt (java.util.Date archived_at) {
		putData(RequestArchive.archived_at, archived_at);
		return (T) this;
	}

	/**
	 * contains archived_at
	 * 
	 * @return boolean
	 */
	public boolean containsArchivedAt () {
		return containsKey(RequestArchive.archived_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, RequestArchive.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, RequestArchive.instance(), req, excludeColumns);
	}

}
