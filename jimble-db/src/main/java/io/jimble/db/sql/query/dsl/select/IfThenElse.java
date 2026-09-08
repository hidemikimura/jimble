package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;
import io.jimble.db.sql.query.where.IWhere;

/**
 * 条件で分ける（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code IF(c, a, b)}、PostgreSQL に {@code IF} は無いので
 * {@code CASE WHEN c THEN a ELSE b END}。
 * </p>
 */
public class IfThenElse extends AbstractFunction {

	/* 条件 */
	private final IWhere condition;

	/**
	 * コンストラクタ
	 *
	 * @param condition	条件
	 * @param whenTrue	真のときの値
	 * @param whenFalse	偽のときの値
	 */
	public IfThenElse (IWhere condition, Object whenTrue, Object whenFalse) {

		super(whenTrue, whenFalse);
		this.condition = condition;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>条件のぶんを先に並べる。</b>書き出す順は
	 * 条件 → 真のとき → 偽のとき なので、ここもその順にする。
	 * </p>
	 */
	@Override
	protected java.util.List<Object> parameters () {

		java.util.List<Object> params = new java.util.ArrayList<>();

		if (condition.hasParameter()) {
			params.add(condition.getParameter());
		}

		params.addAll(super.parameters());

		return params;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		/*
		 * WhereQuery は先頭に空白を付けて書き出す（WHERE の後ろに置く前提）。
		 * <b>IF( `x` > ?, ...) のように空きができる</b>ので、いったん別に書いて詰める。
		 */
		SqlWriter inner = new SqlWriter(sb.dialect());
		condition.whereSql(inner);
		String conditionSql = inner.toString().trim();

		sb.dialect().ifThenElse(sb.builder()
			, () -> sb.append(conditionSql)
			, writer(sb, 0)
			, writer(sb, 1));

	}

}
