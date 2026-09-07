package io.jimble.db.sql.query.where.condition;

import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/**
 * LIKE(%?%)
 */
public class Contains implements ICondition {

	/* 値 */
	private Object value;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public Contains(Object value) {

		this.value = "%" + String.valueOf(value) + "%";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql(StringBuilder sb) {

		sb.append(" LIKE ");
		if (value instanceof IColumn column) {
			sb.append("`");
			sb.append(column.table().name());
			sb.append("`.`");
			sb.append(column.name());
			sb.append("`");
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value instanceof IColumn column) {
			return false;
		} else if (value instanceof IDsl dsl) {
			return dsl.hasParameter();
		} else if (value instanceof ISelect select) {
			return select.hasParameter();
		} else {
			return true;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		if (value instanceof IDsl dsl) {
			return dsl.getParameter();
		} else if (value instanceof ISelect select) {
			return select.getParameter();
		} else {
			return value;
		}

	}

}
