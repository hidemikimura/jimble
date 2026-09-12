package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 名前だけが製品で違う関数（要件 F-D-31）
 *
 * <p>
 * {@code NAME(a, b, ...)} の形で、<b>引数の意味も並びも製品で同じ</b>もの。
 * 名前は {@link SqlFunction} が持つ。
 * </p>
 *
 * <p>
 * <b>並びや意味が違うものはここに入れない。</b>
 * {@code LOCATE} と {@code STRPOS} のように引数の順が違うもの、
 * {@code DATE_FORMAT} と {@code to_char} のように書式の言語が違うものは、
 * それぞれの専用クラス（あるいは {@code Dialect} の専用メソッド）にする。
 * </p>
 */
public class NamedFunction extends AbstractFunction {

	/* 関数 */
	private final SqlFunction function;

	/* 引数の前に付ける語（DISTINCT など）。無ければ null */
	private final String prefix;

	/**
	 * コンストラクタ
	 *
	 * @param function	関数
	 * @param args		引数
	 */
	public NamedFunction (SqlFunction function, Object...args) {

		this(function, null, args);

	}

	/**
	 * コンストラクタ
	 *
	 * @param function	関数
	 * @param prefix	引数の前に付ける語（{@code DISTINCT} など）。無ければ null
	 * @param args		引数
	 */
	public NamedFunction (SqlFunction function, String prefix, Object...args) {

		super(args);
		this.function = function;
		this.prefix = prefix;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.function(function).append('(');

		if (prefix != null) {
			sb.append(prefix).append(' ');
		}

		writeArgs(sb);
		sb.append(')');

	}

}
