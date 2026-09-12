package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 今日の日付（要件 F-D-31）
 */
public class CurrentDate extends AbstractFunction {

	/**
	 * コンストラクタ
	 */
	public CurrentDate () {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append(sb.dialect().currentDate());

	}

}
