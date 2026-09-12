package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * SQL にそのまま出す整数（要件 F-D-31）
 *
 * <p>
 * <b>バインドできないところ</b>がある。
 * MySQL の {@code LAG(x, n)} / {@code LEAD(x, n)} / {@code NTILE(n)} の n は
 * <b>リテラルの整数でなければならず</b>、{@code ?} を置くと実行時に弾かれる。
 * </p>
 *
 * <p>
 * 整数しか持てないようにしてあるので、ここから SQL を書き換えられることはない。
 * </p>
 */
public class SqlLiteral implements IDsl {

	/* 値 */
	private final long value;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 */
	public SqlLiteral (long value) {

		this.value = value;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter () {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter () {

		return null;

	}

}
