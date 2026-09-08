package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

import java.util.ArrayList;
import java.util.List;

/**
 * ifnull
 */
public class IfNull implements IDsl {

	/* 値1 */
	private Object value1;

	/* 値2 */
	private Object value2;

	/**
	 * コンストラクタ
	 *
	 * @param value1    値1
	 * @param value2    値2
	 */
	public IfNull(Object value1, Object value2) {

		this.value1 = value1;
		this.value2 = value2;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.function(SqlFunction.IFNULL).append('(');
		output(sb, value1);
		sb.append(", ");
		output(sb, value2);
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		List<Object> params = new ArrayList<>();
		addParam(params, value1);
		addParam(params, value2);
		return !params.isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		addParam(params, value1);
		addParam(params, value2);
		return params;

	}

	/**
	 * SQL出力
	 *
	 * @param sb	書き出し先
	 * @param value	値
	 */
	private void output (SqlWriter sb, Object value) {

		if (value instanceof IColumn column) {
			sb.qualified(column);
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}

	}

	/**
	 * パラメータ追加
	 *
	 * @param params	パラメータ一覧
	 * @param value		値
	 */
	private void addParam (List<Object> params, Object value) {

		if (value instanceof IDsl dsl) {
			if (dsl.hasParameter()) {
				params.add(dsl.getParameter());
			}
		} else if (value instanceof ISelect select) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
		} else {
			params.add(value);
		}

	}

}
