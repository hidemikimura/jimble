package io.jimble.db.sql.query.select;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;

import java.util.ArrayList;
import java.util.List;

/**
 * select
 */
public class SelectQuery implements ISelect {

	/* ISelect */
	private ISelect select = null;

	/**
	 * ISelect
	 *
	 * @param select	ISelect
	 * @return	ISelect
	 */
	public ISelect select(ISelect select) {

		this.select = select;
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

	/* DSL */
	private IDsl dsl = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect dsl(IDsl dsl) {

		this.dsl = dsl;
		return this;

	}

	/* plus */
	private Object plus = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect plus(Object value) {

		this.plus = value;
		return this;

	}

	/* minus */
	private Object minus = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {

		this.minus = value;
		return this;

	}

	/* multiply */
	private Object multiply = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {

		this.multiply = value;
		return this;

	}

	/* subtract */
	private Object subtract = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect subtract(Object value) {

		this.subtract = value;
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectSql(StringBuilder sb) {

		if (this.dsl == null) {
			this.select.selectSql(sb);
		} else {
			this.dsl.dslSql(sb);
		}

		Object basicCalcOperation = null;
		if (plus != null) {
			sb.append(" + ");
			basicCalcOperation = plus;
		} else if (minus != null) {
			sb.append(" - ");
			basicCalcOperation = minus;
		} else if (multiply != null) {
			sb.append(" * ");
			basicCalcOperation = multiply;
		} else if (subtract != null) {
			sb.append(" / ");
			basicCalcOperation = subtract;
		}
		if (basicCalcOperation != null) {
			if (basicCalcOperation instanceof IColumn column) {
				sb.append("`");
				sb.append(column.table().name());
				sb.append("`.`");
				sb.append(column.name());
				sb.append("`");
			} else if (basicCalcOperation instanceof IDsl d) {
				d.dslSql(sb);
			} else if (basicCalcOperation instanceof ISelect s) {
				s.selectSql(sb);
			} else {
				sb.append("?");
			}
		}

		if (this.dsl == null && this.select instanceof Column column && (this.as == null || this.as.isEmpty())) {
			// TODO 不要？
//			sb.append(" AS ");
//			sb.append("`");
//			sb.append(column.getJoinSelectName());
//			sb.append("`");
		} else if (this.as != null && !this.as.isEmpty()) {
			sb.append(" AS ");
			sb.append("`");
			sb.append(this.as);
			sb.append("`");
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (this.dsl == null) {
			if (this.select.hasParameter()) {
				return true;
			}
		} else {
			if (this.dsl.hasParameter()) {
				return true;
			}
		}

		List<Object> params = new ArrayList<>();
		if (this.plus != null) {
			addParam(params, this.plus);
		} else if (this.minus != null) {
			addParam(params, this.minus);
		} else if (this.multiply != null) {
			addParam(params, this.multiply);
		} else if (this.subtract != null) {
			addParam(params, this.subtract);
		}

		return !params.isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		if (this.dsl == null) {
			if (this.select.hasParameter()) {
				params.add(this.select.getParameter());
			}
		} else {
			if (this.dsl.hasParameter()) {
				params.add(this.dsl.getParameter());
			}
		}

		if (this.plus != null) {
			addParam(params, this.plus);
		} else if (this.minus != null) {
			addParam(params, this.minus);
		} else if (this.multiply != null) {
			addParam(params, this.multiply);
		} else if (this.subtract != null) {
			addParam(params, this.subtract);
		}

		return params;

	}

	/**
	 * パラメータ追加
	 *
	 * @param params	パラメータ一覧
	 * @param value		値
	 */
	private void addParam (List<Object> params, Object value) {

		if (value instanceof IDsl d) {
			if (d.hasParameter()) {
				params.add(d.getParameter());
			}
		} else if (value instanceof ISelect s) {
			if (s.hasParameter()) {
				params.add(s.getParameter());
			}
		} else {
			params.add(value);
		}

	}

}
