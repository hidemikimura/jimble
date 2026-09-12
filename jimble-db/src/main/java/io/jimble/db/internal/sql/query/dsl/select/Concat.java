package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

import java.util.ArrayList;
import java.util.List;

/**
 * concat
 */
public class Concat implements IDsl {

	/* 値一覧 */
	private Object[] values;

	/**
	 * コンストラクタ
	 *
	 * @param values	値一覧
	 */
	public Concat(Object...values) {

		this.values = values;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		/*
		 * NULL の扱いが製品で逆なので、出し方ごと方言に任せる（要件 F-D-30）。
		 * MySQL は CONCAT(...)、PostgreSQL は (a || b || ...)。
		 */
		List<Runnable> parts = new ArrayList<>();
		for (Object value : values) {
			parts.add(() -> output(sb, value));
		}

		sb.dialect().concat(sb.builder(), parts);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		List<Object> params = new ArrayList<>();
		for (Object value : values) {
			addParam(params, value);
			if (!params.isEmpty()) {
				return true;
			}
		}
		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		for (Object value : values) {
			addParam(params, value);
		}
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
