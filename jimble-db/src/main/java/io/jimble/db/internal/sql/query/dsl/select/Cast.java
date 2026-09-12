package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;
import io.jimble.db.dialect.CastType;

/**
 * 型変換（要件 F-D-31）
 *
 * <p>型名が製品でまるで違うので、{@link CastType} で指定する。</p>
 */
public class Cast extends AbstractFunction {

	/* 行き先の型 */
	private final CastType type;

	/* 全体桁（DECIMAL のみ） */
	private final int precision;

	/* 小数桁（DECIMAL のみ） */
	private final int scale;

	/**
	 * コンストラクタ
	 *
	 * @param value	値
	 * @param type	行き先の型
	 */
	public Cast (Object value, CastType type) {

		this(value, type, 0, 0);

		/*
		 * 桁を書かないと DECIMAL(0,0) / numeric(0,0) になる。
		 * PostgreSQL は「precision must be between 1 and 1000」で落ち、
		 * MySQL は<b>小数を落とした値を黙って返す</b>。製品で答えが変わる。
		 */
		if (type == CastType.DECIMAL) {
			throw new IllegalArgumentException("DECIMAL への変換は桁が要ります。castDecimal を使ってください");
		}

	}

	/**
	 * コンストラクタ
	 *
	 * @param value		値
	 * @param type		行き先の型
	 * @param precision	全体桁（{@link CastType#DECIMAL} のみ）
	 * @param scale		小数桁（{@link CastType#DECIMAL} のみ）
	 */
	public Cast (Object value, CastType type, int precision, int scale) {

		super(value);

		if (type == CastType.DECIMAL && (precision < 1 || scale < 0 || scale > precision)) {
			throw new IllegalArgumentException(
				"DECIMAL の桁がおかしいです: precision=%d scale=%d".formatted(precision, scale));
		}

		this.type = type;
		this.precision = precision;
		this.scale = scale;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().cast(sb.builder(), writer(sb, 0), type, precision, scale);

	}

}
