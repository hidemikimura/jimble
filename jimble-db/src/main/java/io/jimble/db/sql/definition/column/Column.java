package io.jimble.db.sql.definition.column;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.IColumn;

import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.internal.sql.query.order_by.IOrderBy;
import io.jimble.db.internal.sql.query.order_by.OrderByQuery;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

/**
 * 列定義
 */
@CheckReturnValue
public class Column implements IColumn, ISelect, IWhere, IOrderBy {

	/* 区切り文字(JSで扱い易いように変数で利用できる文字にする) */
	public static final String SPLITTER = "__";

	// region IColumn

	/* テーブル */
	private final ITable table;

	/* 列名 */
	private final String name;

	/* クラス */
	private final Class<?> clazz;

	/* null許容 */
	private final boolean isNullable;

	/* PK判定 */
	private final boolean isPrimaryKey;

	/* デフォルト値 */
	private final Object defaultValue;

	/* 結合名 */
	private final String joinName;

	/* URLパスプレースホルダー */
	private final String urlPathPlaceholder;

	/**
	 * コンストラクタ
	 *
	 * @param table		テーブル
	 * @param name		列名
	 * @param clazz     型
	 */
	public Column (ITable table, String name, Class<?> clazz) {

		this.table = table;
		this.name = name;
		this.clazz = clazz;
		this.isNullable = true;
		this.defaultValue = null;
		this.isPrimaryKey = false;
		this.joinName = table.name() + SPLITTER + name;
		this.urlPathPlaceholder = "{%s.%s}".formatted(table.name(), name);

	}

	/**
	 * コンストラクタ
	 *
	 * @param table		    テーブル
	 * @param name		    列名
	 * @param clazz         型
	 * @param isNullable    null許容判定
	 * @param defaultValue  デフォルト値
	 */
	public Column (ITable table, String name, Class<?> clazz, boolean isNullable, Object defaultValue) {

		this.table = table;
		this.name = name;
		this.clazz = clazz;
		this.isNullable = isNullable;
		this.defaultValue = defaultValue;
		this.isPrimaryKey = false;
		this.joinName = table.name() + SPLITTER + name;
		this.urlPathPlaceholder = "{%s.%s}".formatted(table.name(), name);

	}

