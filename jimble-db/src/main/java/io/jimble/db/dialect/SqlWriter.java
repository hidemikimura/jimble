package io.jimble.db.dialect;

import io.jimble.util.data.definition.IColumn;
import io.jimble.util.data.definition.ITable;

/**
 * SQL の書き出し先（要件 F-D-30）
 *
 * <p>
 * <b>{@link StringBuilder} に方言を添えただけのもの。</b>
 * SQL を組み立てるメソッドは全部これを受け取る。
 * </p>
 *
 * <p>
 * 引数を2つ（{@code StringBuilder} と {@link Dialect}）にせず1つにまとめたのは、
 * <b>組み立てる側が方言を持ち回らずに済む</b>ためである。
 * {@code append} は {@link StringBuilder} と同じ形にしてあるので、
 * 既存の組み立てコードはそのまま動く。
 * </p>
 */
public final class SqlWriter {

	/* 書き出し先 */
	private final StringBuilder sb;

	/* 方言 */
	private final Dialect dialect;

	/**
	 * コンストラクタ
	 *
	 * @param sb		書き出し先
	 * @param dialect	方言
	 */
	public SqlWriter (StringBuilder sb, Dialect dialect) {

		this.sb = sb;
		this.dialect = dialect == null ? Dialects.defaultDialect() : dialect;

	}

	/**
	 * コンストラクタ
	 *
	 * @param dialect	方言
	 */
	public SqlWriter (Dialect dialect) {

		this(new StringBuilder(), dialect);

	}

	/**
	 * 方言
	 *
	 * @return	方言
	 */
	public Dialect dialect () {

		return dialect;

	}

	/**
	 * 書き出し先そのもの
	 *
	 * <p>方言を知らなくてよい細かい組み立てに使う。</p>
	 *
	 * @return	書き出し先
	 */
	public StringBuilder builder () {

		return sb;

	}

	// region append（StringBuilder と同じ形）

	/**
	 * 足す
	 *
	 * @param value	値
	 * @return	自分
	 */
	public SqlWriter append (String value) {

		sb.append(value);
		return this;

	}

	/**
	 * 足す
	 *
	 * @param value	値
	 * @return	自分
	 */
	public SqlWriter append (char value) {

		sb.append(value);
		return this;

	}

	/**
	 * 足す
	 *
	 * @param value	値
	 * @return	自分
	 */
	public SqlWriter append (int value) {

		sb.append(value);
		return this;

	}

	/**
	 * 足す
	 *
	 * @param value	値
	 * @return	自分
	 */
	public SqlWriter append (long value) {

		sb.append(value);
		return this;

	}

	/**
	 * 足す
	 *
	 * @param value	値
	 * @return	自分
	 */
	public SqlWriter append (Object value) {

		sb.append(value);
		return this;

	}

	/**
	 * 長さ
	 *
	 * @return	長さ
	 */
	public int length () {

		return sb.length();

	}

	/**
	 * 長さを詰める
	 *
	 * @param length	長さ
	 */
	public void setLength (int length) {

		sb.setLength(length);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return sb.toString();

	}

	// endregion

	// region 方言に聞くもの

	/**
	 * 識別子を囲んで足す
	 *
	 * @param name	名前
	 * @return	自分
	 */
	public SqlWriter identifier (String name) {

		dialect.identifier(sb, name);
		return this;

	}

	/**
	 * テーブル修飾つきの列を足す
	 *
	 * <p>テーブル名が空なら列だけ。</p>
	 *
	 * @param table		テーブル名
	 * @param column	列名
	 * @return	自分
	 */
	public SqlWriter qualified (String table, String column) {

		if (table != null && !table.isEmpty()) {
			dialect.identifier(sb, table);
			sb.append('.');
		}

		dialect.identifier(sb, column);

		return this;

	}

	/**
	 * テーブル修飾つきの列を足す
	 *
	 * @param column	列
	 * @return	自分
	 */
	public SqlWriter qualified (IColumn column) {

		ITable table = column.table();

		return qualified(table == null ? null : table.name(), column.name());

	}

	/**
	 * 関数名を足す
	 *
	 * @param function	関数
	 * @return	自分
	 */
	public SqlWriter function (SqlFunction function) {

		sb.append(dialect.function(function));
		return this;

	}

	/**
	 * その製品では書けないと伝える
	 *
	 * @param feature	書けないもの
	 * @return	例外
	 */
	public DialectException unsupported (String feature) {

		return dialect.unsupported(feature);

	}

	// endregion

}
