package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractDsl;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * いまから前後にずらした日時（要件 F-D-30）
 *
 * <p>
 * <b>{@code INTERVAL} の書き方は製品で違う。</b>
 * MySQL は {@code CURRENT_TIMESTAMP + INTERVAL - ? SECOND} と書けるが、
 * PostgreSQL の {@code INTERVAL} は<b>リテラルしか取らない</b>ので
 * {@code CURRENT_TIMESTAMP - (? * INTERVAL '1 SECOND')} になる。
 * </p>
 *
 * <p>
 * 移送元は 14 個のメソッドがそれぞれ SQL 文字列を持っていた。
 * <b>同じ形が 14 個あると、直すときに1つ取りこぼす。</b>ここ1つにまとめた。
 * </p>
 */
public class IntervalFromNow extends AbstractDsl implements IDsl {

	/* 単位 */
	private final String unit;

	/* 前にずらすか */
	private final boolean ago;

	/* いくつ */
	private final long amount;

	/**
	 * コンストラクタ
	 *
	 * @param unit		単位（{@code SECOND} / {@code MINUTE} / {@code HOUR} / {@code DAY} / {@code WEEK} / {@code MONTH} / {@code YEAR}）
	 * @param ago		前にずらすなら true
	 * @param amount	いくつ
	 */
	public IntervalFromNow (String unit, boolean ago, long amount) {

		this.unit = unit;
		this.ago = ago;
		this.amount = amount;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().intervalFromNow(sb.builder(), unit, ago);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter () {

		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter () {

		return new Object[]{amount};

	}

}
