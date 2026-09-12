package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 最初の NULL でない値（要件 F-D-31）
 *
 * <p>
 * 名前も意味も両製品で同じなので、{@code COALESCE} をそのまま書く。
 * 2つだけなら {@code Dsl.ifnull} でもよい（MySQL では {@code IFNULL} になる）。
 * </p>
 */
public class Coalesce extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param values	値
	 */
	public Coalesce (Object...values) {

		super(values);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append("COALESCE(");
		writeArgs(sb);
		sb.append(')');

	}

}
