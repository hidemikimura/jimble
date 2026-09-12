package io.jimble.db.internal.sql.query.dsl.where;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractFunction;

/**
 * 正規表現に当たるか（要件 F-D-31）
 *
 * <p>
 * MySQL は {@code x REGEXP ?}、PostgreSQL は {@code x ~ ?}
 * （大文字小文字を無視するときは {@code ~*}）。
 * </p>
 *
 * <p>
 * <b>正規表現の方言までは揃わない。</b>
 * MySQL 8 は ICU、PostgreSQL は POSIX の拡張正規表現である。
 * {@code ^} {@code $} {@code []} {@code +} {@code *} のような素直な書き方は同じだが、
 * {@code \d} や {@code \w} のような略記は<b>片方でしか効かない</b>ことがある。
 * 両方で使うなら {@code [0-9]} のように文字クラスで書くこと。
 * </p>
 */
public class Regexp extends AbstractFunction {

	/* 大文字小文字を無視するか */
	private final boolean ignoreCase;

	/**
	 * コンストラクタ
	 *
	 * @param value			値
	 * @param pattern		正規表現
	 * @param ignoreCase	大文字小文字を無視するなら true
	 */
	public Regexp (Object value, String pattern, boolean ignoreCase) {

		super(value, pattern);
		this.ignoreCase = ignoreCase;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().regexp(sb.builder(), writer(sb, 0), ignoreCase);

	}

}
