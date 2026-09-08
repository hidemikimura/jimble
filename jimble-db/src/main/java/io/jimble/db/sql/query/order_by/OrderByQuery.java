package io.jimble.db.sql.query.order_by;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.db.sql.query.select.ISelect;

/**
 * order by
 */
public class OrderByQuery implements IOrderBy {

	/* ISelect */
	private ISelect select = null;

	/* order */
	private String order = "ASC";

	/**
	 * コンストラクタ
	 *
	 * @param select	ISelect
	 */
	public OrderByQuery(ISelect select) {

		this.select = select;

	}

	/**
	 * コンストラクタ
	 *
	 * @param order	order
	 */
	public OrderByQuery(String order) {

		this.select = new TemporaryColumn("", order);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy asc() {

		this.order = "ASC";
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy desc() {

		this.order = "DESC";
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void orderBySql (SqlWriter sb) {

		this.select.selectSql(sb);
		sb.append(" ");
		sb.append(this.order);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return this.select.hasParameter();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return this.select.getParameter();

	}

}
