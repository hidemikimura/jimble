package io.jimble.db.sql.query.value;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * value
 */
public class Value implements IValue {

	/* column */
	private IColumn column = null;

	/* value */
	private Object value = null;

	/**
	 * コンストラクタ
	 *
	 * @param column	column
	 * @param value		value
	 */
	public Value (IColumn column, Object value) {

		this.column = column;
		this.value = value;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IColumn column() {

		return this.column;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object value() {

		return this.value;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void columnSql (SqlWriter sb) {

		sb.append(' ');
		sb.identifier(column.name());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void valueSql (SqlWriter sb) {

		if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value instanceof IDsl dsl) {
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
