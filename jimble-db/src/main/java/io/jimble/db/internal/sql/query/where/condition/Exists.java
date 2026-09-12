package io.jimble.db.internal.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * exists
 */
public class Exists implements ICondition {

	/* 値 */
	private Object value;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public Exists(Object value) {

		this.value = value;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql (SqlWriter sb) {

		sb.append(" EXISTS (");
		if (value instanceof SelectBuilder selectBuilder) {
			sb.append(selectBuilder.sql(sb.dialect()));
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value instanceof SelectBuilder selectBuilder) {
			return !selectBuilder.params().isEmpty();
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

		if (value instanceof SelectBuilder selectBuilder) {
			return selectBuilder.params();
		} else if (value instanceof IDsl dsl) {
			return dsl.getParameter();
		} else if (value instanceof ISelect select) {
			return select.getParameter();
		} else {
			return value;
		}

	}

}
