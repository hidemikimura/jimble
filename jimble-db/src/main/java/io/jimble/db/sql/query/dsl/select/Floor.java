package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * floor
 */
public class Floor implements IDsl {

	/* select */
	private ISelect select = null;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 */
	public Floor(ISelect select) {

		this.select = select;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("FLOOR(");
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
