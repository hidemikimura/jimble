package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 日時を epoch 秒にする（要件 F-D-31）
 */
public class UnixTimestamp extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public UnixTimestamp (Object value) {

		super(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().unixTimestamp(sb.builder(), writer(sb, 0));

	}

}
