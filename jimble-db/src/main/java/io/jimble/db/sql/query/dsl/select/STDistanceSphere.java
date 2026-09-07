package io.jimble.db.sql.query.dsl.select;

import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

import java.util.ArrayList;
import java.util.List;

/**
 * ST_Distance_Sphere
 */
public class STDistanceSphere implements IDsl {

	/* 値1 */
	private final Object value1;

	/* 値2 */
	private final Object value2;

	/**
	 * コンストラクタ
	 *
	 * @param value1	値1
	 * @param value2	値2
	 */
	public STDistanceSphere (Object value1, Object value2) {

		this.value1 = value1;
		this.value2 = value2;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql(StringBuilder sb) {

		sb.append("ST_Distance_Sphere(");
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
	 * @param sb	StringBuilder
	 * @param value	値
	 */
	private void output (StringBuilder sb, Object value) {

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
