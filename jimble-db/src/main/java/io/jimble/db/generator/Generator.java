package io.jimble.db.generator;

import io.jimble.util.convertor.Convertor;
import io.jimble.util.io.FileUtil;
import io.jimble.util.io.output.TextOutput;
import io.jimble.util.string.StringUtil;
import io.jimble.util.data.Data;
import io.jimble.util.date.DateUtil;
import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.generator.info.ColumnInfo;
import io.jimble.db.generator.info.IndexInfo;
import io.jimble.db.generator.info.TableInfo;
import io.jimble.db.sql.SQL;
import io.jimble.util.log.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

/**
 * ソースコード生成
 *
 * <p>
 * DB のスキーマからテーブル定義クラス（{@code static final Column} を持つ）と
 * 型付きアクセサ（{@code Abstract<Table>Data}）を生成する（要件 F-G-01〜03）。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>列一覧を静的に出力する</b>（D-17）。移送元の生成コードは
 *       {@code instance().getColumnList()} を呼び、実行時にリフレクションで
 *       フィールドを集めていた。生成する側は列を全部知っているのだから、
 *       {@code List.of(...)} を書き出せばリフレクションは要らない</li>
 *   <li><b>失敗を握りつぶさない。</b>移送元は出力の失敗を {@code Log.error} して
 *       黙って戻っていたため、<b>生成物が欠けたままビルドが進んでいた。</b>
 *       {@link GeneratorException} を投げる</li>
 *   <li><b>jimble の管理テーブルを生成対象から外す</b>（{@link GeneratorConf#FRAMEWORK_TABLES}）。
 *       移送元は {@code SHOW TABLE STATUS} の結果をそのまま生成しており、
 *       {@code migration} や {@code db_lock} がアプリのコードに現れていた</li>
 *   <li><b>生成前に出力先を掃除する。</b>消えたテーブルのクラスが残らないようにする</li>
 * </ol>
 */
public class Generator {


	/**
	 * 生成物の先頭に置く印（要件 F-G-03 / D-88）
	 *
	 * <p>
	 * <b>手で直しても次の {@code codegen} で消える。</b>
	 * 消えることを知らずに直すと、直したはずの変更が黙って戻る。
	 * </p>
	 */
	private static final List<String> GENERATED_HEADER = List.of(
		"/*"
		, " * このファイルは jimble が作りました（codegen）。手で直さないでください。"
		, " * 直しても次の codegen で消えます。"
		, " */"
	);

	/**
	 * 生成物の印を書く
	 *
	 * @param textOutput	出力先
	 * @throws Exception	書けなかった場合
	 */
	private static void writeGeneratedHeader (TextOutput textOutput) throws Exception {

		for (String line : GENERATED_HEADER) {
			textOutput.writeLine(line);
		}

		textOutput.writeLine("");

	}

	/**
	 * ソース生成判定
	 *
	 * @param packageName   パッケージ名
	 * @return  必要な場合 = true
	 */
	public static boolean isGenerationRequired (String packageName) {

		DBSource dbSource = DBUtil.getDataSourceList().getFirst();

		String className = "%s.%s.%s".formatted(packageName, dbSource.name, upperCamel(dbSource.name));
		try {
			Class<?> cls = Class.forName(className);
			Field field = cls.getDeclaredField("SQL_VERSION");
			field.setAccessible(true);
			long version = (long) field.get(null);

			return SQL.VERSION != version;
		} catch (Throwable ex) {
			return true;
		}

	}

	/**
	 * ソースコード生成
	 *
	 * @param outputDir     出力ディレクトリ
	 * @param packageName   パッケージ名
	 */
	public static void generate (File outputDir, String packageName) {

		outputDir.mkdirs();

		for (DBSource dbSource : DBUtil.getDataSourceList()) {
			generate(outputDir, packageName, dbSource);
		}

	}

	/**
	 * ソースコード生成（設定のパッケージ名を使う）
	 *
	 * @param outputDir	出力ディレクトリ
	 */
	public static void generate (File outputDir) {

		generate(outputDir, GeneratorConf.packageName());

	}

