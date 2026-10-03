package io.jimble.db.internal.sql.query.from;

import io.jimble.db.dialect.IndexHint;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereTerms;
import io.jimble.util.data.definition.ITable;

import java.util.List;

/**
 * インデックスのヒントを付けたテーブル（{@code FROM t FORCE INDEX (i)}。D-265）
 *
 * <p>
 * テーブルとしての中身（列・SQL キャッシュの印など）は元のテーブルのまま。SQL を書くときだけ、
 * テーブル名のあとにヒントを足す。PostgreSQL では書いたところで {@code DialectException}。
 * </p>
 */
public final class IndexHintFrom implements IFrom {

	/* テーブル */
	private final Table table;

	/* 種類 */
	private final IndexHint hint;

	/* インデックスの名前 */
	private final List<String> indexNames;

	/**
	 * 作る
	 *
	 * @param table			テーブル
	 * @param hint			種類
	 * @param indexNames	インデックスの名前（確かめてあるもの）
	 */
	public IndexHintFrom (Table table, IndexHint hint, List<String> indexNames) {

		this.table = table;
		this.hint = hint;
		this.indexNames = List.copyOf(indexNames);

	}

	@Override
	public IFrom inner (IFrom from) {

		return new FromQuery(this).inner(from);

	}

	@Override
	public IFrom left (IFrom from) {

		return new FromQuery(this).left(from);

	}

	@Override
	public IFrom on (IWhere... where) {

		return new FromQuery(this).on(where);

	}

	@Override
	public void fromSql (SqlWriter sb) {

		table.fromSql(sb);
		sb.dialect().indexHint(sb.builder(), hint, indexNames);

	}

	@Override
	public boolean hasParameter () {

		return table.hasParameter();

	}

	@Override
	public Object getParameter () {

		return table.getParameter();

	}

	@Override
	public List<ITable> getTableList () {

		return table.getTableList();

	}

	@Override
	public void onTerms (WhereTerms terms) {

		table.onTerms(terms);

	}

}
