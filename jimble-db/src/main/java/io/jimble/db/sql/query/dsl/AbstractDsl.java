package io.jimble.db.sql.query.dsl;

import io.jimble.db.sql.query.order_by.IOrderBy;
import io.jimble.db.sql.query.order_by.OrderByQuery;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

/**
 * DSL基底
 */
public abstract class AbstractDsl implements IDsl, ISelect, IWhere, IOrderBy {

	/* ISelect */
	private final ISelect select = new SelectQuery().dsl(this);

	/* IWhere */
	private final IWhere where = new WhereQuery((IDsl) this);

	/* IOrderBy */
	private final IOrderBy orderBy = new OrderByQuery(this);

	// region ISelect

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect as(String as) {
		return select.as(as);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect dsl(IDsl dsl) {
		return select.dsl(dsl);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectSql(StringBuilder sb) {
		select.selectSql(sb);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect plus(Object value) {
		return select.plus(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {
		return select.minus(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {
		return select.multiply(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect subtract(Object value) {
		return select.subtract(value);
	}

	// endregion

	// region IWhere

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere and(IWhere where) {
		return this.where.and(where);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere or(IWhere where) {
		return this.where.or(where);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere eq(Object value) {
		return this.where.eq(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not(Object value) {
		return this.where.not(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere gt(Object value) {
		return this.where.gt(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere lt(Object value) {
		return this.where.lt(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ge(Object value) {
		return this.where.ge(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere le(Object value) {
		return this.where.le(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_null() {
		return this.where.is_null();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_not_null() {
		return this.where.is_not_null();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere between(Object value1, Object value2) {
		return this.where.between(value1, value2);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere like(Object value) {
		return this.where.like(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_like(Object value) {
		return this.where.not_like(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere contains(Object value) {
		return this.where.contains(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere starts_with(Object value) {
		return this.where.starts_with(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ends_with(Object value) {
		return this.where.ends_with(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere in(Object value) {
		return this.where.in(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_in(Object value) {
		return this.where.not_in(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere exists(Object value) {
		return this.where.exists(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String logicalOperator() {
		return this.where.logicalOperator();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void whereSql(StringBuilder sb) {
		this.where.whereSql(sb);
	}

	// endregion

	// region IOrderBy

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy asc() {
		return this.orderBy.asc();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy desc() {
		return this.orderBy.desc();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void orderBySql(StringBuilder sb) {
		this.orderBy.orderBySql(sb);
	}

	// endregion

}
