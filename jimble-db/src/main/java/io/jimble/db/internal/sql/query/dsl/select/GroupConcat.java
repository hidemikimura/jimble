package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 集めて1つの文字列にする（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code GROUP_CONCAT(x SEPARATOR ',')}、
 * PostgreSQL は {@code STRING_AGG(x::text, ',')}。
 * </p>
 */
public class GroupConcat extends AbstractFunction {

	/* 区切り文字 */
	private final String separator;

	/* 重複を除くか */
	private final boolean distinct;

	/**
	 * コンストラクタ
	 *
	 * @param value		値
	 * @param separator	区切り文字
	 * @param distinct	重複を除くなら true
	 */
	public GroupConcat (Object value, String separator, boolean distinct) {

		super(value);
		this.separator = separator;
		this.distinct = distinct;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().groupConcat(sb.builder(), writer(sb, 0), separator, distinct);

	}

}
