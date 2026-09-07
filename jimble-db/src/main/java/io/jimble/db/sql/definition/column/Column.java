package io.jimble.db.sql.definition.column;

import io.jimble.util.data.definition.IColumn;

import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.order_by.IOrderBy;
import io.jimble.db.sql.query.order_by.OrderByQuery;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

/**
 * 列定義
 */
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
	public ISelect subtract(Object value) {

		return new SelectQuery().select(this).subtract(value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectSql(StringBuilder sb) {

		if (table() != null && !table().name().isEmpty()) {
			sb.append("`");
			sb.append(table().name());
			sb.append("`.");
		}
		sb.append("`");
		sb.append(name);
		sb.append("`");

	}

	// endregion

	// region IWhere

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere and(IWhere where) {

		return null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere or(IWhere where) {

		return null;

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
	public void whereSql(StringBuilder sb) {

		if (table() != null && !table().name().isEmpty()) {
			sb.append("`");
			sb.append(table().name());
			sb.append("`.");
		}
		sb.append("`");
		sb.append(name);
		sb.append("`");

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
	public void orderBySql(StringBuilder sb) {

		if (table() != null && !table().name().isEmpty()) {
			sb.append("`");
			sb.append(table().name());
			sb.append("`.");
		}
		sb.append("`");
		sb.append(name);
		sb.append("`");

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
