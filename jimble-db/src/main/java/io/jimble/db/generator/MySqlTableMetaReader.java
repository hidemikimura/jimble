package io.jimble.db.generator;

import io.jimble.db.DB;
import io.jimble.util.data.Data;
import io.jimble.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL / MariaDB のテーブル定義を読む（要件 F-D-30 / D-98）
 *
 * <p>
 * <b>移送元と同じ {@code SHOW ...} をそのまま使う。</b>
 * {@code information_schema} に置き換えると既定値や型名の表記が
 * わずかに変わることがあり、<b>生成物が静かに変わる</b>。
 * 揃えるのは<b>読んだあとのキー名だけ</b>にしてある。
 * </p>
 */
final class MySqlTableMetaReader implements TableMetaReader {

	@Override
	public List<Data> tables (DB db) {

		List<Data> res = new ArrayList<>();

		for (Data row : db.selectList("SHOW TABLE STATUS")) {
			res.add(new Data()
				.putData("name", row.getString("Name"))
				.putData("comment", row.getStringOptional("Comment")));
		}

		return res;

	}

	@Override
	public List<Data> columns (DB db, String table) {

		List<Data> res = new ArrayList<>();

		for (Data row : db.selectList("SHOW FULL COLUMNS FROM `" + table + "`")) {
			res.add(new Data()
				.putData("name", row.getString("Field"))
				.putData("type", row.getString("Type"))
				.putData("extra", row.getString("Extra"))
				.putData("nullable", "YES".equalsIgnoreCase(row.getString("Null")))
				.putData("primary_key", "PRI".equalsIgnoreCase(row.getString("Key")))
				.putData("comment", row.getStringOptional("Comment"))
				.putData("default_value", row.getString("Default")));
		}

		return res;

	}

	@Override
	public List<Data> indexes (DB db, String table) {

		List<Data> res = new ArrayList<>();

		for (Data row : db.selectList("SHOW INDEX FROM `" + table + "`")) {

			String name = row.getString("Key_name");

			res.add(new Data()
				.putData("name", name)
				.putData("column_name", row.getString("Column_name"))
				.putData("seq", row.getInt("Seq_in_index"))
				.putData("is_primary", "PRIMARY".equalsIgnoreCase(name))
				.putData("is_unique", row.getInt("Non_unique") == 0));

		}

		return res;

	}

	// region 生成する SchemaSQL の書き方

	@Override
	public boolean inlineComment () {

		return true;

	}

	@Override
	public String tableCommentSql (String table, String comment) {

		throw new UnsupportedOperationException("MySQL は列の定義にコメントを書ける");

	}

	@Override
	public String columnCommentSql (String table, String column, String comment) {

		throw new UnsupportedOperationException("MySQL は列の定義にコメントを書ける");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>移送元と同じく、いつも囲む。</b>{@code CURRENT_TIMESTAMP} まで
	 * 文字列にしてしまうが、ここを直すと<b>生成物が変わる</b>ので触らない
	 * （{@code SchemaSQL} は参照用の定数で、フレームワークは実行しない）。
	 * </p>
	 */
	@Override
	public String defaultValueSql (Class<?> typeClass, String value) {

		return "'" + value + "'";

	}

	@Override
	public String addPrimaryKeySql (String table, List<String> columns) {

		return "alter table %s add primary key (%s);".formatted(table, StringUtil.concat(", ", columns));

	}

	@Override
	public String addUniqueSql (String table, String name, List<String> columns) {

		return "alter table %s add unique index %s (%s);"
			.formatted(table, name, StringUtil.concat(", ", columns));

	}

	@Override
	public String addIndexSql (String table, String name, List<String> columns, String predicate) {

		return "alter table %s add index %s (%s);"
			.formatted(table, name, StringUtil.concat(", ", columns));

	}

	// endregion

}
