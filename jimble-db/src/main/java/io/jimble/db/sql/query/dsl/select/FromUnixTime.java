package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * epoch 秒を日時にする（要件 F-D-31）
 */
public class FromUnixTime extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public FromUnixTime (Object value) {

		super(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().fromUnixTime(sb.builder(), writer(sb, 0));

	}

}
