package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * round
 */
public class Round implements IDsl {

	/* select */
	private ISelect select = null;

	/* 桁数 */
	private int digit = 0;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 */
	public Round(ISelect select) {

		this.select = select;

	}

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 * @param digit		桁数
	 */
	public Round(ISelect select, int digit) {

		this.select = select;
		this.digit = digit;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("ROUND(");
		select.selectSql(sb);
		sb.append(", ");
		sb.append(digit);
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
