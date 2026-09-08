package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 日時から日付だけを取り出す（要件 F-D-31）
 */
public class ToDate extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public ToDate (Object value) {

		super(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().toDate(sb.builder(), writer(sb, 0));

	}

}
