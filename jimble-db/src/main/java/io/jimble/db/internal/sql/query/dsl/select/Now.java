package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * now
 */
public class Now implements IDsl {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.function(SqlFunction.NOW).append("()");

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
