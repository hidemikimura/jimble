package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 2つが同じなら NULL（要件 F-D-31）
 *
 * <p>名前も意味も両製品で同じ。0 除算を避けるのによく使う。</p>
 *
 * <pre>
 * // 分母が 0 なら NULL（エラーにしない）
 * Dsl.value(1).divide(Dsl.nullif(Item.stock, 0))
 * </pre>
 */
public class NullIf extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param value1	値1
	 * @param value2	値2
	 */
	public NullIf (Object value1, Object value2) {

		super(value1, value2);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append("NULLIF(");
		writeArgs(sb);
		sb.append(')');

	}

}
