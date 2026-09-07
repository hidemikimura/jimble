package io.jimble.db.sql;

import io.jimble.util.data.Data;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.parameter.Parameter;
import io.jimble.db.sql.query.where.IWhere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * delete builder
 */
public class DeleteBuilder extends AbstractBuilder<DeleteBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param table	table
	 */
	public DeleteBuilder (ITable table) {

		this.from = table;

	}

	// region FROM

	/* FROM句 */
	private ITable from = null;

	// endregion

	// region WHERE

	/* WHERE句 */
	private final List<IWhere> whereList = new ArrayList<>();

	/**
	 * WHERE句
	 *
	 * @param where	WHERE句
	 * @return	DeleteBuilder
	 */
	public DeleteBuilder where(IWhere...where) {

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
	 * @return  DeleteBuilder
	 */
	public DeleteBuilder where (Data data) {

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
	public String sql() {

		StringBuilder sb = new StringBuilder();

		// DELETE句
		sb.append("DELETE FROM ");
		sb.append("`");
		sb.append(from.name());
		sb.append("`");

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
	public DeleteBuilder apply(Data data) {

		where(data);
		return this;

	}

}
