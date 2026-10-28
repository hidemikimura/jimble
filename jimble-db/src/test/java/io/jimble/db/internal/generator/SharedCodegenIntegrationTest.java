package io.jimble.db.internal.generator;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同じ表の作りの DB を何本もデータソースにしても、生成コードを 1 組だけ持つ（{@code codegen_name}・{@code codegen}）
 *
 * <p>
 * 主（{@code jimble_test}）ともう1つ（{@code jimble_test_sub}）に同じ {@code codegen_name} を付ける。
 * 生成は代表（主）からの 1 組だけで、生成したテーブルクラスは、どちらの DB に渡してもその DB で動く。
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class SharedCodegenIntegrationTest {

	/** 生成先 */
	@TempDir
	Path outputDir;

	/** 生成コードのパッケージ */
	private static final String PACKAGE = "gen";

	/** 主 */
	private static final String MAIN = "jimble_test";

	/** もう1つ */
	private static final String OTHER = "jimble_test_sub";

	/** 両方に作る表 */
	private static final String TABLE = "share_item";

	@AfterEach
	void resetLog () {

		Log.resetSink();

	}

	@AfterAll
	static void tearDown () {

		load("");
		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + TABLE);
			}
		}
		DBUtil.stop();
		Conf.reload();

	}

	@Test
	@DisplayName("同じ codegen_name のデータソースは、代表（主）から 1 組だけ生成する")
	void oneSetForSharedName () {

		load(shared());

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertEquals(List.of("shop"), folders(), "1 組だけになっていない");
		assertTrue(source("shop/table/share_item/ShareItem.java").exists());

		String schema = read(source("shop/Shop.java"));
		assertTrue(schema.contains("package gen.shop;"), schema);
		assertTrue(schema.contains("DBUtil.getDB(\"" + MAIN + "\")"), "db() が代表（主）に結び付いていない");

		// スキーマの実体の名前は、代表のデータソース名（AbstractSchema.dbSource() が引く）
		assertTrue(read(source("shop/ShopSchema.java")).contains("return \"" + MAIN + "\";"));

	}

	@Test
	@DisplayName("生成したテーブルクラスを、代表でないデータソースの DB で実行すると、その DB に流れる")
	void sharedTableRunsOnEitherDatabase () throws Exception {

		load(shared());

		try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
			main.execute("INSERT INTO " + TABLE + " (id, name) VALUES (1, 'main')");
			other.execute("INSERT INTO " + TABLE + " (id, name) VALUES (1, 'other')");
		}

		Generator.generate(outputDir.toFile(), PACKAGE);

		List<String> errors = GeneratedSources.compile(outputDir);
		assertTrue(errors.isEmpty(), String.join("\n", errors));

		try (URLClassLoader loader = new URLClassLoader(new URL[] { outputDir.resolve("__classes").toUri().toURL() }, getClass().getClassLoader())) {

			Table table = (Table) Class.forName(PACKAGE + ".shop.table.share_item.ShareItem", true, loader)
				.getMethod("instance").invoke(null);

			try (DB main = DBUtil.getDB(MAIN); DB other = DBUtil.getDB(OTHER)) {
				assertEquals("main", name(main.selectList(SQL.select().from(table))));
				assertEquals("other", name(other.selectList(SQL.select().from(table))), "代表でない DB に流れていない");
			}

		}

	}

	@Test
	@DisplayName("codegen = false のデータソースは生成しない")
	void codegenFalse () {

		load("db.jimble_test_sub.codegen = false");

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertEquals(List.of(MAIN), folders());

	}

	@Test
	@DisplayName("書かなければ、これまでどおりデータソースごとに生成する")
	void defaultIsPerDataSource () {

		load("");

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertEquals(List.of(MAIN, OTHER), folders());

	}

	@Test
	@DisplayName("表の作りが代表と違えば警告する（生成は続ける）")
	void warnsWhenShapeDiffers () {

		load(shared());

		try (DB other = DBUtil.getDB(OTHER)) {
			other.execute("ALTER TABLE " + TABLE + " ADD COLUMN extra int null");
		}

		List<String> warnings = new ArrayList<>();
		Log.sink((logger, level, message, data, throwable) -> {
			if (level == Level.WARN) {
				warnings.add(message);
			}
		});

		Generator.generate(outputDir.toFile(), PACKAGE);

		assertTrue(warnings.stream().anyMatch(message -> message.contains(OTHER) && message.contains(TABLE)), warnings.toString());
		assertEquals(List.of("shop"), folders());

	}

	@Test
	@DisplayName("codegen_name はパッケージ名とフォルダ名になるので、小文字で始まる小文字・数字・_ だけ")
	void invalidCodegenName () {

		load("db.jimble_test.codegen_name = \"Shop\"");

		assertThrows(GeneratorException.class, () -> Generator.generate(outputDir.toFile(), PACKAGE));

	}

	// region ヘルパー

	/**
	 * 2つに同じ codegen_name を付ける設定
	 *
	 * @return	設定
	 */
	private static String shared () {

		return """
			db.jimble_test.codegen_name = "shop"
			db.jimble_test_sub.codegen_name = "shop"
			""";

	}

	/**
	 * 設定を足して読み込み直し、両方の DB に同じ表を作り直す
	 *
	 * @param hocon	足す設定
	 */
	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon + "\nmigration.on_startup = false").withFallback(Conf.conf().config()));
		DBUtil.load(Conf.conf().config(), SharedCodegenIntegrationTest.class);

		for (String name : new String[] { MAIN, OTHER }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS " + TABLE);
				TestDdl.execute(db, """
					CREATE TABLE share_item (
						id   int          not null primary key,
						name varchar(50)  not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
					""");
			}
		}

	}

	/**
	 * 生成先のフォルダ（名前の順）
	 *
	 * @return	フォルダ名
	 */
	private List<String> folders () {

		File[] dirs = outputDir.toFile().listFiles(File::isDirectory);

		return dirs == null ? List.of() : Arrays.stream(dirs).map(File::getName).filter(name -> !name.startsWith("__")).sorted().toList();

	}

	private File source (String relative) {

		return outputDir.resolve(relative).toFile();

	}

	private static String read (File file) {

		try {
			return Files.readString(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * 1行だけ返った結果の name
	 *
	 * @param rows	結果
	 * @return	name
	 */
	private static String name (List<Data> rows) {

		assertEquals(1, rows.size(), rows.toString());
		assertFalse(rows.getFirst().isEmpty());

		return rows.getFirst().getData(TABLE).getString("name");

	}

	// endregion

}
