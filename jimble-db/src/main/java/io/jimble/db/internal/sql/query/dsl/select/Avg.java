package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * avg
 */
public class Avg implements IDsl {

	/* select */
	private ISelect select = null;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 */
	public Avg(ISelect select) {

		this.select = select;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.function(SqlFunction.AVG).append('(');
		select.selectSql(sb);
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return select.hasParameter();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return select.getParameter();

	}

}
