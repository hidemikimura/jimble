/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_list_example.table_data.department;

import db.approval_list_example.table.department.Department;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 部署
 */
public abstract class AbstractDepartmentData<T extends AbstractDepartmentData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Department.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Department.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Department.id);
	}

	/**
	 * get name
	 * 
	 * @return name
	 */
	public java.lang.String name () {
		return getString(Department.name);
	}

	/**
	 * set name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T name (java.lang.String name) {
		putData(Department.name, name);
		return (T) this;
	}

	/**
	 * contains name
	 * 
	 * @return boolean
	 */
	public boolean containsName () {
		return containsKey(Department.name);
	}

	/**
	 * get created_at
	 * 
	 * @return created_at
	 */
	public java.util.Date createdAt () {
		return getDate(Department.created_at);
	}

	/**
	 * set created_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T createdAt (java.util.Date created_at) {
		putData(Department.created_at, created_at);
		return (T) this;
	}

	/**
	 * contains created_at
	 * 
	 * @return boolean
	 */
	public boolean containsCreatedAt () {
		return containsKey(Department.created_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Department.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Department.instance(), req, excludeColumns);
	}

}
