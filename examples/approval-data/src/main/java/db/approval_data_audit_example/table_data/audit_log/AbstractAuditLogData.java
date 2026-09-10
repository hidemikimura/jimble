/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_audit_example.table_data.audit_log;

import db.approval_data_audit_example.table.audit_log.AuditLog;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 監査ログ
 */
public abstract class AbstractAuditLogData<T extends AbstractAuditLogData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(AuditLog.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(AuditLog.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(AuditLog.id);
	}

	/**
	 * get staff_id
	 * 
	 * @return staff_id
	 */
	public long staffId () {
		return getLong(AuditLog.staff_id);
	}

	/**
	 * set staff_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T staffId (long staff_id) {
		putData(AuditLog.staff_id, staff_id);
		return (T) this;
	}

	/**
	 * contains staff_id
	 * 
	 * @return boolean
	 */
	public boolean containsStaffId () {
		return containsKey(AuditLog.staff_id);
	}

	/**
	 * get action
	 * 
	 * @return action
	 */
	public java.lang.String action () {
		return getString(AuditLog.action);
	}

	/**
	 * set action
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T action (java.lang.String action) {
		putData(AuditLog.action, action);
		return (T) this;
	}

	/**
	 * contains action
	 * 
	 * @return boolean
	 */
	public boolean containsAction () {
		return containsKey(AuditLog.action);
	}

	/**
	 * get target
	 * 
	 * @return target
	 */
	public java.lang.String target () {
		return getString(AuditLog.target);
	}

	/**
	 * set target
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T target (java.lang.String target) {
		putData(AuditLog.target, target);
		return (T) this;
	}

	/**
	 * contains target
	 * 
	 * @return boolean
	 */
	public boolean containsTarget () {
		return containsKey(AuditLog.target);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(AuditLog.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(AuditLog.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(AuditLog.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, AuditLog.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, AuditLog.instance(), req, excludeColumns);
	}

}
