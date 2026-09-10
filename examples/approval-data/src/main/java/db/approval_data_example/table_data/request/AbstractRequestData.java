/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_example.table_data.request;

import db.approval_data_example.table.request.Request;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 申請
 */
public abstract class AbstractRequestData<T extends AbstractRequestData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Request.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Request.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Request.id);
	}

	/**
	 * get staff_id
	 * 
	 * @return staff_id
	 */
	public long staffId () {
		return getLong(Request.staff_id);
	}

	/**
	 * set staff_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T staffId (long staff_id) {
		putData(Request.staff_id, staff_id);
		return (T) this;
	}

	/**
	 * contains staff_id
	 * 
	 * @return boolean
	 */
	public boolean containsStaffId () {
		return containsKey(Request.staff_id);
	}

	/**
	 * get amount
	 * 
	 * @return amount
	 */
	public long amount () {
		return getLong(Request.amount);
	}

	/**
	 * set amount
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T amount (long amount) {
		putData(Request.amount, amount);
		return (T) this;
	}

	/**
	 * contains amount
	 * 
	 * @return boolean
	 */
	public boolean containsAmount () {
		return containsKey(Request.amount);
	}

	/**
	 * get status
	 * 
	 * @return status
	 */
	public java.lang.String status () {
		return getString(Request.status);
	}

	/**
	 * set status
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T status (java.lang.String status) {
		putData(Request.status, status);
		return (T) this;
	}

	/**
	 * contains status
	 * 
	 * @return boolean
	 */
	public boolean containsStatus () {
		return containsKey(Request.status);
	}

	/**
	 * get decided_by
	 * 
	 * @return decided_by
	 */
	public long decidedBy () {
		return getLong(Request.decided_by);
	}

	/**
	 * set decided_by
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T decidedBy (long decided_by) {
		putData(Request.decided_by, decided_by);
		return (T) this;
	}

	/**
	 * contains decided_by
	 * 
	 * @return boolean
	 */
	public boolean containsDecidedBy () {
		return containsKey(Request.decided_by);
	}

	/**
	 * get decided_at
	 * 
	 * @return decided_at
	 */
	public java.util.Date decidedAt () {
		return getDate(Request.decided_at);
	}

	/**
	 * set decided_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T decidedAt (java.util.Date decided_at) {
		putData(Request.decided_at, decided_at);
		return (T) this;
	}

	/**
	 * contains decided_at
	 * 
	 * @return boolean
	 */
	public boolean containsDecidedAt () {
		return containsKey(Request.decided_at);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Request.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Request.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Request.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Request.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Request.instance(), req, excludeColumns);
	}

}
