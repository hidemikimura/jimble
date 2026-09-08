package io.jimble.db.sql.query.dsl;

import io.jimble.db.dialect.SqlWriter;
/**
 * 自由SQL
 */
public class FreeSQL implements IDsl {

	/* SQL */
	private String sql;

	/* パラメータ */
	private Object[] params;

	/**
	 * コンストラクタ
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 */
	public FreeSQL(String sql, Object...params) {
		this.sql = sql;
		this.params = params;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append(sql);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return params != null && params.length > 0;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return params;

	}

}
