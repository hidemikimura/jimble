package io.jimble.db.sql.query.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * select value
 */
public class SelectValue implements ISelect {

	/* value */
	private Object value;

	/**
	 * value
	 *
	 * @param value value
	 * @return  ISelect
	 */
	public ISelect value (Object value) {

		this.value = value;
		return this;

	}

	/* as */
	private String as = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect as(String as) {

		this.as = as;
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect dsl(IDsl dsl) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect plus(Object value) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect subtract(Object value) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectSql (SqlWriter sb) {

		sb.append("?");

		if (this.as != null && !this.as.isEmpty()) {
			sb.append(" AS ");
			sb.identifier(this.as);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return this.value;

	}

}
