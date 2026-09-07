package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * truncate
 */
public class Truncate implements IDsl {

	/* select */
	private ISelect select = null;

	/* 桁数 */
	private int digit = 0;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 */
	public Truncate(ISelect select) {

		this.select = select;

	}

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 * @param digit		桁数
	 */
	public Truncate(ISelect select, int digit) {

		this.select = select;
		this.digit = digit;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("TRUNCATE(");
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
