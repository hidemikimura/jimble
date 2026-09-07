package io.jimble.util.conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jar の外の {@code conf/} を読む（要件 D-71）
 *
 * <p>
 * 設定を jar の中に固めてしまうと、<b>値を1つ変えるのにビルドが要る</b>。
 * 外に置けるようにしたぶん、<b>どちらが効いているか</b>を確かめる必要がある。
 * </p>
 */
class ConfDirTest {

	@AfterEach
	void restore () {

		System.clearProperty(Conf.KEY_CONF_DIR);
		System.clearProperty(Conf.KEY_ENV);
		Conf.reload();

	}

	/**
	 * 設定ファイルを書く
	 *
	 * @param dir		ディレクトリ
	 * @param name		ファイル名
	 * @param content	中身
	 */
	private static void write (Path dir, String name, String content) throws IOException {

		Files.writeString(dir.resolve(name), content);

	}

	/**
	 * このディレクトリを使う
	 *
	 * @param dir	ディレクトリ
	 */
	private static void useDir (Path dir) {

		System.setProperty(Conf.KEY_CONF_DIR, dir.toString());
		Conf.reload();

	}

	@Test
	@DisplayName("conf/ が無くても落ちない（クラスパスだけで動く）")
	void missingDirIsFine (@TempDir Path temp) {

		useDir(temp.resolve("nothing-here"));

		assertEquals("既定", Conf.conf().getString("nothing.at.all", "既定"));
		assertTrue(Conf.sources().isEmpty(), Conf.sources().toString());

	}

	@Test
	@DisplayName("conf/application.conf を読む")
	void readsExternalFile (@TempDir Path temp) throws IOException {

		write(temp, "application.conf", "sample { value = \"外\" }");

		useDir(temp);

		assertEquals("外", Conf.conf().getString("sample.value", ""));

	}

	@Test
	@DisplayName("conf/ はクラスパスより優先される")
	void externalWinsOverClasspath (@TempDir Path temp) throws IOException {

		/*
		 * クラスパスの application.conf（テスト用リソース）には
		 * conf.test.origin = "classpath" が入っている。
		 */
		assertEquals("classpath", Conf.conf().getString("conf.test.origin", ""));

		write(temp, "application.conf", "conf { test { origin = \"external\" } }");

		useDir(temp);

		assertEquals("external", Conf.conf().getString("conf.test.origin", ""));

	}

	@Test
	@DisplayName("環境別は基本ファイルより優先される（外でも中でも）")
	void envFileWins (@TempDir Path temp) throws IOException {

		write(temp, "application.conf", "sample { value = \"基本\" }");
		write(temp, "application.unittest.conf", "sample { value = \"環境別\" }");

		System.setProperty(Conf.KEY_ENV, "unittest");
		useDir(temp);

		assertEquals("環境別", Conf.conf().getString("sample.value", ""));

	}

	@Test
	@DisplayName("読んだファイルが分かる（起動ログに出すため）")
	void sourcesAreVisible (@TempDir Path temp) throws IOException {

		write(temp, "application.conf", "sample { value = \"外\" }");
		write(temp, "application.unittest.conf", "sample { other = 1 }");

		System.setProperty(Conf.KEY_ENV, "unittest");
		useDir(temp);

		// 読ませる
		Conf.conf().getString("sample.value", "");

		assertEquals(2, Conf.sources().size(), Conf.sources().toString());
		assertTrue(Conf.sources().get(0).endsWith("application.unittest.conf"), Conf.sources().toString());
		assertTrue(Conf.sources().get(1).endsWith("application.conf"), Conf.sources().toString());

	}

}
