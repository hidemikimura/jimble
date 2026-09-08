package io.jimble.db.sql;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.Data;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.parameter.Parameter;
import io.jimble.db.sql.query.set.ISet;
import io.jimble.db.sql.query.set.Set;
import io.jimble.db.sql.query.where.IWhere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * update builder
 */
public class UpdateBuilder extends AbstractBuilder<UpdateBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param table	Table
	 */
	public UpdateBuilder (ITable table) {

		this.table = table;

	}

	// region Table

	/* Table */
	private ITable table = null;

	// endregion

	// region Set

	/* set */
	private final List<ISet> setList = new ArrayList<>();

	/**
	 * set
	 *
	 * @param column	column
	 * @param value		value
	 * @return	UpdateBuilder
	 */
	public UpdateBuilder set (IColumn column, Object value) {

		setList.add(new Set(column, value));
		return this;

	}

	/**
	 * VALUE句
	 *
	 * @param data data JSON { set: { table_name: { column_name: value } } }
	 * @return  UpdateBuilder
	 */
	public UpdateBuilder set (Data data) {

		if (!data.containsKey("set")) {
			return this;
		}
		Data setData = data.getData("set");

		if (!setData.containsKey(table.name())) {
			return this;
		}
		Data tableData = setData.getDataOptional(table.name());

		for (String columnName : tableData.keySet()) {
			Object v = tableData.get(columnName);
			if (v instanceof String stringValue) {
				if ("now()".equals(stringValue)) {
					set(new TemporaryColumn(table, columnName), Dsl.now());
				} else {
					set(new TemporaryColumn(table, columnName), stringValue);
				}
			} else {
				set(new TemporaryColumn(table, columnName), v);
			}
		}

		return this;

	}

	// endregion

	// region WHERE

	/* WHERE句 */
	private final List<IWhere> whereList = new ArrayList<>();

	/**
	 * WHERE句
	 *
	 * @param where	WHERE句
	 * @return	UpdateBuilder
	 */
	public UpdateBuilder where(IWhere...where) {

		if (where == null || where.length == 0) {
			return this;
		}

		this.whereList.addAll(Arrays.asList(where));
		return this;

	}

	/**
	 * WHERE句
	 *
	 * @param data data JSON { q: { table_name.column_name|condition: value } }
	 * @return  UpdateBuilder
	 */
	public UpdateBuilder where (Data data) {

		List<IWhere> whereList = whereList(data);
		if (whereList != null) {
			for (IWhere w : whereList) {
				where(w);
			}
		}

		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String sql (io.jimble.db.dialect.Dialect dialect) {

		SqlWriter sb = new SqlWriter(dialect);

		// UPDATE句
		sb.append("UPDATE ");

		// テーブル
		sb.identifier(table.name());

		// SET句
		sb.append(" SET ");
		for (int i = 0; i < setList.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			setList.get(i).setSql(sb);
		}

		// WHERE句
		if (!whereList.isEmpty()) {
			sb.append(" WHERE ");
			for (int i = 0; i < whereList.size(); i++) {
				IWhere where = whereList.get(i);
				if (i > 0) {
					String logicalOperator = where.logicalOperator();
					if (logicalOperator == null || logicalOperator.isEmpty()) {
						sb.append(" AND ");
					} else {
						sb.append(" ");
						sb.append(logicalOperator);
						sb.append(" ");
					}
				}
				sb.append("(");
				where.whereSql(sb);
				sb.append(")");
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

		// set
		for (ISet set : setList) {
			if (set.hasParameter()) {
				params.add(set.getParameter());
			}
		}

		// where
		for (IWhere where : whereList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UpdateBuilder apply(Data data) {

		set(data);
		where(data);
		return this;

	}


	// region 内省（要件 F-D-28）

	/**
	 * 更新するテーブル
	 *
	 * @return	テーブル
	 */
	public ITable table () {

		return table;

	}

	/**
	 * WHERE
	 *
	 * @return	WHERE
	 */
	public List<IWhere> whereList () {

		return List.copyOf(whereList);

	}

	// endregion

}
