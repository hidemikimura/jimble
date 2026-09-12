package io.jimble.db.sql;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.Data;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.definition.table.TemporaryTable;
import io.jimble.db.internal.sql.query.from.IFrom;
import io.jimble.db.internal.sql.query.order_by.IOrderBy;
import io.jimble.db.internal.sql.query.order_by.OrderByQuery;
import io.jimble.db.internal.sql.query.parameter.Parameter;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectValue;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.paging.Paging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * select builder
 */
public class SelectBuilder extends AbstractBuilder<SelectBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param select	SELECT句
	 */
	public SelectBuilder(Object...select) {

		this.select(select);

	}

	// region SELECT

	/* SELECT句一覧 */
	private final List<ISelect> selectList = new ArrayList<>();

	/**
	 * SELECT句
	 *
	 * @param select	SELECT句
	 * @return	SelectBuilder
	 */
	public SelectBuilder select (Object...select) {

		if (select == null || select.length == 0) {
			return this;
		}

		this.selectList.addAll(expandSelectArray(select));

		return this;

	}

	/**
	 * 引数を展開する
	 *
	 * @param selects   引数
	 * @return  List
	 */
	private List<ISelect> expandSelectArray (Object...selects) {

		List<ISelect> selectList = new ArrayList<>();
		for (Object o : selects) {

			if (o instanceof ISelect select) {
				selectList.add(select);
			} else if (o instanceof List<?> list) {
				for (Object listObject : list) {
					selectList.addAll(expandSelectArray(listObject));
				}
			} else if (o.getClass().isArray()) {
				// 素の配列（long[] など）も通る（要件 D-162）
				for (Object arrayObject : ArrayUtil.toList(o)) {
					selectList.addAll(expandSelectArray(arrayObject));
				}
			} else {
				selectList.add(new SelectValue().value(o));
			}

		}
		return selectList;

	}

	// endregion

	// region FROM

	/* FROM句 */
	private IFrom from = null;

	/**
	 * FROM句
	 *
	 * @param from	FROM句
	 * @return	SelectBuilder
	 */
	public SelectBuilder from(IFrom from) {

		this.from = from;
		return this;

	}

	// endregion

	// region JOIN

	/**
	 * INNER JOIN
	 *
	 * @param from	IFrom
	 * @return	SelectBuilder
	 */
	public SelectBuilder inner(IFrom from) {

		if (this.from == null) {
			this.from = from;
		} else {
			this.from = this.from.inner(from);
		}
		return this;

	}

	/**
	 * LEFT JOIN
	 *
	 * @param from	IFrom
	 * @return	SelectBuilder
	 */
	public SelectBuilder left(IFrom from) {

		if (this.from == null) {
			this.from = from;
		} else {
			this.from = this.from.left(from);
		}
		return this;

	}

	/**
	 * ON
	 *
	 * @param where	IWhere
	 * @return	SelectBuilder
	 */
	public SelectBuilder on(IWhere where) {

		if (this.from == null) {
			return this;
		}

		this.from.on(where);
		return this;

	}

	// endregion

	// region WHERE

	/* WHERE句 */
	private final List<IWhere> whereList = new ArrayList<>();

	/**
	 * WHERE句を削除する
	 *
	 * @return  WHERE句を削除する
	 */
	public SelectBuilder clearWhere() {

		whereList.clear();
		return this;

	}

	/**
	 * WHERE句
	 *
	 * @param where	WHERE句
	 * @return	SelectBuilder
	 */
	public SelectBuilder where(IWhere...where) {

		if (where == null || where.length == 0) {
			return this;
		}

		this.whereList.addAll(Arrays.asList(where));
		return this;

	}

	/**
	 * WHERE句
	 *
	 * @param where where JSON { q: { table_name.column_name|condition(|option): value } }
	 * @return  SelectBuilder
	 */
	public SelectBuilder where (Data where) {

		List<IWhere> whereList = whereList(where);
		if (whereList != null) {
			for (IWhere w : whereList) {
				where(w);
			}
		}

		return this;

	}

	// endregion

	// region GROUP BY

	/* GROUP BY句一覧 */
	private final List<ISelect> groupByList = new ArrayList<>();

	/**
	 * GROUP BY句
	 *
	 * @param groupBy	GROUP BY句
	 * @return	SelectBuilder
	 */
	public SelectBuilder groupBy (ISelect...groupBy) {

		if (groupBy == null || groupBy.length == 0) {
			return this;
		}

		this.groupByList.addAll(Arrays.asList(groupBy));

		return this;

	}

	// endregion

	// region HAVING

	/* HAVING句 */
	private final List<IWhere> havingList = new ArrayList<>();

	/**
	 * HAVING句を削除する
	 *
	 * @return  SelectBuilder
	 */
	public SelectBuilder clearHaving () {

		this.havingList.clear();
		return this;

	}

	/**
	 * HAVING句
	 *
	 * @param having	HAVING句
	 * @return	SelectBuilder
	 */
	public SelectBuilder having(IWhere...having) {

		if (having == null || having.length == 0) {
			return this;
		}

		this.havingList.addAll(Arrays.asList(having));
		return this;

	}

	// endregion

	// region ORDER BY

	/* ORDER BY句一覧 */
	private final List<IOrderBy> orderByList = new ArrayList<>();

	/**
	 * ORDER BY句
	 *
	 * @param orderBy	ORDER BY句
	 * @return	SelectBuilder
	 */
	public SelectBuilder orderBy (IOrderBy...orderBy) {

		if (orderBy == null || orderBy.length == 0) {
			return this;
		}

		this.orderByList.addAll(Arrays.asList(orderBy));

		return this;

	}

	/**
	 * ORDER BY句
	 *
	 * @param data data JSON { order: { table_name: { column_name: asc | desc } } }
	 * @return  SelectBuilder
	 */
	public SelectBuilder orderBy (Data data) {

		if (!data.containsKey("order")) {
			return this;
		}
		Data orderData = data.getData("order");

		for (String tableName : orderData.keySet()) {

			TemporaryTable table = new TemporaryTable(tableName);

			Data tableData = orderData.getDataOptional(tableName);
			for (String columnName : tableData.keySet()) {

				TemporaryColumn column = new TemporaryColumn(table, columnName);
				String orderBy = tableData.getString(columnName);

				if ("desc".equalsIgnoreCase(orderBy)) {
					orderBy(column.desc());
				} else {
					orderBy(column.asc());
				}

			}

		}

		return this;

	}

	/**
	 * SELECT句
	 *
	 * @param select	SELECT句
	 * @return	SelectBuilder
	 */
	public SelectBuilder orderByAsc (ISelect...select) {

		if (select == null) {
			return this;
		}

		for (ISelect s : select) {
			this.orderByList.add(new OrderByQuery(s).asc());
		}

		return this;

	}

	/**
	 * SELECT句
	 *
	 * @param select	SELECT句
	 * @return	SelectBuilder
	 */
	public SelectBuilder orderByDesc (ISelect...select) {

		if (select == null) {
			return this;
		}

		for (ISelect s : select) {
			this.orderByList.add(new OrderByQuery(s).desc());
		}

		return this;

	}

	// endregion

	// region LIMIT,OFFSET

	/* LIMIT */
	private long limit = -1;

	/**
	 * LIMIT
	 *
	 * @param limit	LIMIT
	 * @return	SelectBuilder
	 */
	public SelectBuilder limit (long limit) {

		this.limit = limit;
		return this;

	}

	/* OFFSET */
	private long offset = -1;

	/**
	 * OFFSET
	 *
	 * @param offset	OFFSET
	 * @return	SelectBuilder
	 */
	public SelectBuilder offset (long offset) {

		this.offset = offset;
		if (this.offset < 0) {
			this.offset = 0;
		}
		return this;

	}

	/* ページング */
	private Paging paging = null;

	/**
	 * ページング
	 *
	 * @param paging    Paging
	 * @return  SelectBuilder
	 */
	public SelectBuilder paging (Paging paging) {

		this.paging = paging;

		if (!paging.perAll()) {
			limit(paging.per());
			offset(paging.start() - 1);
		}

		return this;

	}

	/**
	 * ページング情報を取得する
	 *
	 * @return  ページング情報
	 */
	public Paging paging () {

		return paging;

	}

	// endregion

	// region FOR UPDATE

	/* FOR UPDATE */
	private boolean forUpdate = false;

	/**
	 * FOR UPDATE
	 *
	 * @return	SelectBuilder
	 */
	public SelectBuilder forUpdate () {

		this.forUpdate = true;
		return this;

	}

	// endregion

	// region FOR UPDATE NOWAIT

	/* FOR UPDATE NOWAIT */
	private boolean forUpdateNoWait = false;

	/**
	 * FOR UPDATE NOWAIT
	 *
	 * @return	SelectBuilder
	 */
	public SelectBuilder forUpdateNoWait () {

		this.forUpdateNoWait = true;
		return this;

	}

	// endregion

	// region FOR UPDATE SKIP LOCKED

	/* FOR UPDATE SKIP LOCKED */
	private boolean forUpdateSkipLocked = false;

	/**
	 * FOR UPDATE SKIP LOCKED
	 *
	 * @return	SelectBuilder
	 */
	public SelectBuilder forUpdateSkipLocked () {

		this.forUpdateSkipLocked = true;
		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String sql (io.jimble.db.dialect.Dialect dialect) {

		SqlWriter sb = new SqlWriter(dialect);

		// SELECT
		sqlSelect(sb);

		// FROM
		sqlFrom(sb);

		// WHERE
		sqlWhere(sb);

		// GROUP BY
		sqlGroupBy(sb);

		// HAVING
		sqlHaving(sb);

		// ORDER BY
		sqlOrderBy(sb);

		// LIMIT
		sqlLimitOffset(sb);

		// FOR UPDATE
		sqlForUpdate(sb);

		return sb.toString();

	}

	/**
	 * メインテーブルを取得する
	 *
	 * @return  メインテーブル
	 */
	public Table mainTable () {

		if (from == null) {
			return null;
		}

		return (Table) from.getTableList().getFirst();

	}

	/**
	 * メインテーブル PKカラムを取得する
	 *
	 * @return  メインテーブル PKカラム
	 */
	public Column mainTablePkColumn () {

		Table mainTable = mainTable();
		if (mainTable == null) {
			return null;
		}

		for (Column column : mainTable.getColumnList()) {
			if ("id".equals(column.name())
				|| column.isPrimaryKey()) {
				return column;
			}
		}

		return null;

	}

	/**
	 * IDのみSELECTするSQL
	 *
	 * @return  SQL
	 */
	public String simpleSql () {

		return simpleSql(io.jimble.db.dialect.Dialects.defaultDialect());

	}

	/**
	 * IDのみSELECTするSQL（要件 F-D-30）
	 *
	 * @param dialect	方言
	 * @return  SQL
	 */
	public String simpleSql (io.jimble.db.dialect.Dialect dialect) {

		SqlWriter sb = new SqlWriter(dialect);

		// SELECT
		{
			Column column = mainTablePkColumn();
			if (column != null) {
				select(column);
			}
			if (selectList.isEmpty()) {
				setSelectList();
			}
		}
		sqlSelect(sb);

		// FROM
		sqlFrom(sb);

		// WHERE
		sqlWhere(sb);

		// GROUP BY
		sqlGroupBy(sb);

		// HAVING
		sqlHaving(sb);

		// ORDER BY
		sqlOrderBy(sb);

		// LIMIT
		sqlLimitOffset(sb);

		// FOR UPDATE
		sqlForUpdate(sb);

		return sb.toString();

	}

	/**
	 * 全件数取得SQL
	 *
	 * @return	全件数取得SQL
	 */
	public String rowCountSql () {

		return rowCountSql(io.jimble.db.dialect.Dialects.defaultDialect());

	}

	/**
	 * 全件数取得SQL（要件 F-D-30）
	 *
	 * @param dialect	方言
	 * @return	全件数取得SQL
	 */
	public String rowCountSql (io.jimble.db.dialect.Dialect dialect) {

		SqlWriter sb = new SqlWriter(dialect);

		// SELECT
		sb.append("SELECT COUNT(__count_table.cnt) AS cnt");

		// FROM
		sb.append(" FROM (");

		// SELECT
		sb.append("SELECT 1 AS cnt");

		// FROM
		sqlFrom(sb);

		// WHERE
		sqlWhere(sb);

		// GROUP BY
		sqlGroupBy(sb);

		// HAVING
		sqlHaving(sb);

		sb.append(") __count_table");

		return sb.toString();

	}

	/**
	 * SELECT句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlSelect(SqlWriter sb) {

		sb.append("SELECT");
		if (selectList.isEmpty()) {
			setSelectList();
		}

		for (int i = 0; i < selectList.size(); i++) {
			ISelect select = selectList.get(i);
			if (i > 0) {
				sb.append(",");
			}
			sb.append(" ");
			selectList.get(i).selectSql(sb);
			if (select instanceof Column column) {
				sb.append(" AS ");
				sb.identifier(column.getJoinSelectName());
			}
		}

	}

	/**
	 * 全テーブルのselect句を設定する
	 */
	private void setSelectList () {

		if (from != null) {
			List<ITable> tableList = from.getTableList();
			if (tableList != null) {
				for (ITable iTable : tableList) {
					Table table = (Table) iTable;
					for (Column column : table.getColumnList()) {
						select(column.as(column.getJoinSelectName()));
					}
				}
			}
		}

	}

	/**
	 * FROM句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlFrom(SqlWriter sb) {

		if (from != null) {
			sb.append(" FROM");
			this.from.fromSql(sb);
		}

	}

	/**
	 * WHERE句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlWhere(SqlWriter sb) {

		if (!whereList.isEmpty()) {
			sb.append(" WHERE ");
			for (int i = 0; i < whereList.size(); i++) {
				IWhere where = whereList.get(i);
				if (i > 0) {
					String logicalOperator = where.logicalOperator();
					if (logicalOperator == null || logicalOperator.isEmpty()) {
						sb.append(" AND ");
					} else {
						sb.append(" ");
						sb.append(logicalOperator);
						sb.append(" ");
					}
				}
				sb.append("(");
				where.whereSql(sb);
				sb.append(")");
			}
		}

	}

	/**
	 * GROUP BY句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlGroupBy(SqlWriter sb) {

		if (!groupByList.isEmpty()) {
			sb.append(" GROUP BY");
			for (int i = 0; i < groupByList.size(); i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(" ");
				groupByList.get(i).selectSql(sb);
			}
		}

	}

	/**
	 * HAVING句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlHaving(SqlWriter sb) {

		if (!havingList.isEmpty()) {
			sb.append(" HAVING ");
			for (int i = 0; i < havingList.size(); i++) {
				IWhere where = havingList.get(i);
				if (i > 0) {
					String logicalOperator = where.logicalOperator();
					if (logicalOperator == null || logicalOperator.isEmpty()) {
						sb.append(" AND ");
					} else {
						sb.append(" ");
						sb.append(logicalOperator);
						sb.append(" ");
					}
				}
				sb.append("(");
				where.whereSql(sb);
				sb.append(")");
			}
		}

	}

	/**
	 * ORDER BY句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlOrderBy(SqlWriter sb) {

		if (!orderByList.isEmpty()) {
			sb.append(" ORDER BY ");
			for (int i = 0; i < orderByList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				orderByList.get(i).orderBySql(sb);
			}
		}

	}

	/**
	 * LIMIT句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlLimitOffset(SqlWriter sb) {

		if (limit >= 0) {
			sb.append(" LIMIT ?");
			if (offset >= 0) {
				sb.append(" OFFSET ?");
			}
		}

	}

	/**
	 * FOR UPDATE句SQL
	 *
	 * @param sb	書き出し先
	 */
	private void sqlForUpdate(SqlWriter sb) {

		if (forUpdate) {
			sb.append(" FOR UPDATE");
		} else if (forUpdateNoWait) {
			sb.append(" FOR UPDATE NOWAIT");
		} else if (forUpdateSkipLocked) {
			sb.append(" FOR UPDATE SKIP LOCKED");
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> params() {

		List<Object> params = new ArrayList<>();

		// SELECT
		for (ISelect select : selectList) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
		}

		// FROM
		if (from != null) {
			if (from.hasParameter()) {
				params.add(from.getParameter());
			}
		}

		// WHERE
		for (IWhere where : whereList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		// GROUP BY
		for (ISelect select : groupByList) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
		}

		// HAVING
		for (IWhere where : havingList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		// ORDER BY
		for (IOrderBy orderBy : orderByList) {
			if (orderBy.hasParameter()) {
				params.add(orderBy.getParameter());
			}
		}

		// LIMIT
		if (limit >= 0) {
			params.add(limit);
			if (offset >= 0) {
				params.add(offset);
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * 全件数取得SQLパラメータを取得する
	 */
	public List<Object> rowCountParams() {

		List<Object> params = new ArrayList<>();

		// FROM
		if (from != null) {
			if (from.hasParameter()) {
				params.add(from.getParameter());
			}
		}

		// WHERE
		for (IWhere where : whereList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		// GROUP BY
		for (ISelect select : groupByList) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
		}

		// HAVING
		for (IWhere where : havingList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SelectBuilder apply(Data data) {

		where(data);
		orderBy(data);
		return this;

	}


	// region 内省（要件 F-D-28）

	/**
	 * FROM（結合を含む）
	 *
	 * <p>
	 * <b>組み立てた SQL を、あとから読むための口。</b>
	 * SQL 結果のキャッシュが「どのテーブルに触るか」「結合先が1行に決まるか」を知るのに使う。
	 * </p>
	 *
	 * @return	FROM。指定していなければ null
	 */
	public IFrom from () {

		return from;

	}

	/**
	 * このクエリが触るテーブル（結合を含む）
	 *
	 * @return	テーブル。FROM が無ければ空
	 */
	public List<ITable> tableList () {

		return from == null ? List.of() : from.getTableList();

	}

	/**
	 * WHERE
	 *
	 * @return	WHERE
	 */
	public List<IWhere> whereList () {

		return List.copyOf(whereList);

	}

	/**
	 * 件数を絞っているか（{@code LIMIT} / {@code OFFSET}）
	 *
	 * <p>
	 * <b>絞っていると、行が変わっていなくても中身が変わる。</b>
	 * {@code ORDER BY name LIMIT 1} は、別の行の名前が変わるだけで
	 * 返る行が入れ替わる。SQL 結果のキャッシュはこれを見て安全側に倒す。
	 * </p>
	 *
	 * @return	絞っていれば true
	 */
	public boolean hasRowLimit () {

		return limit >= 0 || offset >= 0;

	}

	// endregion

}
