/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example.table_data.request_item;

import db.approval_forms_example.table.request_item.RequestItem;
import io.jimble.db.generator.data.AbstractTableData;
import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;

/**
 * 申請の明細
 */
public abstract class AbstractRequestItemData<T extends AbstractRequestItemData<?>> extends AbstractTableData {

	/**
	 * get id
	 * 
	 * @return id
	 */
	public long id () {
		return getLong(RequestItem.id);
	}

	/**
	 * set id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T id (long id) {
		putData(RequestItem.id, id);
		return (T) this;
	}

	/**
	 * contains id
	 * 
	 * @return boolean
	 */
	public boolean containsId () {
		return containsKey(RequestItem.id);
	}

	/**
	 * get request_id
	 * 
	 * @return request_id
	 */
	public long requestId () {
		return getLong(RequestItem.request_id);
	}

	/**
	 * set request_id
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T requestId (long request_id) {
		putData(RequestItem.request_id, request_id);
		return (T) this;
	}

	/**
	 * contains request_id
	 * 
	 * @return boolean
	 */
	public boolean containsRequestId () {
		return containsKey(RequestItem.request_id);
	}

	/**
	 * get name
	 * 
	 * @return name
	 */
	public java.lang.String name () {
		return getString(RequestItem.name);
	}

	/**
	 * set name
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T name (java.lang.String name) {
		putData(RequestItem.name, name);
		return (T) this;
	}

	/**
	 * contains name
	 * 
	 * @return boolean
	 */
	public boolean containsName () {
		return containsKey(RequestItem.name);
	}

	/**
	 * get amount
	 * 
	 * @return amount
	 */
	public long amount () {
		return getLong(RequestItem.amount);
	}

	/**
	 * set amount
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T amount (long amount) {
		putData(RequestItem.amount, amount);
		return (T) this;
	}

	/**
	 * contains amount
	 * 
	 * @return boolean
	 */
	public boolean containsAmount () {
		return containsKey(RequestItem.amount);
	}

	/**
	 * get sort_no
	 * 
	 * @return sort_no
	 */
	public int sortNo () {
		return getInt(RequestItem.sort_no);
	}

	/**
	 * set sort_no
	 * 
	 * @return Data
	 */
	@SuppressWarnings("unchecked")
	public T sortNo (int sort_no) {
		putData(RequestItem.sort_no, sort_no);
		return (T) this;
	}

	/**
	 * contains sort_no
	 * 
	 * @return boolean
	 */
	public boolean containsSortNo () {
		return containsKey(RequestItem.sort_no);
	}


	/**
	 * set insert sql data
	 * 
	 * @param builder InsertBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {
		setInsertData(builder, RequestItem.instance(), req, excludeColumns);
	}

	/**
	 * set update sql data
	 * 
	 * @param builder UpdateBuilder
	 * @param req request
	 * @param excludeColumns exclude columns
	 */
	public static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {
		setUpdateData(builder, RequestItem.instance(), req, excludeColumns);
	}

}
