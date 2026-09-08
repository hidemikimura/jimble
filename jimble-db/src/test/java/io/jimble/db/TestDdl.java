package io.jimble.db;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * テスト用のテーブルを製品に合わせて作る（要件 F-D-30）
 *
 * <p>
 * 結合テストの<b>お膳立て</b>のためのもの。検証したいのは
 * SQL ビルダーが吐く SQL のほうで、テーブルの作り方ではない。
 * そこで<b>テストには MySQL の DDL を1つだけ書き</b>、
 * PostgreSQL で走らせるときだけここで置き換える。
 * </p>
 *
 * <p>
 * <b>汎用の変換器ではない。</b>テストが実際に使っている書き方だけを扱う。
 * 知らない書き方が来たら、そのまま渡して DB に落としてもらう
 * （黙って直したつもりになるより、落ちたほうが分かる）。
 * </p>
 *
 * <table>
 *   <caption>置き換えるもの</caption>
 *   <tr><th>MySQL</th><th>PostgreSQL</th></tr>
 *   <tr><td>{@code bigint unsigned auto_increment}</td><td>{@code bigserial}</td></tr>
 *   <tr><td>{@code int unsigned auto_increment}</td><td>{@code serial}</td></tr>
 *   <tr><td>{@code xxx unsigned}</td><td>{@code xxx}</td></tr>
 *   <tr><td>{@code tinyint(1)}</td><td>{@code boolean}（既定値の 0/1 も直す）</td></tr>
 *   <tr><td>{@code datetime}</td><td>{@code timestamp}</td></tr>
 *   <tr><td>{@code current_timestamp()}</td><td>{@code current_timestamp}</td></tr>
 *   <tr><td>{@code comment '...'}</td><td>落とす</td></tr>
 *   <tr><td>{@code ENGINE=... CHARSET=... COLLATE=...}</td><td>落とす</td></tr>
 *   <tr><td>バッククォート</td><td>二重引用符</td></tr>
 * </table>
 */
public final class TestDdl {

	/**
	 * コンストラクタ
	 */
	private TestDdl () {

	}

	/**
	 * DDL を実行する
	 *
	 * @param db		DB
	 * @param mysqlDdl	MySQL の DDL
	 */
	public static void execute (DB db, String mysqlDdl) {

		db.execute(translate(db.dialect(), mysqlDdl));

		if (MySqlDialect.NAME.equals(db.dialect().name())) {
			return;
		}

		/*
		 * PostgreSQL は列の定義にコメントを書けない。
		 * <b>落としたままにすると、コメントを読む側（codegen）の
		 * テストが「コメントが無い」ことを確かめてしまう。</b>
		 * MySQL の DDL に書いてあったコメントを COMMENT ON で付け直す。
		 */
		for (String sql : commentSqlList(mysqlDdl)) {
			db.execute(sql);
		}

	}

	/**
	 * MySQL の DDL に書かれたコメントを {@code COMMENT ON} にする
	 *
	 * <p>
	 * <b>正規表現1本では足りない。</b>{@code decimal(10,2)} や
	 * {@code enum('a','b')} の中にカンマが入るので、
	 * <b>括弧の外のカンマ</b>で列の定義に切ってから読む。
	 * </p>
	 *
	 * @param mysqlDdl	MySQL の DDL
	 * @return	SQL
	 */
	private static List<String> commentSqlList (String mysqlDdl) {

		List<String> res = new ArrayList<>();

		Matcher table = Pattern
			.compile("(?is)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?[`\"]?([A-Za-z0-9_]+)[`\"]?")
			.matcher(mysqlDdl);

		if (!table.find()) {
			return res;
		}

		String tableName = table.group(1);

		int open = mysqlDdl.indexOf('(', table.end());
		int close = matching(mysqlDdl, open);

		if (open < 0 || close < 0) {
			return res;
		}

		for (String part : split(mysqlDdl.substring(open + 1, close))) {

			Matcher column = Pattern
				.compile("(?is)^\\s*[`\"]?([A-Za-z0-9_]+)[`\"]?\\s+.*?comment\\s+'((?:[^']|'')*)'")
				.matcher(part);

			if (!column.find()) {
				continue;
			}

			String name = column.group(1);

			// constraint / primary key / index の行は列ではない
			if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
				continue;
			}

			res.add("comment on column %s.%s is '%s'".formatted(tableName, name, column.group(2)));

		}

		// ) のうしろの comment 'xxx' はテーブルのコメント
		Matcher tableComment = Pattern
			.compile("(?is)comment\\s*=?\\s*'((?:[^']|'')*)'")
			.matcher(mysqlDdl.substring(close + 1));

		if (tableComment.find()) {
			res.add("comment on table %s is '%s'".formatted(tableName, tableComment.group(1)));
		}

