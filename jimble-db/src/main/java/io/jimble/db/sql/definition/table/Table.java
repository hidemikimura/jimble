package io.jimble.db.sql.definition.table;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.ITable;

import io.jimble.util.data.Data;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.internal.sql.query.from.FromQuery;
import io.jimble.db.internal.sql.query.from.IFrom;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.log.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * table
 */
public class Table implements ITable, IFrom {

	// region ITable

	/* スキーマ */
	private final ISchema schema;

	/* テーブル名 */
	private final String name;

	/**
	 * コンストラクタ
	 *
	 * @param schema	スキーマ
	 * @param name		テーブル名
	 */
	public Table(ISchema schema, String name) {

		this.schema = schema;
		this.name = name;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISchema schema() {

		return this.schema;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name() {

		return this.name;

	}

	// endregion

	// region IFrom

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom inner(IFrom from) {

		return new FromQuery(this).inner(from);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom left(IFrom from) {

		return new FromQuery(this).left(from);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom on(IWhere...where) {

		return new FromQuery(this).on(where);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void fromSql (SqlWriter sb) {

		sb.append(' ');
		sb.identifier(name);

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
	 */
	@Override
	public List<ITable> getTableList() {

		List<ITable> tableList = new ArrayList<>();
		tableList.add(this);
		return tableList;

	}

	// endregion

	// region 空Data取得

	/**
	 * 空Data取得
	 *
	 * @return  空Data
	 */
	public Data emptyData () {

		Data row = new Data();

		Data data = row.getDataOptional(name());
		for (Column column : getColumnList()) {

			if (column.isNullable()) {
				data.put(column.name(), column.defaultValue());
			} else if (column.defaultValue() == null) {
				if (String.class.equals(column.clazz())) {
					data.put(column.name(), "");
				} else if (int.class.equals(column.clazz())) {
					data.put(column.name(), 0);
				} else if (long.class.equals(column.clazz())) {
					data.put(column.name(), 0L);
				} else if (double.class.equals(column.clazz())) {
					data.put(column.name(), 0D);
				} else if (Date.class.equals(column.clazz())) {
					data.put(column.name(), new Date());
				}
			} else {
				data.put(column.name(), column.defaultValue());
			}

		}

		return row;

	}

	// endregion

	// region 列一覧

	/* 列一覧取得済み判定 */
	private boolean isGetColumnList = false;

	/* 列一覧 */
	private final List<Column> columnList = new ArrayList<>();

	/* 列一覧取得ロック */
	private final ReentrantLock columnLock = new ReentrantLock();

	/**
	 * 列一覧を取得する
	 *
	 * @return	列一覧
	 */
	public List<Column> getColumnList() {

		if (isGetColumnList) {
			return columnList;
		}

		try {

			columnLock.lock();

			if (isGetColumnList) {
				return columnList;
			}

			getColumnListInner();

		} catch (Exception ex) {

			Log.error(ex);

		} finally {

			columnLock.unlock();

		}

		return columnList;

	}

	/**
	 * 列一覧を宣言する
	 *
	 * <p>
	 * <b>生成コードはこれを override して静的な一覧を返す</b>（D-17）。
	 * override しなかった場合はリフレクションで {@link Column} 型のフィールドを集める。
	 * </p>
	 *
	 * @return	列一覧（宣言しない場合は null）
	 */
	protected List<Column> declareColumns () {

		return null;

	}

	/**
	 * 列一覧を取得する
	 *
	 * <p>
	 * {@link #declareColumns()} を実装していればそれを使う。
	 * していなければリフレクションで集める（手書きのテーブル定義向けの後方互換）。
	 * </p>
	 */
	private void getColumnListInner () {

		List<Column> declared = declareColumns();
		if (declared != null) {
			columnList.addAll(declared);
			isGetColumnList = true;
			return;
		}

		try {

			Field[] fields = getClass().getDeclaredFields();
			for (Field field : fields) {
				if (Column.class.isAssignableFrom(field.getType())) {
					// 非公開クラス・非公開フィールドでも読めるようにする。
					// 移送元はここで IllegalAccessException になり、
					// Log.error に落として空のリストを返していた（原因が分からず追いにくい）
					field.setAccessible(true);
					columnList.add((Column) field.get(this));
				}
			}

		} catch (Exception ex) {

			Log.error(ex);

		}

		if (columnList.isEmpty()) {
			Log.warn("列一覧が空です。declareColumns() を実装してください: " + getClass().getName());
		}

		isGetColumnList = true;

	}

	// endregion

	// region キー（要件 F-D-28）

	/**
	 * 一意キーを宣言する
	 *
	 * <p>
	 * <b>生成コードがこれを override する</b>（D-94）。
	 * 1つのキーが複数列なら、その列を並べたリストにする。
	 * </p>
	 *
	 * <p>
	 * override しなかった場合は「一意キーを知らない」ことになり、
	 * SQL 結果のキャッシュは<b>安全側（テーブルごと消す）に倒れる</b>。
	 * </p>
	 *
	 * @return	一意キーの一覧（宣言しない場合は null）
	 */
	protected List<List<Column>> declareUniqueKeys () {

		return null;

	}

	/**
	 * 主キー
	 *
	 * @return	主キーの列（無ければ空）
	 */
	public List<Column> getPrimaryKeyList () {

		List<Column> keys = new ArrayList<>();

		for (Column column : getColumnList()) {
			if (column.isPrimaryKey()) {
				keys.add(column);
			}
		}

		return keys;

	}

	/**
	 * 一意キー（主キーを含まない）
	 *
	 * @return	一意キーの一覧
	 */
	public List<List<Column>> getUniqueKeyList () {

		List<List<Column>> declared = declareUniqueKeys();

		return declared == null ? List.of() : declared;

	}

	/**
	 * 1行を特定できるキー（主キー + 一意キー）
	 *
	 * <p>
	 * <b>SQL 結果のキャッシュはこれを見て「どの行か」を決める</b>（要件 F-D-28）。
	 * </p>
	 *
	 * @return	キーの一覧（1つのキーは1列とは限らない）
	 */
	public List<List<Column>> getKeyList () {

		List<List<Column>> keys = new ArrayList<>();

		List<Column> primary = getPrimaryKeyList();
		if (!primary.isEmpty()) {
			keys.add(primary);
		}

		keys.addAll(getUniqueKeyList());

		return keys;

	}

	// endregion

	// region デフォルトデータ

	/**
	 * デフォルトデータを取得する
	 *
	 * @return	デフォルトデータ
	 */
	public Data defaultData () {

		Data row = new Data();

		for (Column column : getColumnList()) {
			row.putData(column, column.defaultValue());
		}

		return row;

	}

	// endregion


	/**
	 * 同じテーブルか（D-175）
	 *
	 * <p>
	 * <b>スキーマ名とテーブル名が同じなら同じテーブルである。</b>
	 * インスタンスが同じかどうかは見ない——生成物の {@code instance()} は
	 * <b>呼ぶたびに新しいものを返す</b>ので（D-174）、
	 * 同一性で比べると <b>{@code Staff.id.table()} と {@code Staff.instance()} が
	 * 別物になる</b>。「同じテーブルか」を見ているところが、静かに外れる。
	 * </p>
	 *
	 * <p>
	 * <b>仮テーブル（{@code TemporaryTable}）とは等しくならない。</b>
	 * あちらのスキーマは {@code empty} なので、名前が同じでもスキーマ名で分かれる——
	 * <b>名前だけで作ったものと、定義から来たものを混ぜない</b>ためである。
	 * </p>
	 *
	 * @param other	比べる相手
	 * @return	同じテーブルなら true
	 */
	@Override
	public boolean equals (Object other) {

		if (this == other) {
			return true;
		}

		if (!(other instanceof Table that)) {
			return false;
		}

		return java.util.Objects.equals(schemaName(), that.schemaName())
			&& java.util.Objects.equals(name(), that.name());

	}

	@Override
	public int hashCode () {

		return java.util.Objects.hash(schemaName(), name());

	}

	/**
	 * スキーマ名（無ければ null）
	 *
	 * @return	スキーマ名
	 */
	private String schemaName () {

		return schema() == null ? null : schema().name();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {

		return name();

	}

	/**
	 * SQLでテーブル内に含めるための名前を取得する
	 *
	 * @param name	名前
	 * @return	SQL as名
	 */
	public String sqlColumn (String name) {

		return name() + Column.SPLITTER + name;

	}

	/**
	 * カスタムカラム
	 *
	 * @param name	カラム名
	 * @return	カスタムカラム
	 */
	public Column customColumn (String name) {

		return new TemporaryColumn(this, name);

	}

}
