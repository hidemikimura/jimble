package io.jimble.db.sql;

import io.jimble.util.data.Data;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.parameter.Parameter;
import io.jimble.db.sql.query.set.ISet;
import io.jimble.db.sql.query.set.Set;
import io.jimble.db.sql.query.value.IValue;
import io.jimble.db.sql.query.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * insert builder
 */
public class InsertBuilder extends AbstractBuilder<InsertBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param table	Table
	 */
	public InsertBuilder (ITable table) {

		this.table = table;

	}

	// region TABLE

	/* FROM句 */
	private ITable table = null;

	/**
	 * TBALE
	 *
	 * @return	TABLE
	 */
	public ITable getTable() {

		return table;

	}

	// endregion

	// region VALUE

	/* valueリスト */
	private final List<IValue> valueList = new ArrayList<>();

	/**
	 * VALUE句
	 *
	 * @param column	column
	 * @param value		value
	 * @return	InsertBuilder
	 */
	public InsertBuilder value (IColumn column, Object value) {

		valueList.add(new Value(column, value));
		return this;

	}

	/**
	 * VALUE句
	 *
	 * @param value value JSON { value: { table_name: { column_name: value } } }
	 * @return  InsertBuilder
	 */
	public InsertBuilder value (Data value) {

		if (!value.containsKey("value")) {
			return this;
		}
		Data valueData = value.getData("value");

		if (!valueData.containsKey(table.name())) {
			return this;
		}
		Data tableData = valueData.getDataOptional(table.name());

		for (String columnName : tableData.keySet()) {
			Object v = tableData.get(columnName);
			if (v instanceof String stringValue) {
				if ("now()".equals(stringValue)) {
					value(new TemporaryColumn(table, columnName), Dsl.now());
				} else {
					value(new TemporaryColumn(table, columnName), stringValue);
				}
			} else {
				value(new TemporaryColumn(table, columnName), v);
			}
		}

		return this;

	}

	// endregion

	// region VALUE（列指定のみ）

	/* value(列指定のみ)リスト */
	private final List<IColumn> columnList = new ArrayList<>();

	/**
	 * VALUE句（列指定のみ）
	 *
	 * @param column	column
	 * @return	InsertBuilder
	 */
	public InsertBuilder value (IColumn...column) {

		columnList.addAll(Arrays.asList(column));
		return this;

	}

	// endregion

	// region SelectBuilder

	/* SelectBuilder */
	private SelectBuilder selectBuilder = null;

	/**
	 * VALUE句（Select）
	 *
	 * @param selectBuilder select builder
	 * @return  InsertBuilder
	 */
	public InsertBuilder value (SelectBuilder selectBuilder) {

		this.selectBuilder = selectBuilder;
		return this;

	}

	// endregion

	// region SET

	/* set */
	private final List<ISet> setList = new ArrayList<>();

	/**
	 * on duplicate key update
	 *
	 * @param column	column
	 * @param value		value
	 * @return	InsertBuilder
	 */
	public InsertBuilder onDuplicateKeyUpdate (IColumn column, Object value) {

		setList.add(new Set(column, value));
		return this;

	}

	/**
	 * on duplicate key update
	 *
	 * @param column	column
	 * @return	InsertBuilder
	 */
	public InsertBuilder onDuplicateKeyUpdateValues (IColumn column) {

		setList.add(new Set(column, Dsl.values(column)));
		return this;

	}

	// endregion

	// region ignore

	/* ignore */
	private boolean ignore = false;

	/**
	 * ignore
	 *
	 * @return	InsertBuilder
	 */
	public InsertBuilder ignore() {

		this.ignore = true;
		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String sql() {

		StringBuilder sb = new StringBuilder();

		// INSERT句
		sb.append("INSERT ");
		if (this.ignore) {
			sb.append("IGNORE ");
		}
		sb.append("INTO ");

		// テーブル
		sb.append("`");
		sb.append(table.name());
		sb.append("`");

		// COLUMN句
		sb.append(" (");
		if (!columnList.isEmpty()) {
			for (int i = 0; i < columnList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(columnList.get(i).name());
			}
		} else {
			for (int i = 0; i < valueList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				valueList.get(i).columnSql(sb);
			}
		}
		sb.append(")");

		// VALUE句
		if (selectBuilder != null) {
			sb.append(" ");
			sb.append(selectBuilder.sql());
		} else {
			sb.append(" VALUES (");
			for (int i = 0; i < valueList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				valueList.get(i).valueSql(sb);
			}
			sb.append(")");
		}

		// ON DUPLICATE KEY UPDATE句
		if (!setList.isEmpty()) {
			sb.append(" ON DUPLICATE KEY UPDATE ");
			boolean isFirst = true;
			for (ISet set : setList) {
				if (!isFirst) {
					sb.append(", ");
				}
				isFirst = false;
				set.setSql(sb);
			}
		}

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> params() {

		List<Object> params = new ArrayList<>();

		// value
		for (IValue value : valueList) {
			if (value.hasParameter()) {
				params.add(value.getParameter());
			}
		}

		// select
		if (selectBuilder != null) {
			params.addAll(selectBuilder.params());
		}

		// set
		for (ISet set : setList) {
			if (set.hasParameter()) {
				params.add(set.getParameter());
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InsertBuilder apply(Data data) {

		value(data);
		return this;

	}

}
