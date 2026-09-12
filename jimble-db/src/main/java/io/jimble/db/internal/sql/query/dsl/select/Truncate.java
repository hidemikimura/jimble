package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
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
	public void dslSql (SqlWriter sb) {

		// PostgreSQL は TRUNC（TRUNCATE は DDL の予約語）
		sb.dialect().truncate(sb.builder(), () -> select.selectSql(sb), digit);

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