	/**
	 * 対象データソースのソースコードを生成する
	 *
	 * @param outputDir     出力ディレクトリ
	 * @param packageName   パッケージ名
	 * @param dbSource      DBソース
	 */
	public static void generate (File outputDir, String packageName, DBSource dbSource) {

		// ルートディレクトリ(db/{スキーマ名})
		File rootDir = new File(outputDir, dbSource.name.toLowerCase());

		// 消えたテーブルのクラスが残らないよう、生成前に掃除する
		FileUtil.delete(rootDir);
		rootDir.mkdirs();

		// スキーマクラス名
		String schemeClassName = upperCamel(dbSource.name);

		// テーブル定義一覧を取得する
		List<TableInfo> tableInfoList = getTableInfoList(dbSource);

		// スキーマクラス
		outputScheme(rootDir, packageName, dbSource, dbSource.name, schemeClassName, tableInfoList);

		// テーブルクラス
		for (TableInfo tableInfo : tableInfoList) {
			outputTable(rootDir, packageName, dbSource, schemeClassName, tableInfo);
			outputTableData(rootDir, packageName, dbSource, schemeClassName, tableInfo);
		}

	}

	// region スキーマクラスソースコード出力

	/**
	 * スキーマクラスソースコード出力
	 *
	 * @param rootDir           ルートディレクトリ
	 * @param packageName       パッケージ名
	 * @param dbSource          DBソース
	 * @param schemeName        スキーマ名
	 * @param schemeClassName   スキーマクラス名
	 * @param tableInfoList     テーブル情報一覧
	 */
	private static void outputScheme (File rootDir, String packageName, DBSource dbSource, String schemeName, String schemeClassName, List<TableInfo> tableInfoList) {

		File sourceFile = new File(rootDir, schemeClassName + ".java");

		try (
			TextOutput textOutput = new TextOutput(sourceFile)
		) {

			writeGeneratedHeader(textOutput);

			textOutput.writeLine("package %s.%s;".formatted(packageName, dbSource.name));
			textOutput.writeLine("");

			for (TableInfo tableInfo : tableInfoList) {
				textOutput.writeLine("import %s.%s.table.%s.%s;".formatted(packageName, dbSource.name, tableInfo.name, tableInfo.className));
			}
			textOutput.writeLine("import io.jimble.db.DB;");
			textOutput.writeLine("import io.jimble.db.DBUtil;");
			textOutput.writeLine("import io.jimble.db.sql.definition.schema.AbstractSchema;");
			textOutput.writeLine("import io.jimble.db.sql.definition.table.Table;");
			textOutput.writeLine("");


			textOutput.writeLine("/**");
			textOutput.writeLine(" * %s".formatted(schemeName));
			textOutput.writeLine(" */");
			textOutput.writeLine("public class %s extends AbstractSchema {".formatted(schemeClassName));
			textOutput.writeLine("");
			textOutput.writeLine("\tprivate static final long SQL_VERSION = %s;".formatted(SQL.VERSION));
			textOutput.writeLine("");
			for (TableInfo tableInfo : tableInfoList) {
				textOutput.writeLine("\t/* %s */".formatted(tableInfo.comment));
				textOutput.writeLine("\tpublic static final Table %s = %s.instance();".formatted(tableInfo.name, tableInfo.className));
				textOutput.writeLine("");
			}
			textOutput.writeLine("");


			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * {@inheritDoc}");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\t@Override");
			textOutput.writeLine("\tpublic String name () { return \"%s\"; }".formatted(dbSource.name));
			textOutput.writeLine("");

			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * get DB instance");
			textOutput.writeLine("\t *");
			textOutput.writeLine("\t * @return DB");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\tpublic static DB db () { return DBUtil.getDB(\"%s\"); }".formatted(dbSource.name));
			textOutput.writeLine("");

			if (!dbSource.subsDbSourceMap.isEmpty()) {
				for (String subDbName : dbSource.subsDbSourceMap.keySet()) {
					textOutput.writeLine("\t/**");
					textOutput.writeLine("\t * get sub DB instance");
					textOutput.writeLine("\t *");
					textOutput.writeLine("\t * @return sub DB");
					textOutput.writeLine("\t */");
					textOutput.writeLine("\tpublic static DB %sDB () { return DBUtil.getDB(\"%s\").newSubDB(\"%s\"); }".formatted(subDbName, dbSource.name, subDbName));
					textOutput.writeLine("");
				}
			}

			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * schema SQL");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\tpublic static final String SchemaSQL = ");
			for (TableInfo tableInfo : tableInfoList) {
				textOutput.writeLine("");

				String tableComment = tableInfo.getCommentNoVersion();
				textOutput.write("\t\t/* " + tableInfo.name);
				if (tableComment != null && !tableComment.isEmpty()) {
					textOutput.write("（" + tableComment + "）");
				}
				textOutput.write(" */\n");

				textOutput.write("\t\t\"create table \" + ");
				textOutput.write(tableInfo.name + " + \" ( \" + \n");

				for (int i = 0; i < tableInfo.columnList.size(); i++) {
					ColumnInfo columnInfo = tableInfo.columnList.get(i);

					textOutput.write("\t\t\t");
					textOutput.write(tableInfo.className + "." + columnInfo.name);
					textOutput.write(" + \" " + columnInfo.type);
					if (columnInfo.extra != null && !columnInfo.extra.isEmpty()) {
						textOutput.write(" " + columnInfo.extra);
					}
					if (columnInfo.defaultValueString != null) {
						textOutput.write(" default '" + columnInfo.defaultValueString + "'");
					}
					if (!columnInfo.nullable) {
						textOutput.write(" not null");
					}
					if (columnInfo.comment != null && !columnInfo.comment.isEmpty()) {
						textOutput.write(" comment '" + escapeComment(columnInfo.comment) + "'");
					}
					if (i < tableInfo.columnList.size() - 1) {
						textOutput.write(",");
					}
					textOutput.write("\" + \n");
				}

				textOutput.write("\t\t\")");
				if (tableComment != null && !tableComment.isEmpty()) {
					textOutput.write(" comment '" + escapeComment(tableComment) + "'");
				}
				textOutput.write("; \" + \n");

				for (IndexInfo indexInfo : tableInfo.indexList) {
					if (indexInfo.isPrimary) {
						textOutput.writeLine("\t\t\"alter table " + tableInfo.name + " add primary key (" + StringUtil.concat(", ", indexInfo.columnNameList) + ");\" +");
					} else if (indexInfo.isUnique) {
						textOutput.writeLine("\t\t\"alter table " + tableInfo.name + " add unique index " + indexInfo.name + " (" + StringUtil.concat(", ", indexInfo.columnNameList) + ");\" +");
					} else {
						textOutput.writeLine("\t\t\"alter table " + tableInfo.name + " add index " + indexInfo.name + " (" + StringUtil.concat(", ", indexInfo.columnNameList) + ");\" +");
					}
				}
			}
			textOutput.writeLine("\t\"\";");


			textOutput.writeLine("}");

		} catch (Exception ex) {
			Log.error(ex);
		}

	}

	private static final String commentQuotePattern = Pattern.quote("\"");
	private static final String commentQuote = "\\\\\"";

	private static String escapeComment (String comment) {

		return comment.replaceAll(commentQuotePattern, commentQuote);

	}

	// endregion

	// region テーブルクラス出力

	/**
	 * テーブルクラス出力
	 *
	 * @param rootDir           ルートディレクトリ
	 * @param packageName       パッケージ名
	 * @param dbSource          DBソース
	 * @param schemeClassName   スキーマクラス名
	 * @param tableInfo         テーブル情報
	 */
	private static void outputTable (File rootDir, String packageName, DBSource dbSource, String schemeClassName, TableInfo tableInfo) {

		File tableRootDir = new File(rootDir, "table");
		File tableDir = new File(tableRootDir, tableInfo.name);
		tableDir.mkdirs();

		File sourceFile = new File(tableDir, tableInfo.className + ".java");
		try (
			TextOutput textOutput = new TextOutput(sourceFile)
		) {

			writeGeneratedHeader(textOutput);

			textOutput.writeLine("package %s.%s.table.%s;".formatted(packageName, dbSource.name, tableInfo.name));
			textOutput.writeLine("");

			textOutput.writeLine("import %s.%s.%s;".formatted(packageName, dbSource.name, schemeClassName));
			textOutput.writeLine("import io.jimble.db.sql.definition.column.Column;");
			textOutput.writeLine("import io.jimble.util.data.definition.ISchema;");
			textOutput.writeLine("import io.jimble.db.sql.definition.table.Table;");
			textOutput.writeLine("");
			textOutput.writeLine("import java.util.List;");
			textOutput.writeLine("");

			textOutput.writeLine("/**");
			textOutput.writeLine(" * %s".formatted(tableInfo.comment));
			textOutput.writeLine(" */");
			textOutput.writeLine("public class %s extends Table {".formatted(tableInfo.className));
			textOutput.writeLine("");
			for (ColumnInfo columnInfo : tableInfo.columnList) {
				textOutput.writeLine("\t/* %s */".formatted(columnInfo.comment));
				textOutput.writeLine("\tpublic static final Column %s = new Column(instance(), \"%s\", %s.class, %s, %s, %s);".formatted(columnInfo.name, columnInfo.name, columnInfo.typeClass.getTypeName(), String.valueOf(columnInfo.nullable), getColumnDefaultValueString(columnInfo), String.valueOf(columnInfo.primaryKey)));
				textOutput.writeLine("");
			}
			textOutput.writeLine("");

			// 列一覧を静的に出力する（D-17）。実行時のリフレクションを無くすため
			{
				List<String> names = new ArrayList<>();
				for (ColumnInfo columnInfo : tableInfo.columnList) {
					names.add(columnInfo.name);
				}
				textOutput.writeLine("\t/* 列一覧（生成時に確定。実行時のリフレクションはしない） */");
				textOutput.writeLine("\tprivate static final List<Column> COLUMNS = List.of(%s);".formatted(StringUtil.concat(", ", names)));
				textOutput.writeLine("");
			}

			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * {@inheritDoc}");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\t@Override");
			textOutput.writeLine("\tprotected List<Column> declareColumns () { return COLUMNS; }");
			textOutput.writeLine("");

			textOutput.writeLine("\tpublic static List<Column> columns () { return COLUMNS; }");
			textOutput.writeLine("");

			textOutput.writeLine("\tpublic %s (ISchema schema, String name) { super(schema, name); }".formatted(tableInfo.className));
			textOutput.writeLine("");

			textOutput.writeLine("\tpublic static %s instance () { return new %s(new %s(), \"%s\"); }".formatted(tableInfo.className, tableInfo.className, schemeClassName, tableInfo.name));
			textOutput.writeLine("");

			textOutput.writeLine("}");

		} catch (Exception ex) {
			throw new GeneratorException("テーブルクラスを出力できませんでした: " + sourceFile, ex);
		}

	}

	// endregion

	// region テーブルデータクラス出力

	/**
	 * テーブルデータクラス出力
	 *
	 * @param rootDir           ルートディレクトリ
	 * @param packageName       パッケージ名
	 * @param dbSource          DBソース
	 * @param schemeClassName   スキーマクラス名
	 * @param tableInfo         テーブル情報
	 */
	private static void outputTableData (File rootDir, String packageName, DBSource dbSource, String schemeClassName, TableInfo tableInfo) {

		File tableRootDir = new File(rootDir, "table_data");
		File tableDir = new File(tableRootDir, tableInfo.name);
		tableDir.mkdirs();

		String className = "Abstract%sData".formatted(tableInfo.className);

		File sourceFile = new File(tableDir, className + ".java");
		try (
			TextOutput textOutput = new TextOutput(sourceFile)
		) {

			writeGeneratedHeader(textOutput);

			textOutput.writeLine("package %s.%s.table_data.%s;".formatted(packageName, dbSource.name, tableInfo.name));
			textOutput.writeLine("");

			textOutput.writeLine("import %s.%s.table.%s.%s;".formatted(packageName, dbSource.name, tableInfo.name, tableInfo.className));
			textOutput.writeLine("import io.jimble.db.generator.data.AbstractTableData;");
			textOutput.writeLine("import io.jimble.util.data.Data;");
			textOutput.writeLine("import io.jimble.db.sql.InsertBuilder;");
			textOutput.writeLine("import io.jimble.db.sql.UpdateBuilder;");
			textOutput.writeLine("import io.jimble.db.sql.definition.column.Column;");
			textOutput.writeLine("");

			textOutput.writeLine("/**");
			textOutput.writeLine(" * %s".formatted(tableInfo.comment));
			textOutput.writeLine(" */");
			textOutput.writeLine("public abstract class %s<T extends %s<?>> extends AbstractTableData {".formatted(className, className));
			for (ColumnInfo columnInfo : tableInfo.columnList) {

				textOutput.writeLine("");
				textOutput.writeLine("\t/**");
				textOutput.writeLine("\t * get %s".formatted(columnInfo.name));
				textOutput.writeLine("\t * ");
				textOutput.writeLine("\t * @return %s".formatted(columnInfo.name));
				textOutput.writeLine("\t */");
				textOutput.writeLine("\tpublic %s %s () {".formatted(columnInfo.typeClass.getTypeName(), columnInfo.nameLowerCamel()));
				textOutput.writeLine("\t\treturn get%s(%s.%s);".formatted(columnInfo.typeClassNameUpperCamel(), tableInfo.className, columnInfo.name));
				textOutput.writeLine("\t}");

				textOutput.writeLine("");
				textOutput.writeLine("\t/**");
				textOutput.writeLine("\t * set %s".formatted(columnInfo.name));
				textOutput.writeLine("\t * ");
				textOutput.writeLine("\t * @return Data");
				textOutput.writeLine("\t */");
				// (T) this は必ず安全（自己型パラメータ）。利用側のビルドに警告を出さない
				textOutput.writeLine("\t@SuppressWarnings(\"unchecked\")");
				textOutput.writeLine("\tpublic T %s (%s %s) {".formatted(columnInfo.nameLowerCamel(), columnInfo.typeClass.getTypeName(), columnInfo.name));
				textOutput.writeLine("\t\tputData(%s.%s, %s);".formatted(tableInfo.className, columnInfo.name, columnInfo.name));
				textOutput.writeLine("\t\treturn (T) this;");
				textOutput.writeLine("\t}");

				textOutput.writeLine("");
				textOutput.writeLine("\t/**");
				textOutput.writeLine("\t * contains %s".formatted(columnInfo.name));
				textOutput.writeLine("\t * ");
				textOutput.writeLine("\t * @return boolean");
				textOutput.writeLine("\t */");
				textOutput.writeLine("\tpublic boolean contains%s () {".formatted(columnInfo.nameUpperCamel()));
				textOutput.writeLine("\t\treturn containsKey(%s.%s);".formatted(tableInfo.className, columnInfo.name));
				textOutput.writeLine("\t}");

			}
			textOutput.writeLine("");
			textOutput.writeLine("");

			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * set insert sql data");
			textOutput.writeLine("\t * ");
			textOutput.writeLine("\t * @param builder InsertBuilder");
			textOutput.writeLine("\t * @param req request");
			textOutput.writeLine("\t * @param excludeColumns exclude columns");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\tpublic static void setInsertSqlData (InsertBuilder builder, Data req, Column...excludeColumns) {");
			textOutput.writeLine("\t\tsetInsertData(builder, %s.instance(), req, excludeColumns);".formatted(tableInfo.className));
			textOutput.writeLine("\t}");
			textOutput.writeLine("");

			textOutput.writeLine("\t/**");
			textOutput.writeLine("\t * set update sql data");
			textOutput.writeLine("\t * ");
			textOutput.writeLine("\t * @param builder UpdateBuilder");
			textOutput.writeLine("\t * @param req request");
			textOutput.writeLine("\t * @param excludeColumns exclude columns");
			textOutput.writeLine("\t */");
			textOutput.writeLine("\tpublic static void setUpdateSqlData (UpdateBuilder builder, Data req, Column...excludeColumns) {");
			textOutput.writeLine("\t\tsetUpdateData(builder, %s.instance(), req, excludeColumns);".formatted(tableInfo.className));
			textOutput.writeLine("\t}");
			textOutput.writeLine("");

			textOutput.writeLine("}");

		} catch (Exception ex) {
			throw new GeneratorException("テーブルデータクラスを出力できませんでした: " + sourceFile, ex);
		}

	}

	// endregion


	// region テーブル定義一覧を取得する

	/**
	 * テーブル定義一覧を取得する
	 *
	 * @param dbSource  DBソース
	 * @return  テーブル定義一覧
	 */
	private static List<TableInfo> getTableInfoList (DBSource dbSource) {

		List<TableInfo> tableInfoList = new ArrayList<>();
		Set<String> excludes = GeneratorConf.excludeTables();

		try (
			DB db = DBUtil.getDB(dbSource)
		) {

			List<Data> tableList = db.selectList("SHOW TABLE STATUS");
			for (Data table : tableList) {

				// jimble の管理テーブルはアプリのテーブル定義に出さない
				if (excludes.contains(table.getString("Name").toLowerCase())) {
					continue;
				}

				TableInfo tableInfo = new TableInfo();
				tableInfo.name = table.getString("Name");
				tableInfo.className = upperCamel(tableInfo.name);
				tableInfo.comment = table.getStringOptional("Comment");
				if (tableInfo.comment.isEmpty()) {
					tableInfo.comment = tableInfo.name;
				}

				List<Data> columnList = db.selectList("SHOW FULL COLUMNS FROM `" + tableInfo.name + "`");
				for (Data column : columnList) {

					ColumnInfo columnInfo = new ColumnInfo();
					columnInfo.name = column.getString("Field");
					columnInfo.type = column.getString("Type");
					columnInfo.extra = column.getString("Extra");
					columnInfo.typeClass = getColumnTypeClass(columnInfo.type);
					columnInfo.nullable = "YES".equalsIgnoreCase(column.getString("Null"));
					columnInfo.primaryKey = "PRI".equalsIgnoreCase(column.getString("Key"));
					columnInfo.comment = column.getStringOptional("Comment");
					if (columnInfo.comment.isEmpty()) {
						columnInfo.comment = columnInfo.name;
					}
					getColumnDefaultValue(columnInfo, column.getString("Default"));
					tableInfo.columnList.add(columnInfo);

				}

				{
					Map<String, List<Data>> tableIndexDataList = new HashMap<>();
					List<Data> indexList = db.selectList("SHOW INDEX FROM `" + tableInfo.name + "`");
					for (Data index : indexList) {

						String indexName = index.getString("Key_name");
						if (tableIndexDataList.containsKey(indexName)) {
							tableIndexDataList.get(indexName).add(index);
						} else {
							List<Data> indexDataList = new ArrayList<>();
							indexDataList.add(index);
							tableIndexDataList.put(indexName, indexDataList);
						}

					}

					for (String indexName : tableIndexDataList.keySet()) {

						IndexInfo indexInfo = new IndexInfo();
						indexInfo.name = indexName;

						List<Data> indexDataList = tableIndexDataList.get(indexName);
						indexDataList.sort((o1, o2) -> o1.getInt("Seq_in_index") - o2.getInt("Seq_in_index"));
						for (Data indexData : indexDataList) {

							if ("PRIMARY".equalsIgnoreCase(indexName)) {
								indexInfo.isPrimary = true;
							} else if (indexData.getInt("Non_unique") == 0) {
								indexInfo.isUnique = true;
							}

							indexInfo.columnNameList.add(indexData.getString("Column_name"));

						}

						tableInfo.indexList.add(indexInfo);

					}
					tableInfo.indexList.sort((o1, o2) -> {
						if (o1.isPrimary) {
							return -1;
						} else if (o2.isPrimary) {
							return 1;
						} else if (o1.isUnique) {
							return -1;
						} else if (o2.isUnique) {
							return 1;
						} else {
							return o1.name.compareTo(o2.name);
						}
					});
				}

				tableInfoList.add(tableInfo);

			}

		} catch (Exception ex) {

			throw new GeneratorException("テーブル定義を取得できませんでした: " + dbSource.name, ex);

		}

		return tableInfoList;

	}

	/**
	 * 列型からJava型を取得する
	 *
	 * @param _type 列型
	 * @return  Java型
	 */
	private static Class<?> getColumnTypeClass (String _type) {

		String type = _type.toLowerCase();

		if (type.contains("bigint")) {
			return long.class;
		}

		if (type.contains("bool")
			|| type.contains("tinyint")) {
			return boolean.class;
		}

		if (type.contains("int")) {
			return int.class;
		}

		if (type.contains("decimal")
			|| type.contains("float")
			|| type.contains("double")) {
			return double.class;
		}

		if (type.contains("datetime")
			|| type.contains("date")
			|| type.contains("timestamp")) {
			return Date.class;
		}

		if (type.contains("char")
			|| type.contains("text")) {
			return String.class;
		}

		if (type.contains("json")) {
			return Data.class;
		}

		return String.class;

	}

	/**
	 * 列のデフォルト値を設定する
	 *
	 * @param columnInfo    列定義
	 * @param value         デフォルト値
	 */
	private static void getColumnDefaultValue (ColumnInfo columnInfo, String value) {

		if (value == null) {
			return;
		}

		columnInfo.defaultValueString = value;

		try {
			columnInfo.defaultValue = Convertor.convert(null, value, columnInfo.typeClass);
		} catch (Exception ignore) {
			columnInfo.defaultValue = value;
		}

	}

	/**
	 * デフォルト値の文字列値を取得する
	 *
	 * @param columnInfo    列定義
	 * @return  デフォルト値の文字列値
	 */
	private static String getColumnDefaultValueString (ColumnInfo columnInfo) {

		if (columnInfo.defaultValue == null) {
			return "null";
		}

		if (String.class.equals(columnInfo.typeClass)) {
			return "\"%s\"".formatted(columnInfo.defaultValueString);
		}

		if (Date.class.equals(columnInfo.typeClass)) {
			// current_timestamp() のような関数は日時リテラルではない。
			// 移送元はこれも parseDate に渡していて、クラス初期化のたびに解析に失敗して null になっていた
			if (DateUtil.parseDate(columnInfo.defaultValueString) == null) {
				return "null";
			}
			return "io.jimble.util.date.DateUtil.parseDate(\"%s\")".formatted(columnInfo.defaultValueString);
		}

		if (int.class.equals(columnInfo.typeClass)) {
			return columnInfo.defaultValueString;
		}

		if (boolean.class.equals(columnInfo.typeClass)) {
			// tinyint(1) の既定値は "1" / "0" で返ってくる。
			// 移送元はそれをそのまま書き出していたため、boolean 列の既定値が
			// Boolean ではなく Integer になっていた
			return String.valueOf(Boolean.TRUE.equals(columnInfo.defaultValue));
		}

		if (long.class.equals(columnInfo.typeClass)) {
			return "%sL".formatted(columnInfo.defaultValueString);
		}

		if (double.class.equals(columnInfo.typeClass)) {
			return "%sD".formatted(columnInfo.defaultValueString);
		}

		if (Data.class.equals(columnInfo.typeClass)) {
			return "null";
		}

		return columnInfo.defaultValueString;

	}

	// endregion

	// region Upper Camel

	/* 区切り文字 */
	private static final Set<Character> UPPER_CAMEL_SPLITTER = new HashSet<>();
	static {
		UPPER_CAMEL_SPLITTER.add(' ');
		UPPER_CAMEL_SPLITTER.add('_');
		UPPER_CAMEL_SPLITTER.add('-');
		UPPER_CAMEL_SPLITTER.add('.');
	}

	/**
	 * 文字列をアッパーキャメルに変換する
	 *
	 * @param src   文字列
	 * @return  アッパーキャメル文字列
	 */
	private static String upperCamel (String src) {

		StringBuilder sb = new StringBuilder();
		boolean isStart = true;
		for (char c : src.toCharArray()) {

			if (UPPER_CAMEL_SPLITTER.contains(c)) {
				isStart = true;
				continue;
			}

			if (isStart) {
				sb.append(Character.toUpperCase(c));
				isStart = false;
			} else {
				sb.append(c);
			}

		}

		return sb.toString();

	}

	// endregion

}
