package io.jimble.db.internal.generator;

import io.jimble.db.DB;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.util.data.Data;

import java.util.List;

/**
 * テーブル定義を DB から読む（要件 F-D-30 / D-98）
 *
 * <p>
 * <b>製品ごとに引き方がまるで違う。</b>
 * MySQL は {@code SHOW TABLE STATUS} / {@code SHOW FULL COLUMNS} / {@code SHOW INDEX}、
 * PostgreSQL は {@code pg_catalog}。返る列の名前も意味も揃っていない。
 * </p>
 *
 * <p>
 * <b>ここで名前と型を揃える。</b>{@link Generator} は揃ったものだけを見るので、
 * 生成の本体に製品ごとの分岐が入らない。
 * </p>
 *
 * <p>
 * 返す {@link Data} のキーは次のとおり。
 * </p>
 *
 * <table>
 *   <caption>揃えたあとのキー</caption>
 *   <tr><th>メソッド</th><th>キー</th></tr>
 *   <tr><td>{@link #tables}</td><td>{@code name} / {@code comment}</td></tr>
 *   <tr><td>{@link #columns}</td><td>{@code name} / {@code type} / {@code extra} / {@code nullable}（真偽）
 *       / {@code primary_key}（真偽）/ {@code comment} / {@code default_value}</td></tr>
 *   <tr><td>{@link #indexes}</td><td>{@code name} / {@code column_name} / {@code seq}
 *       / {@code is_unique}（真偽）/ {@code is_primary}（真偽）</td></tr>
 * </table>
 */
interface TableMetaReader {

	/**
	 * その製品の読み手
	 *
	 * @param dialect	方言
	 * @return	読み手
	 * @throws GeneratorException	対応していない製品の場合
	 */
	static TableMetaReader of (Dialect dialect) {

		if (MySqlDialect.NAME.equals(dialect.name())) {
			return new MySqlTableMetaReader();
		}

		if (PostgreSqlDialect.NAME.equals(dialect.name())) {
			return new PostgreSqlTableMetaReader();
		}

		throw new GeneratorException(
			"テーブル定義クラスの生成に対応していない製品です: " + dialect.name());

	}

	/**
	 * テーブル一覧
	 *
	 * @param db	DB
	 * @return	テーブル
	 */
	List<Data> tables (DB db);

	/**
	 * 列一覧（定義順）
	 *
	 * @param db	DB
	 * @param table	テーブル名
	 * @return	列
	 */
	List<Data> columns (DB db, String table);

	/**
	 * インデックス一覧（1行1列。{@code seq} の順）
	 *
	 * @param db	DB
	 * @param table	テーブル名
	 * @return	インデックスの列
	 */
	List<Data> indexes (DB db, String table);

	// region 生成する SchemaSQL の書き方

	/**
	 * 列の定義にコメントを書けるか
	 *
	 * <p>MySQL は書ける。PostgreSQL は {@code COMMENT ON} を別に出す。</p>
	 *
	 * @return	書ける場合 = true
	 */
	boolean inlineComment ();

	/**
	 * テーブルのコメントを付ける文（{@link #inlineComment} が false のときだけ使う）
	 *
	 * @param table		テーブル名
	 * @param comment	コメント
	 * @return	SQL
	 */
	String tableCommentSql (String table, String comment);

	/**
	 * 列のコメントを付ける文（{@link #inlineComment} が false のときだけ使う）
	 *
	 * @param table		テーブル名
	 * @param column	列名
	 * @param comment	コメント
	 * @return	SQL
	 */
	String columnCommentSql (String table, String column, String comment);

	/**
	 * 既定値の書き方
	 *
	 * @param typeClass	その列の Java 型（囲むかどうかの判断に使う）
	 * @param value		既定値
	 * @return	DDL に書く形
	 */
	String defaultValueSql (Class<?> typeClass, String value);

	/**
	 * 主キーを足す文
	 *
	 * @param table		テーブル名
	 * @param columns	列名
	 * @return	SQL
	 */
	String addPrimaryKeySql (String table, List<String> columns);

	/**
	 * 一意キーを足す文
	 *
	 * @param table		テーブル名
	 * @param name		キー名
	 * @param columns	列名
	 * @return	SQL
	 */
	String addUniqueSql (String table, String name, List<String> columns);

	/**
	 * インデックスを足す文
	 *
	 * @param table		テーブル名
	 * @param name		インデックス名
	 * @param columns	列名
	 * @param predicate	条件（部分インデックス）。無ければ null
	 * @return	SQL
	 */
	String addIndexSql (String table, String name, List<String> columns, String predicate);

	// endregion

}