	/**
	 * コンストラクタ
	 *
	 * @param table		    テーブル
	 * @param name		    列名
	 * @param clazz         型
	 * @param isNullable    null許容判定
	 * @param defaultValue  デフォルト値
	 * @param isPrimaryKey  PK判定
	 */
	public Column (ITable table, String name, Class<?> clazz, boolean isNullable, Object defaultValue, boolean isPrimaryKey) {

		this.table = table;
		this.name = name;
		this.clazz = clazz;
		this.isNullable = isNullable;
		this.defaultValue = defaultValue;
		this.isPrimaryKey = isPrimaryKey;
		this.joinName = table.name() + SPLITTER + name;
		this.urlPathPlaceholder = "{%s.%s}".formatted(table.name(), name);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ITable table() {

		return this.table;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name() {

		return this.name;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> clazz() {

		return this.clazz;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isNullable() {

		return this.isNullable;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object defaultValue() {

		return this.defaultValue;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isPrimaryKey() {

		return this.isPrimaryKey;

	}

	// endregion

	// region ISelect

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect as(String as) {

		return new SelectQuery().select(this).as(as);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect dsl(IDsl dsl) {

		return new SelectQuery().dsl(dsl);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect plus(Object value) {

		return new SelectQuery().select(this).plus(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {

		return new SelectQuery().select(this).minus(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {

		return new SelectQuery().select(this).multiply(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect divide(Object value) {

		return new SelectQuery().select(this).divide(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectSql (SqlWriter sb) {

		sb.append(rendered(sb.dialect()).qualified());

	}

	// endregion

	// region IWhere

	/**
	 * {@inheritDoc}
	 *
	 * @deprecated 列そのものは条件ではない（呼ぶと例外）。比較を作ってから {@code and(...)} する。
	 *             {@code IWhere} の約束なので消せない——列の型で呼んだときにコンパイラが知らせるよう、非推奨のまま残す（要件 D-194）
	 */
	@Override
	@Deprecated(since = "1.5.0", forRemoval = true)
	public IWhere and(IWhere where) {

		/*
		 * 列そのものは条件ではない。1.4 までは null を返していたので、
		 * where(...) に null が入って<b>離れた所で NPE</b> になっていた（要件 D-190）。
		 */
		throw new io.jimble.db.sql.SqlBuildException(
			"列に直接 and(...) は付けられません。先に比較を作ってください（例: col.eq(1).and(other.eq(2))）");

	}

	/**
	 * {@inheritDoc}
	 *
	 * @deprecated 列そのものは条件ではない（呼ぶと例外）。比較を作ってから {@code or(...)} する。
	 *             {@code IWhere} の約束なので消せない——列の型で呼んだときにコンパイラが知らせるよう、非推奨のまま残す（要件 D-194）
	 */
	@Override
	@Deprecated(since = "1.5.0", forRemoval = true)
	public IWhere or(IWhere where) {

		/*
		 * 列そのものは条件ではない。1.4 までは null を返していたので、
		 * where(...) に null が入って<b>離れた所で NPE</b> になっていた（要件 D-190）。
		 */
		throw new io.jimble.db.sql.SqlBuildException(
			"列に直接 or(...) は付けられません。先に比較を作ってください（例: col.eq(1).or(other.eq(2))）");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere eq(Object value) {

		return new WhereQuery(this).eq(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not(Object value) {

		return new WhereQuery(this).not(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere gt(Object value) {

		return new WhereQuery(this).gt(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere lt(Object value) {

		return new WhereQuery(this).lt(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ge(Object value) {

		return new WhereQuery(this).ge(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere le(Object value) {

		return new WhereQuery(this).le(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_null() {

		return new WhereQuery(this).is_null();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_not_null() {

		return new WhereQuery(this).is_not_null();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere between(Object value1, Object value2) {

		return new WhereQuery(this).between(value1, value2);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere like(Object value) {

		return new WhereQuery(this).like(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_like(Object value) {

		return new WhereQuery(this).not_like(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere contains(Object value) {

		return new WhereQuery(this).contains(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere starts_with(Object value) {

		return new WhereQuery(this).starts_with(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ends_with(Object value) {

		return new WhereQuery(this).ends_with(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere in(Object value) {

		return new WhereQuery(this).in(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_in(Object value) {

		return new WhereQuery(this).not_in(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere exists(Object value) {

		return new WhereQuery(this).exists(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String logicalOperator() {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void whereSql (SqlWriter sb) {

		sb.append(rendered(sb.dialect()).qualified());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return null;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 列そのものにパラメータは無い。
	 * 継承して {@code hasParameter()} / {@code getParameter()} を上書きした列（計算列など）は、既定の形で集める。
	 * </p>
	 */
	@Override
	public void collectParameters (java.util.List<Object> out) {

		if (getClass() != Column.class && getClass() != TemporaryColumn.class) {
			io.jimble.db.sql.query.ParameterSource.collectByGetParameter(this, out);
		}

	}

	// endregion

	// region IOrderBy

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy asc() {

		return new OrderByQuery(this).asc();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOrderBy desc() {

		return new OrderByQuery(this).desc();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void orderBySql (SqlWriter sb) {

		sb.append(rendered(sb.dialect()).qualified());

	}

	// endregion


	// region join select名

	/**
	 * join select名を取得する
	 *
	 * @return  join select名
	 */
	public String getJoinSelectName () {

		return this.joinName;

	}

	// region 書き出した字面（2.7.5）

	/**
	 * 方言で書き出した字面
	 *
	 * @param dialect	方言
	 * @param qualified	テーブル修飾つきの列（{@code `t`.`c`}）
	 * @param alias		SELECT 句の別名（{@code `t__c`}）
	 */
	private record Rendered (Dialect dialect, String qualified, String alias) {}

	/*
	 * <b>列の字面は方言が同じなら毎回同じなので、1度だけ作る</b>（2.7.5）。
	 * 列はどれも final で、作ったあと変わらない。SELECT・WHERE・ORDER BY のたびに
	 * 識別子を囲み直していて、主キー 1 行の SELECT を組み立てる CPU の 3 分の 1 ほどを使っていた。
	 * 方言はふつう 1 つなので、最後に使った方言の分だけ持つ（record 1 つを volatile で入れ替える）
	 */
	private volatile Rendered rendered;

	/**
	 * 方言で書き出した字面
	 *
	 * @param dialect	方言
	 * @return	字面
	 */
	private Rendered rendered (Dialect dialect) {

		Rendered current = rendered;

		if (current != null && current.dialect() == dialect) {
			return current;
		}

		SqlWriter qualified = new SqlWriter(dialect);
		qualified.qualified(table() == null ? null : table().name(), name);

		SqlWriter alias = new SqlWriter(dialect);
		alias.identifier(joinName);

		Rendered created = new Rendered(dialect, qualified.toString(), alias.toString());
		rendered = created;

		return created;

	}

	/**
	 * SELECT 句に書く形（{@code `t`.`c` AS `t__c`}）
	 *
	 * <p>{@code select()} で列を並べないとき、テーブルの全列をこの形で書く。</p>
	 *
	 * @param sb	書き出し先
	 */
	public void selectAsJoinNameSql (SqlWriter sb) {

		Rendered current = rendered(sb.dialect());

		sb.append(current.qualified()).append(" AS ").append(current.alias());

	}

	/**
	 * 別名（{@code `t__c`}）を書く
	 *
	 * @param sb	書き出し先
	 */
	public void joinNameSql (SqlWriter sb) {

		sb.append(rendered(sb.dialect()).alias());

	}

	// endregion

	// endregion

	// region URLパスパラメータプレースホルダー

	/**
	 * URLパスパラメータプレースホルダー
	 *
	 * @return  URLパスパラメータプレースホルダー
	 */
	public String urlPathPlaceholder () {

		return this.urlPathPlaceholder;

	}

	// endregion


	/**
	 * 同じ列か（D-175）
	 *
	 * <p>
	 * <b>テーブルと列名が同じなら同じ列である。</b>型も null 許容も PK かどうかも見ない——
	 * それらは<b>同じ列の性質</b>であって、どの列かを決めるものではない。
	 * </p>
	 *
	 * <p>
	 * インスタンスが同じかどうかを見ないのは {@link io.jimble.db.sql.definition.table.Table} と同じ理由である（D-174）。
	 * </p>
	 *
	 * @param other	比べる相手
	 * @return	同じ列なら true
	 */
	@Override
	public boolean equals (Object other) {

		if (this == other) {
			return true;
		}

		if (!(other instanceof Column that)) {
			return false;
		}

		return java.util.Objects.equals(table(), that.table())
			&& java.util.Objects.equals(name(), that.name());

	}

	@Override
	public int hashCode () {

		return java.util.Objects.hash(table(), name());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {

		if (table() == null) {
			return name();
		}

		return table().name() + "." + name();

	}

	/**
	 * カスタムテーブル
	 *
	 * @param tableName	テーブル名
	 * @return	カスタムカラム
	 */
	public Column customTable (String tableName) {

		return new TemporaryColumn(tableName, name());

	}

}
