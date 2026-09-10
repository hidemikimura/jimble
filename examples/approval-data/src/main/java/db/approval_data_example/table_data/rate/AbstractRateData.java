/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_example.table_data.rate;

import db.approval_data_example.table.rate.Rate;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * レート
 */
public abstract class AbstractRateData<T extends AbstractRateData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(Rate.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(Rate.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(Rate.id);
	}

	/**
	 * get code
	 * 
	 * @return code
	 */
	public java.lang.String code () {
		return getString(Rate.code);
	}

	/**
	 * set code
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T code (java.lang.String code) {
		putData(Rate.code, code);
		return (T) this;
	}

	/**
	 * contains code
	 * 
	 * @return boolean
	 */
	public boolean containsCode () {
		return containsKey(Rate.code);
	}

	/**
	 * get value
	 * 
	 * @return value
	 */
	public long value () {
		return getLong(Rate.value);
	}

	/**
	 * set value
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T value (long value) {
		putData(Rate.value, value);
		return (T) this;
	}

	/**
	 * contains value
	 * 
	 * @return boolean
	 */
	public boolean containsValue () {
		return containsKey(Rate.value);
	}

	/**
	 * get updated_at
	 * 
	 * @return updated_at
	 */
	public java.util.Date updatedAt () {
		return getDate(Rate.updated_at);
	}

	/**
	 * set updated_at
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T updatedAt (java.util.Date updated_at) {
		putData(Rate.updated_at, updated_at);
		return (T) this;
	}

	/**
	 * contains updated_at
	 * 
	 * @return boolean
	 */
	public boolean containsUpdatedAt () {
		return containsKey(Rate.updated_at);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, Rate.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, Rate.instance(), req, excludeColumns);
	}

}
