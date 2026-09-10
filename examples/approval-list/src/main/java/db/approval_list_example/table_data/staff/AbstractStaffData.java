/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_list_example.table_data.staff;

import db.approval_list_example.table.staff.Staff;
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
	 * get department_id
	 * 
	 * @return department_id
	 */
	public long departmentId () {
		return getLong(Staff.department_id);
	}

	/**
	 * set department_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T departmentId (long department_id) {
		putData(Staff.department_id, department_id);
		return (T) this;
	}

	/**
	 * contains department_id
	 * 
	 * @return boolean
	 */
	public boolean containsDepartmentId () {
		return containsKey(Staff.department_id);
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
