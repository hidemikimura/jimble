package io.jimble.db.sql.query.dsl.select;

import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * avg
 */
public class Values implements IDsl {

	/* select */
	private IColumn column = null;

	/**
	 * コンストラクタ
	 *
	 * @param column	column
	 */
	public Values(IColumn column) {

		this.column = column;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("VALUES(`");
		sb.append(column.table().name());
		sb.append("`.`");
		sb.append(column.name());
		sb.append("`)");

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
