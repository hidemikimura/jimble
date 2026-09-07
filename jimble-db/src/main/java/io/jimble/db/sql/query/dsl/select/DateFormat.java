package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * date_format
 */
public class DateFormat implements IDsl {

	/* format */
	private String format;

	/* select */
	private ISelect select = null;

	/**
	 * コンストラクタ
	 *
	 * @param select	select
	 * @param format	format
	 */
	public DateFormat(ISelect select, String format) {

		this.select = select;
		this.format = format;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("DATE_FORMAT(");
		select.selectSql(sb);
		sb.append(", '");
		sb.append(format);
		sb.append("')");

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
