package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;
import io.jimble.db.dialect.DatePart;

/**
 * 日時から一部を取り出す（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code YEAR(x)}、PostgreSQL は {@code EXTRACT(YEAR FROM x)}。
 * <b>曜日の起点まで揃えてある</b>（{@link DatePart#DAY_OF_WEEK}）。
 * </p>
 */
public class DatePartOf extends AbstractFunction {

	/* 取り出す部分 */
	private final DatePart part;

	/**
	 * コンストラクタ
	 *
	 * @param part	取り出す部分
	 * @param value	値
	 */
	public DatePartOf (DatePart part, Object value) {

		super(value);
		this.part = part;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().datePart(sb.builder(), part, writer(sb, 0));

	}

}
