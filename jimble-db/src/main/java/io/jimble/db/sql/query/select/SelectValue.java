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

		return new SelectQuery().select(this).plus(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {

		return new SelectQuery().select(this).minus(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {

		return new SelectQuery().select(this).multiply(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Deprecated(since = "1.5.0", forRemoval = true)
	@SuppressWarnings("removal")
	public ISelect subtract(Object value) {

		return divide(value);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>1.4 までは四則演算が全部 {@code null} を返していた（要件 D-190）。</p>
	 */
	@Override
	public ISelect divide(Object value) {

		return new SelectQuery().select(this).divide(value);

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
