package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 日付の差（日数。要件 F-D-31）
 *
 * <p>{@code from - to} の日数。</p>
 */
public class DateDiff extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param from	引かれるほう
	 * @param to	引くほう
	 */
	public DateDiff (Object from, Object to) {

		super(from, to);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().dateDiffDays(sb.builder(), writer(sb, 0), writer(sb, 1));

	}

}
