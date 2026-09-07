package io.jimble.db.generator.data;

import io.jimble.util.data.Data;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.table.Table;

import java.util.Date;
import java.util.HashSet;

/**
 * table data
 */
public abstract class AbstractTableData extends Data {

	/* 除外列名一覧 */
	private static final HashSet<String> excludeColumnNameSet = new HashSet<>();
	static {
		excludeColumnNameSet.add("created_at");
		excludeColumnNameSet.add("updated_at");
		excludeColumnNameSet.add("deleted_at");
	}

	/**
	 * insert用データを設定する
	 *
	 * @param builder			InsertBuilder
	 * @param table				テーブル
	 * @param req				リクエスト
	 * @param excludeColumns	除外列
	 */
	protected static void setInsertData (InsertBuilder builder, Table table, Data req, Column...excludeColumns) {

		HashSet<String> excludeColumnSet = null;
		if (excludeColumns != null && excludeColumns.length > 0) {
			excludeColumnSet = new HashSet<>();
			for (Column col : excludeColumns) {
				excludeColumnSet.add(col.name());
			}
		}

		for (Column column : table.getColumnList()) {

			if (column.isPrimaryKey()) {
				continue;
			}

			if (excludeColumnNameSet.contains(column.name())) {
				continue;
			}

			if (excludeColumnSet != null && excludeColumnSet.contains(column.name())) {
				continue;
			}

			if (req.containsKey(column)) {
				builder.value(column, getData(req, column));
			}

		}

	}

	/**
	 * update用データを設定する
	 *
	 * @param builder			UpdateBuilder
	 * @param table				テーブル
	 * @param req				リクエスト
	 * @param excludeColumns	除外列
	 */
	protected static void setUpdateData (UpdateBuilder builder, Table table, Data req, Column...excludeColumns) {

		HashSet<String> excludeColumnSet = null;
		if (excludeColumns != null && excludeColumns.length > 0) {
			excludeColumnSet = new HashSet<>();
			for (Column col : excludeColumns) {
				excludeColumnSet.add(col.name());
			}
		}

		for (Column column : table.getColumnList()) {

			if (column.isPrimaryKey()) {
				continue;
			}

			if (excludeColumnSet != null && excludeColumnSet.contains(column.name())) {
				continue;
			}

			if (req.containsKey(column)) {
				builder.set(column, getData(req, column));
			}

		}

	}

	/**
	 * データを取得する
	 *
	 * @param req		リクエスト
	 * @param column	列
	 * @return	データ
	 */
	private static Object getData (Data req, Column column) {

		if (long.class.equals(column.clazz())) {
			return req.getLongObject(column);
		} else if (int.class.equals(column.clazz())) {
			return req.getIntObject(column);
		} else if (double.class.equals(column.clazz())) {
			return req.getDoubleObject(column);
		} else if (boolean.class.equals(column.clazz())) {
			return req.getBooleanObject(column);
		} else if (Date.class.equals(column.clazz())) {
			return req.getDate(column);
		} else if (String.class.equals(column.clazz())) {
			return req.getString(column);
		} else if (Data.class.equals(column.clazz())) {
			return req.getData(column);
		}

		return req.getObject(column);

	}

}
