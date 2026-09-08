package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

import java.util.ArrayList;
import java.util.List;

/**
 * ST_GeomFromText
 */
public class STGeomFromText implements IDsl {

	/* 値 */
	private final Object value;

	/* SRID */
	private int srId = 4326;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public STGeomFromText(Object value) {

		this.value = value;

	}

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 * @param srId	SRID
	 */
	public STGeomFromText(Object value, int srId) {

		this.value = value;
		this.srId = srId;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.function(SqlFunction.ST_GEOM_FROM_TEXT).append('(');
		output(sb, value);
		sb.append(", ");
		sb.append(srId);
		sb.append(")");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		List<Object> params = new ArrayList<>();
		addParam(params, value);
		return !params.isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		addParam(params, value);
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
