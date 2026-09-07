package io.jimble.db.sql.query.where.condition;

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
	public void conditionSql(StringBuilder sb) {

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
