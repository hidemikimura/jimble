/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_auth_example.table_data.staff;

import db.approval_auth_example.table.staff.Staff;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 社員
 */
public abstract class AbstractStaffData<T extends AbstractStaffData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Staff.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Staff.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Staff.id);
	}

	/**
	 * get login_id
	 * 
	 * @return login_id
	 */
	public java.lang.String loginId () {
		return getString(Staff.login_id);
	}

	/**
	 * set login_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T loginId (java.lang.String login_id) {
		putData(Staff.login_id, login_id);
		return (T) this;
	}

	/**
	 * contains login_id
	 * 
	 * @return boolean
	 */
	public boolean containsLoginId () {
		return containsKey(Staff.login_id);
	}

	/**
	 * get password_hash
	 * 
	 * @return password_hash
	 */
	public java.lang.String passwordHash () {
		return getString(Staff.password_hash);
	}

	/**
	 * set password_hash
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T passwordHash (java.lang.String password_hash) {
		putData(Staff.password_hash, password_hash);
		return (T) this;
	}

	/**
	 * contains password_hash
	 * 
	 * @return boolean
	 */
	public boolean containsPasswordHash () {
		return containsKey(Staff.password_hash);
	}

	/**
	 * get name
	 * 
	 * @return name
	 */
	public java.lang.String name () {
		return getString(Staff.name);
	}

	/**
	 * set name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T name (java.lang.String name) {
		putData(Staff.name, name);
		return (T) this;
	}

	/**
	 * contains name
	 * 
	 * @return boolean
	 */
	public boolean containsName () {
		return containsKey(Staff.name);
	}

	/**
	 * get role
	 * 
	 * @return role
	 */
	public java.lang.String role () {
		return getString(Staff.role);
	}

	/**
	 * set role
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T role (java.lang.String role) {
		putData(Staff.role, role);
		return (T) this;
	}

	/**
	 * contains role
	 * 
	 * @return boolean
	 */
	public boolean containsRole () {
		return containsKey(Staff.role);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Staff.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Staff.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Staff.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Staff.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Staff.instance(), req, excludeColumns);
	}

}
