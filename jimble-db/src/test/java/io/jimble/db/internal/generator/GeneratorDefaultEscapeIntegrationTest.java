package io.jimble.db.internal.generator;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 既定値に {@code "} や {@code \} があっても、生成した Java がコンパイルできる
 *
 * <p>
 * スキーマクラスは表を作る SQL を Java の文字列（{@code sql.append("... default ...")}）として書き出す。
 * <b>既定値の SQL をエスケープせずに埋め込んでいた</b>ので、{@code jsonb} の既定値 {@code '{"a": 1}'::jsonb} の
 * {@code "} で文字列が閉じ、生成物がコンパイルできなかった。文字列の列の既定値の Java のリテラル（{@code "..."}）も同じだった。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class GeneratorDefaultEscapeIntegrationTest {

	/** 生成先 */
	@TempDir
	Path outputDir;

	/** 生成コードのパッケージ */
	private static final String PACKAGE = "gen";

	/** 表 */
	private static final String TABLE = "gen_default_escape";

	/** 文字列の列の既定値（{@code "} と {@code \} を含む） */
	private static final String NAME_DEFAULT = "say \"hi\" \\ bye";

	@BeforeAll
	static void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), GeneratorDefaultEscapeIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DROP TABLE IF EXISTS " + TABLE);

			if (db.dialect() instanceof PostgreSqlDialect) {
				// 他のセッションで起きた形（jsonb の既定値に "）
				db.execute("""
					CREATE TABLE gen_default_escape (
						id   bigint      not null primary key,
						name varchar(40) not null default 'say "hi" \\ bye',
						data jsonb       not null default '{"a": 1, "b": "x"}'::jsonb
					)
					""");
			} else {
				db.execute("""
					CREATE TABLE gen_default_escape (
						id   bigint      not null primary key,
						name varchar(40) not null default 'say "hi" \\\\ bye',
						data json        null default ('{"a": 1, "b": "x"}')
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
					""");
			}

		}

	}

	@AfterAll
	static void tearDown () {

		if (DBUtil.isUseDB()) {
			try (DB db = DBUtil.getMainDB()) {
				db.execute("DROP TABLE IF EXISTS " + TABLE);
			}
		}
		DBUtil.stop();

	}

	@Test
	@DisplayName("既定値に \" や \\ があっても、生成した Java がコンパイルでき、既定値も元の文字列に戻る")
	void generatedSourceCompiles () throws Exception {

		Generator.generate(outputDir.toFile(), PACKAGE);

		List<String> errors = GeneratedSources.compile(outputDir);
		assertTrue(errors.isEmpty(), "生成コードがコンパイルできない:\n" + String.join("\n", errors));

		try (URLClassLoader loader = new URLClassLoader(new URL[] { outputDir.resolve("__classes").toUri().toURL() }, getClass().getClassLoader())) {

			Class<?> table = Class.forName(PACKAGE + ".jimble_test.table." + TABLE + ".GenDefaultEscape", true, loader);
			io.jimble.db.sql.definition.column.Column name =
				(io.jimble.db.sql.definition.column.Column) table.getField("name").get(null);

			assertEquals(NAME_DEFAULT, name.defaultValue(), "文字列の列の既定値が元の文字列に戻らない");

			String schemaSql = (String) Class.forName(PACKAGE + ".jimble_test.JimbleTest", true, loader).getField("SchemaSQL").get(null);
			assertTrue(schemaSql.contains("\"a\""), "SchemaSQL に jsonb / json の既定値が元の形で入っていない: " + schemaSql);

		}

	}

}
