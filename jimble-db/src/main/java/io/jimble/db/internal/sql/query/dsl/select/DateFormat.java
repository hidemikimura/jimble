package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
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
	public void dslSql (SqlWriter sb) {

		// 書式の言語が製品で違うので、出し方ごと方言に任せる（要件 F-D-30）
		sb.dialect().dateFormat(sb.builder(), () -> select.selectSql(sb), format);

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
