package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * いまの時刻（要件 F-D-31）
 */
public class CurrentTime extends AbstractFunction {

	/**
	 * コンストラクタ
	 */
	public CurrentTime () {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append(sb.dialect().currentTime());

	}

}
