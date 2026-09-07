package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;

/**
 * now
 */
public class Now implements IDsl {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("NOW()");

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
