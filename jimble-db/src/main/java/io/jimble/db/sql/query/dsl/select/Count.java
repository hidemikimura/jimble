package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * count
 */
public class Count implements IDsl {

	/* select */
	private ISelect select = null;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 */
	public Count (ISelect select) {

		this.select = select;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("COUNT(");
		if (select == null) {
			sb.append("*");
		} else {
			select.selectSql(sb);
		}
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (select == null) {
			return false;
		} else {
			return select.hasParameter();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		if (select == null) {
			return null;
		} else {
			return select.getParameter();
		}

	}

}
