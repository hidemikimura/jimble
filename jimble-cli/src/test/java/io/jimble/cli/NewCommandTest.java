package io.jimble.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code jimble new} のテスト（要件 F-X-01）
 *
 * <p>
 * <b>生成したものが実際にビルドできて起動するところ</b>は、
 * ここでは見られない（Gradle と Maven リポジトリが要る）。
 * それは実機で確かめている（{@code docs/design-m8.md} 6.4）。
 * ここで固定するのは「何が、どこに、どんな中身で出るか」である。
 * </p>
 */
class NewCommandTest {

	@Test
	@DisplayName("そのまま動くプロジェクトの中身が全部出る")
	void generates (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		assertEquals("my-blog", root.getFileName().toString());

		for (String path : new String[]{
			"settings.gradle.kts"
			, "build.gradle.kts"
			, "gradle.properties"
			, ".gitignore"
			, "README.md"
			, "src/main/java/myblog/App.java"
			, "src/main/jte/myblog/index.jte"
			, "conf/application.conf"
			, "conf/logback.xml"
		}) {
			assertTrue(Files.isRegularFile(root.resolve(path)), path);
		}

	}

	@Test
	@DisplayName("置き換えの印が残らない")
	void noPlaceholdersLeft (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		try (var paths = Files.walk(root)) {

			paths.filter(Files::isRegularFile).forEach(path -> {

				String text;
				try {
					text = Files.readString(path, StandardCharsets.UTF_8);
				} catch (IOException ex) {
					throw new AssertionError(path.toString(), ex);
				}

				/*
				 * 置き換え漏れは「生成したプロジェクトがコンパイルできない」形で出る。
				 * 出たものを全部見て、印が1つも残っていないことを確かめる。
				 */
				assertFalse(text.contains("__"), "置き換え漏れ: " + path + "\n" + text);

			});

		}

	}

	@Test
	@DisplayName("パッケージと mainClass が揃っている")
	void packageMatchesMainClass (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		String app = Files.readString(root.resolve("src/main/java/myblog/App.java"));
		String build = Files.readString(root.resolve("build.gradle.kts"));

		assertTrue(app.startsWith("package myblog;"), app.substring(0, 40));
		assertTrue(build.contains("mainClass = \"myblog.App\""), build);

		// テンプレートの置き場所も同じところを指す
		String appBody = app;
		assertTrue(appBody.contains("\"myblog/index.jte\""), appBody);

	}

	@Test
	@DisplayName("依存の版は jar の版と揃う")
	void versionMatches (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		String build = Files.readString(root.resolve("build.gradle.kts"));

		/*
		 * ここがずれると、生成したプロジェクトが
		 * 解決できない依存を持つことになる。
		 */
		assertTrue(build.contains("io.jimble:jimble-web:" + Version.current()), build);

	}

	@Test
	@DisplayName("すでにあるものは上書きしない")
	void doesNotOverwrite (@TempDir Path dir) throws IOException {

		NewCommand.run("my-blog", dir);

		// 書きかけのコードが消えることのないように
		IOException ex = assertThrows(IOException.class, () -> NewCommand.run("my-blog", dir));

		assertTrue(ex.getMessage().contains("すでにあります"), ex.getMessage());

	}

	@Test
	@DisplayName("DB 名は名前から決まる")
	void databaseName (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		assertTrue(Files.isDirectory(root.resolve("conf/migration/my_blog")));

		String conf = Files.readString(root.resolve("conf/application.conf"));
		assertTrue(conf.contains("my_blog"), conf);

	}

	@Test
	@DisplayName("マイグレーションは置いただけでは流れない")
	void migrationIsAnExample (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		/*
		 * DB を使わない人が jimble new しただけで
		 * マイグレーションが流れると驚く。拡張子で止めてある。
		 */
		assertTrue(Files.isRegularFile(
			root.resolve("conf/migration/my_blog/001_create_note.sql.example")));

		assertFalse(Files.exists(
			root.resolve("conf/migration/my_blog/001_create_note.sql")));

	}

}
