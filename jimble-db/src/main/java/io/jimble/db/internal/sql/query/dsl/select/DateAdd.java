package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.dialect.DateUnit;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 日時を足す・引く（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code DATE_ADD(x, INTERVAL ? DAY)}、
 * PostgreSQL は {@code (x + (? * INTERVAL '1 DAY'))}。
 * </p>
 */
public class DateAdd extends AbstractFunction {

	/* 単位 */
	private final DateUnit unit;

	/* 引くかどうか */
	private final boolean subtract;

	/**
	 * コンストラクタ
	 *
	 * @param value		値
	 * @param amount	足す数
	 * @param unit		単位
	 * @param subtract	引くなら true
	 */
	public DateAdd (Object value, long amount, DateUnit unit, boolean subtract) {

		super(value, amount);
		this.unit = unit;
		this.subtract = subtract;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().dateAdd(sb.builder(), writer(sb, 0), unit, subtract);

	}

}
