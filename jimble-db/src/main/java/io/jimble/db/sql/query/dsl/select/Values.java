package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * avg
 */
public class Values implements IDsl {

	/* select */
	private IColumn column = null;

	/**
	 * コンストラクタ
	 *
	 * @param column	column
	 */
	public Values(IColumn column) {

		this.column = column;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		/*
		 * 入れようとした値を指す書き方は製品で違う（要件 F-D-30）。
		 * MySQL は VALUES(col)、PostgreSQL は EXCLUDED.col。
		 */
		sb.dialect().insertedValue(sb.builder()
			, column.table() == null ? null : column.table().name(), column.name());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return null;

	}

}
