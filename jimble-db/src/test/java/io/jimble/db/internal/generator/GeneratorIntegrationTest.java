package io.jimble.db.internal.generator;

import io.jimble.db.FrameworkTables;

import io.jimble.db.TestDdl;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * コード生成が実 DB に対して動くことの確認
 *
 * <p>
 * <b>生成しただけでは意味がないので、生成した Java を実際にコンパイルする。</b>
 * 生成コードが参照する API が変わったときに、ここが赤くなる。
 * </p>
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。実行は次のとおり。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 */
@Tag("db")
class GeneratorIntegrationTest {

	/** 生成先 */
	@TempDir
	Path outputDir;

	/** 生成コードのパッケージ */
	private static final String PACKAGE = "gen";

	// region 準備

	@BeforeAll
	static void setUp () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), GeneratorIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS gen_item");
		TestDdl.execute(db, """
			CREATE TABLE gen_item (
				id         bigint unsigned auto_increment comment '商品ID' primary key,
				code       varchar(50)     not null comment '商品コード',
				name       varchar(250)    null comment '商品名',
				price      int unsigned    default 0 not null comment '価格',
				amount     decimal(10,2)   default 0 not null comment '金額,税込',
				is_active  tinyint(1)      default 1 not null comment '有効',
				note       text            null comment '備考',
				created_at datetime        default current_timestamp() not null comment '作成日時',
				constraint gen_item_code unique (code)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '商品'
			""");

		/*
		 * 一意キーとして数えてはいけないインデックス（PostgreSQL でだけ作れる）。
		 *   式インデックス   … 列が取れないので、残った列だけで一意だと誤解しうる
		 *   部分インデックス … 条件に合う行の中でしか一意でない
		 */
		if (!MySqlDialect.NAME.equals(db.dialect().name())) {
			db.execute("CREATE UNIQUE INDEX gen_item_lower ON gen_item (price, lower(name))");
			db.execute("CREATE UNIQUE INDEX gen_item_partial ON gen_item (note) WHERE is_active");
		}

	}

	@AfterAll
	static void tearDown () {

		if (DBUtil.isUseDB()) {
			DBUtil.getMainDB().execute("DROP TABLE IF EXISTS gen_item");
		}
		DBUtil.stop();

	}

	// endregion

	// region テスト

	@Test
	@DisplayName("テーブル定義クラスと型付きアクセサが生成される")
	void generate () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertTrue(source("table/gen_item/GenItem.java").exists());
		assertTrue(source("table_data/gen_item/AbstractGenItemData.java").exists());
		assertTrue(source("JimbleTest.java").exists(), "スキーマクラスが生成されていない");

	}

	@Test
	@DisplayName("D-88 生成物には「手で直すな」の印が入る（F-G-03）")
	void generatedHeader () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		/*
		 * 直しても次の codegen で消える。
		 * 消えることを知らずに直すと、直したはずの変更が黙って戻る。
		 */
		for (String path : new String[]{
			"JimbleTest.java"
			, "table/gen_item/GenItem.java"
			, "table_data/gen_item/AbstractGenItemData.java"
		}) {

			String text = read(source(path));

			assertTrue(text.startsWith("/*\n * このファイルは jimble が作りました（codegen）。手で直さないでください。")
				, path + " に印が無い: " + text.substring(0, Math.min(120, text.length())));

		}

	}

	@Test
	@DisplayName("一意キーが静的に出力される（D-94。SQL結果キャッシュが「どの行か」を決めるのに要る）")
	void staticUniqueKeys () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		/*
		 * SHOW INDEX では取っていたのに、Java には出していなかった。
		 * 主キーは Column.isPrimaryKey() で分かるので、ここには入れない。
		 */
		assertTrue(table.contains("private static final List<List<Column>> UNIQUE_KEYS = List.of(List.of(code));")
			, table);

		assertTrue(table.contains("protected List<List<Column>> declareUniqueKeys () { return UNIQUE_KEYS; }")
			, table);

	}

	@Test
	@DisplayName("列一覧が静的に出力される（D-17。実行時のリフレクションをしない）")
	void staticColumnList () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		assertTrue(table.contains("private static final List<Column> COLUMNS = List.of("), table);
		assertTrue(table.contains("protected List<Column> declareColumns () { return COLUMNS; }"), table);
		assertFalse(table.contains("instance().getColumnList()"), "リフレクション経由の列一覧が残っている");

	}

	@Test
	@DisplayName("列の型が Java の型に変換される")
	void columnTypes () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		assertTrue(table.contains("new Column(instance(), \"id\", long.class"), table);
		assertTrue(table.contains("new Column(instance(), \"code\", java.lang.String.class"), table);
		assertTrue(table.contains("new Column(instance(), \"price\", int.class"), table);
		// tinyint(1) は boolean
		assertTrue(table.contains("new Column(instance(), \"is_active\", boolean.class"), table);
		assertTrue(table.contains("new Column(instance(), \"created_at\", java.util.Date.class"), table);
		// decimal(10,2) は double（PostgreSQL は numeric として返る）
		assertTrue(table.contains("new Column(instance(), \"amount\", double.class"), table);

	}

	@Test
	@DisplayName("型名の中にカンマがあってもコメントを取り違えない")
	void commentWithComma () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		// decimal(10,2) のカンマで切ってしまうと、この列のコメントだけが消える
		assertTrue(table.contains("/* 金額,税込 */"), table);

	}

	@Test
	@DisplayName("式インデックスと部分インデックスは一意キーに数えない")
	void unsafeUniqueIndexes () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		/*
		 * 式インデックスは残った列だけで一意だと誤解しうる（price は一意ではない）。
		 * 部分インデックスは条件に合う行の中でしか一意でない。
		 * どちらも「どの行か」を決める根拠にはできない（要件 F-D-28）。
		 */
		assertTrue(table.contains("UNIQUE_KEYS = List.of(List.of(code));"), table);

	}

	@Test
	@DisplayName("既定値が Java の型に合った形で出力される")
	void columnDefaultValues () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		String table = read(source("table/gen_item/GenItem.java"));

		// tinyint(1) の既定値 "1" は Integer ではなく Boolean にする
		assertTrue(table.contains("\"is_active\", boolean.class, false, true,"), table);
		// current_timestamp() は日時リテラルではないので null
		assertTrue(table.contains("\"created_at\", java.util.Date.class, false, null,"), table);
		assertFalse(table.contains("parseDate(\"current_timestamp"), "関数の既定値を日時として解析しようとしている");

	}

	@Test
	@DisplayName("jimble の管理テーブルは生成対象から外れる")
	void excludeFrameworkTables () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		for (String table : FrameworkTables.ALL) {
			assertFalse(source("table/" + table).exists(), table + " が生成されている");
		}

		String schema = read(source("JimbleTest.java"));
		assertFalse(schema.contains("db_lock"), "スキーマクラスに管理テーブルが載っている");

	}

	@Test
	@DisplayName("消えたテーブルのクラスは次の生成で消える")
	void cleanStaleSource () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		File stale = source("table/gen_gone/GenGone.java");
		stale.getParentFile().mkdirs();
		write(stale, "package gen.jimble_test.table.gen_gone; public class GenGone {}");
		assertTrue(stale.exists());

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertFalse(stale.exists(), "消えたテーブルのクラスが残っている");

	}

	@Test
	@DisplayName("生成した Java が実際にコンパイルできる")
	void generatedSourceCompiles () {

		Generator.generate(outputDir.toFile(), PACKAGE);

		List<String> errors = compile(outputDir);

		assertTrue(errors.isEmpty(), "生成コードがコンパイルできない:\n" + String.join("\n", errors));

	}

	// endregion

	// region ヘルパー

	/**
	 * 生成された Java をコンパイルする
	 *
	 * <p>クラスパスはこのテストが動いているものをそのまま使う。</p>
	 *
	 * @param sourceRoot	生成先
	 * @return	エラーメッセージ（無ければ空）
	 */
	private List<String> compile (Path sourceRoot) {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			// JRE で動かしている場合。ここは JDK 前提なので起きない
			throw new IllegalStateException("javac が使えません（JDK で実行してください）");
		}

		List<File> sources = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(sourceRoot)) {
			paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> sources.add(p.toFile()));
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

		assertFalse(sources.isEmpty(), "生成された Java がない");

		File classesDir = sourceRoot.resolve("__classes").toFile();
		classesDir.mkdirs();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

			List<String> options = List.of(
				"-classpath", System.getProperty("java.class.path")
				, "-d", classesDir.getAbsolutePath()
				, "-encoding", "UTF-8"
				, "-nowarn"
			);

			compiler.getTask(
				null
				, fileManager
				, diagnostics
				, options
				, null
				, fileManager.getJavaFileObjectsFromFiles(sources)
			).call();

		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

		List<String> errors = new ArrayList<>();
		diagnostics.getDiagnostics().stream()
			.filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
			.forEach(d -> errors.add(d.toString()));

		return errors;

	}

	/**
	 * 生成物のファイル
	 *
	 * @param relative	スキーマディレクトリからの相対パス
	 * @return	ファイル
	 */
	private File source (String relative) {

		return outputDir.resolve(DBUtil.getMainDataSource().name.toLowerCase()).resolve(relative).toFile();

	}

	/**
	 * ファイルを読む
	 *
	 * @param file	ファイル
	 * @return	中身
	 */
	private String read (File file) {

		try {
			return Files.readString(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * ファイルを書く
	 *
	 * @param file		ファイル
	 * @param content	中身
	 */
	private void write (File file, String content) {

		try {
			Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

	}

	// endregion

}
