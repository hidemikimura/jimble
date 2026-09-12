package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 部分文字列の位置（1から。無ければ 0。要件 F-D-31）
 *
 * <p>
 * <b>引数の並びが製品で逆。</b>MySQL は {@code LOCATE(探すもの, 対象)}、
 * PostgreSQL は {@code STRPOS(対象, 探すもの)}。
 * </p>
 */
public class Locate extends AbstractFunction {

	/**
	 * コンストラクタ
	 *
	 * @param needle	探すもの
	 * @param haystack	対象
	 */
	public Locate (Object needle, Object haystack) {

		super(needle, haystack);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().locate(sb.builder(), writer(sb, 0), writer(sb, 1));

	}

}
