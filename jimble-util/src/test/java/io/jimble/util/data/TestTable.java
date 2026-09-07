package io.jimble.util.data;

import io.jimble.util.data.definition.IColumn;
import io.jimble.util.data.definition.ISchema;
import io.jimble.util.data.definition.ITable;

/**
 * テスト用のテーブル定義
 *
 * <p>本物の列定義（M3 の SQL ビルダー）は同じインターフェースを実装する。</p>
 */
final class TestTable implements ITable {

	/* スキーマ */
	static final ISchema SCHEMA = () -> "test_schema";

	/* site テーブル */
	static final TestTable SITE = new TestTable("site");

	/* feed テーブル */
	static final TestTable FEED = new TestTable("feed");

	/* テーブル名 */
	private final String name;

	/**
	 * コンストラクタ
	 *
	 * @param name	テーブル名
	 */
	private TestTable (String name) {

		this.name = name;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISchema schema () {

		return SCHEMA;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () {

		return name;

	}

	/**
	 * 列を作る
	 *
	 * @param columnName	列名
	 * @param clazz			型
	 * @return	列
	 */
	IColumn column (String columnName, Class<?> clazz) {

		TestTable table = this;

		return new IColumn() {
			@Override public ITable table () { return table; }
			@Override public String name () { return columnName; }
			@Override public Class<?> clazz () { return clazz; }
			@Override public boolean isNullable () { return true; }
			@Override public Object defaultValue () { return null; }
			@Override public boolean isPrimaryKey () { return false; }
		};

	}

}
