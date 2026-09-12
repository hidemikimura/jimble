package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 日時の差（秒。要件 F-D-31）
 *
 * <p>{@code from - to} の秒数。</p>
 */
public class SecondsBetween extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param from	引かれるほう
	 * @param to	引くほう
	 */
	public SecondsBetween (Object from, Object to) {

		super(from, to);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().dateDiffSeconds(sb.builder(), writer(sb, 0), writer(sb, 1));

	}

}