		return res;

	}

	/** 列名ではない語 */
	private static final List<String> RESERVED = List.of(
		"constraint", "primary", "unique", "key", "index", "fulltext", "spatial", "foreign", "check");

	/**
	 * 対応する閉じ括弧
	 *
	 * @param text	文字列
	 * @param open	開き括弧の位置
	 * @return	閉じ括弧の位置。無ければ -1
	 */
	private static int matching (String text, int open) {

		if (open < 0) {
			return -1;
		}

		int depth = 0;
		boolean inQuote = false;

		for (int i = open; i < text.length(); i++) {

			char c = text.charAt(i);

			if (c == '\'') {
				inQuote = !inQuote;
			} else if (!inQuote && c == '(') {
				depth++;
			} else if (!inQuote && c == ')') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}

		}

		return -1;

	}

	/**
	 * 括弧の外のカンマで切る
	 *
	 * @param body	{@code create table x ( ... )} の中身
	 * @return	列や制約の定義
	 */
	private static List<String> split (String body) {

		List<String> res = new ArrayList<>();

		int depth = 0;
		boolean inQuote = false;
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < body.length(); i++) {

			char c = body.charAt(i);

			if (c == '\'') {
				inQuote = !inQuote;
			} else if (!inQuote && c == '(') {
				depth++;
			} else if (!inQuote && c == ')') {
				depth--;
			} else if (!inQuote && depth == 0 && c == ',') {
				res.add(sb.toString());
				sb.setLength(0);
				continue;
			}

			sb.append(c);

		}

		res.add(sb.toString());

		return res;

	}

	/**
	 * 採番の続きを、いま入っている最大値に合わせる
	 *
	 * <p>
	 * <b>MySQL の auto_increment は、id を明示して入れると勝手に追いつく。
	 * PostgreSQL の連番は追いつかない。</b>
	 * お膳立てで id を明示して入れたあと、そのままだと
	 * 次の INSERT が 1 を採ろうとして主キー重複で落ちる。
	 * </p>
	 *
	 * @param db		DB
	 * @param table		テーブル名
	 * @param column	採番列
	 */
	public static void syncSequence (DB db, String table, String column) {

		if (MySqlDialect.NAME.equals(db.dialect().name())) {
			return;
		}

		db.select("""
			SELECT setval(
				pg_get_serial_sequence('%s', '%s')
				, COALESCE((SELECT MAX(%s) FROM %s), 1)
			) AS seq
			""".formatted(table, column, column, table));

	}

	/**
	 * いまのスキーマを返す式（{@code information_schema.table_schema} と比べるとき）
	 *
	 * @param db	DB
	 * @return	SQL の式
	 */
	public static String currentSchema (DB db) {

		return MySqlDialect.NAME.equals(db.dialect().name()) ? "DATABASE()" : "current_schema()";

	}

	/**
	 * いまのデータベース名を返す式
	 *
	 * <p>MySQL には「データベース」と「スキーマ」の区別が無いので {@code DATABASE()}。</p>
	 *
	 * @param db	DB
	 * @return	SQL の式
	 */
	public static String currentDatabase (DB db) {

		return MySqlDialect.NAME.equals(db.dialect().name()) ? "DATABASE()" : "current_database()";

	}

	/**
	 * DDL を製品に合わせて書き換える
	 *
	 * @param dialect	方言
	 * @param mysqlDdl	MySQL の DDL
	 * @return	その製品の DDL
	 */
	public static String translate (Dialect dialect, String mysqlDdl) {

		if (MySqlDialect.NAME.equals(dialect.name())) {
			return mysqlDdl;
		}

		String sql = mysqlDdl;

		// comment 'xxx' を落とす（列にも表にも付く）
		sql = sql.replaceAll("(?i)\\s+comment\\s+'(?:[^']|'')*'", "");

		// ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin → )
		sql = sql.replaceAll("(?i)\\)\\s*ENGINE\\s*=\\s*\\w+(\\s+DEFAULT)?(\\s+CHARSET\\s*=\\s*\\w+)?(\\s+COLLATE\\s*=\\s*\\w+)?", ")");

		// 連番
		sql = sql.replaceAll("(?i)\\bbigint\\s+unsigned\\s+auto_increment\\b", "bigserial");
		sql = sql.replaceAll("(?i)\\bbigint\\s+auto_increment\\b", "bigserial");
		sql = sql.replaceAll("(?i)\\bint\\s+unsigned\\s+auto_increment\\b", "serial");
		sql = sql.replaceAll("(?i)\\bint\\s+auto_increment\\b", "serial");

		// 真偽値（既定値の 0/1 も直さないと PostgreSQL が受け取らない）
		sql = sql.replaceAll("(?i)\\btinyint\\s*\\(\\s*1\\s*\\)", "boolean");
		sql = sql.replaceAll("(?i)(\\bboolean\\b[^,\\n]*?\\bdefault\\s+)1\\b", "$1true");
		sql = sql.replaceAll("(?i)(\\bboolean\\b[^,\\n]*?\\bdefault\\s+)0\\b", "$1false");

		// unsigned は無い
		sql = sql.replaceAll("(?i)\\s+unsigned\\b", "");

		// 日時
		sql = sql.replaceAll("(?i)\\bdatetime\\b", "timestamp");
		sql = sql.replaceAll("(?i)\\bcurrent_timestamp\\s*\\(\\s*\\)", "current_timestamp");

		// 識別子の囲み
		sql = sql.replace('`', '"');

		return sql;

	}

}
