package io.jimble.db.internal.sql.query.where.condition;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

import java.util.Collection;

/**
 * in
 */
public class In implements ICondition {

	/* 値 */
	private Object value;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public In(Object value) {

		this.value = value;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void conditionSql (SqlWriter sb) {

		// 空の一覧のまま書くと IN () になる（要件 F-D-07）
		InValues.check(value, "IN");

		sb.append(" IN (");
		if (value instanceof SelectBuilder selectBuilder) {
			sb.append(selectBuilder.sql(sb.dialect()));
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else if (value instanceof Collection<?> list) {
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append("?");
			}
		} else if (value != null && value.getClass().isArray()) {
			Object[] array = (Object[]) value;
			for (int i = 0; i < array.length; i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append("?");
			}
		} else {
			sb.append("?");
		}
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value instanceof SelectBuilder selectBuilder) {
			return !selectBuilder.params().isEmpty();
		} else if (value instanceof IDsl dsl) {
			return dsl.hasParameter();
		} else if (value instanceof ISelect select) {
			return select.hasParameter();
		} else if (value instanceof Collection<?> list) {
			return !list.isEmpty();
		} else if (value != null && value.getClass().isArray()) {
			Object[] array = (Object[]) value;
			return array.length > 0;
		} else {
			return true;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		if (value instanceof SelectBuilder selectBuilder) {
			return selectBuilder.params();
		} else if (value instanceof IDsl dsl) {
			return dsl.getParameter();
		} else if (value instanceof ISelect select) {
			return select.getParameter();
		} else if (value instanceof Collection<?> list) {
			return list;
		} else if (value != null && value.getClass().isArray()) {
			return value;
		} else {
			return value;
		}

	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String operator () {

		return io.jimble.db.sql.query.where.WhereTerm.IN;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object conditionValue () {

		return value;

	}

}
