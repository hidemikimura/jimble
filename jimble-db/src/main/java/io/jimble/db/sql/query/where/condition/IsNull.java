package io.jimble.db.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
/**
 * is null
 */
public class IsNull implements ICondition {

	/**
	 * コンストラクタ
	 */
	public IsNull () {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql (SqlWriter sb) {

		sb.append(" IS NULL");

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
