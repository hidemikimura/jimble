package io.jimble.db.internal.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
/**
 * is not null
 */
public class IsNotNull implements ICondition {

	/**
	 * コンストラクタ
	 */
	public IsNotNull() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql (SqlWriter sb) {

		sb.append(" IS NOT NULL");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return null;

	}

}
