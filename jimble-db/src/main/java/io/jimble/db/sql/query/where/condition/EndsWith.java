package io.jimble.db.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * LIKE(%?)
 */
public class EndsWith implements ICondition {

	/* 値 */
	private Object value;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public EndsWith(Object value) {

		this.value = "%" + String.valueOf(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql (SqlWriter sb) {

		sb.append(" LIKE ");
		if (value instanceof IColumn column) {
			sb.qualified(column);
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else {
			sb.append("?");
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value instanceof IColumn column) {
			return false;
		} else if (value instanceof IDsl dsl) {
			return dsl.hasParameter();
		} else if (value instanceof ISelect select) {
			return select.hasParameter();
		} else {
			return true;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		if (value instanceof IDsl dsl) {
			return dsl.getParameter();
		} else if (value instanceof ISelect select) {
			return select.getParameter();
		} else {
			return value;
		}

	}

}
