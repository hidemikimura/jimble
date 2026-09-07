package io.jimble.util.conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 設定はクラスパスから1つだけ読む（要件 D-80 / D-81）
 *
 * <p>
 * 見るのは3つ。<b>jar の外を見ないこと</b>、
 * <b>環境別ファイルが無ければ {@code application.conf} が効くこと</b>、
 * そして<b>共通を足すのはファイルの {@code include} であって
 * フレームワークではないこと</b>である。
 * </p>
 */
class ConfLoadTest {

	/** 前に使っていた「jar の外の conf/」を指すキー（もう効かないことを確かめる） */
	private static final String OLD_CONF_DIR = "jimble.conf.dir";

	@AfterEach
	void restore () {

		System.clearProperty(OLD_CONF_DIR);
		System.clearProperty(Conf.PROPERTY_ENV);
		System.clearProperty(Conf.KEY_ENV);
		Conf.reload();

	}

	/**
	 * 環境を決める
	 *
	 * @param env	環境
	 */
	private static void useEnv (String env) {

		System.setProperty(Conf.PROPERTY_ENV, env);
		Conf.reload();

	}

	@Test
	@DisplayName("D-78 -Djimble.env を読む（ドキュメントに書いてあるほう）")
	void envFromDocumentedProperty () {

		useEnv("staging");

		assertEquals("staging", Conf.env());

	}

	@Test
	@DisplayName("D-78 -Denv も読む（前からの書き方）")
	void envFromLegacyProperty () {

		System.setProperty(Conf.KEY_ENV, "staging");
		Conf.reload();

		assertEquals("staging", Conf.env());

	}

	@Test
	@DisplayName("D-78 両方あれば jimble.env が勝つ")
	void envPrefersDocumentedProperty () {

		System.setProperty(Conf.PROPERTY_ENV, "staging");
		System.setProperty(Conf.KEY_ENV, "other");
		Conf.reload();

		assertEquals("staging", Conf.env());

	}

	@Test
	@DisplayName("どちらも無ければ local")
	void envDefault () {

		Conf.reload();

		assertEquals("local", Conf.env());

	}

	@Test
	@DisplayName("D-80 環境別ファイルが無ければ application.conf を読む")
	void fallsBackToBase () {

		// application.nowhere.conf は無い
		useEnv("nowhere");

		assertEquals("classpath", Conf.conf().getString("conf.test.origin", ""));

	}

	@Test
	@DisplayName("D-80 環境別ファイルがあれば、同じキーはそちらが勝つ")
	void envFileWins () {

		useEnv("unittest");

		assertEquals("unittest", Conf.conf().getString("conf.test.origin", ""));

	}

	@Test
	@DisplayName("D-81 共通は環境別ファイルの include で入る")
	void includePullsInBase () {

		useEnv("unittest");

		/*
		 * application.unittest.conf の1行目に
		 *   include "application.conf"
		 * と書いてあるから入る。フレームワークが裏で足しているのではない。
		 */
		assertEquals("共通だけ", Conf.conf().getString("conf.test.base_only", ""));
		assertEquals("共通の秘密", Conf.conf().getString("common.secret", ""));

	}

	@Test
	@DisplayName("D-81 include が無ければ共通は入らない（裏で重ねない）")
	void withoutIncludeBaseIsNotMerged () {

		useEnv("noinclude");

		assertEquals("noinclude", Conf.conf().getString("conf.test.origin", ""));
		assertEquals("", Conf.conf().getString("common.secret", ""));

	}

	@Test
	@DisplayName("D-81 include の書き忘れは起動時に名指しで言える")
	void missingKeysAreReported () {

		useEnv("noinclude");

		Conf.conf().getString("conf.test.origin", "");

		// 名前順。conf.test.base_only も落ちている
		assertEquals(List.of("common", "conf"), Conf.missingFromEnvFile());

	}

	@Test
	@DisplayName("D-81 名前が同じでも中身が足りなければ気づける")
	void missingKeysLooksInside () {

		/*
		 * トップレベルだけを比べると、
		 * conf {} と common {} が「ある」ので見逃す。
		 */
		useEnv("partial");

		Conf.conf().getString("conf.test.origin", "");

		assertEquals(List.of("common", "conf"), Conf.missingFromEnvFile());
		assertEquals("", Conf.conf().getString("common.secret", ""));

	}

	@Test
	@DisplayName("D-81 include を書いていれば、落ちたキーは無い")
	void nothingMissingWithInclude () {

		useEnv("unittest");

		Conf.conf().getString("conf.test.origin", "");

		assertTrue(Conf.missingFromEnvFile().isEmpty(), Conf.missingFromEnvFile().toString());

	}

	@Test
	@DisplayName("D-80 jar の外の conf/ は読まない")
	void ignoresExternalDir (@TempDir Path temp) throws IOException {

		Files.writeString(temp.resolve("application.conf")
			, "conf { test { origin = \"外\" } }");

		System.setProperty(OLD_CONF_DIR, temp.toString());
		Conf.reload();

		assertEquals("classpath", Conf.conf().getString("conf.test.origin", ""));

	}

	@Test
	@DisplayName("D-81 読んだファイルが分かる（起動ログに出すため）")
	void sourcesAreVisible () {

		useEnv("unittest");

		// 読ませる
		Conf.conf().getString("conf.test.origin", "");

		/*
		 * 読むのは1つ。include で入ったものはここには出ない
		 * （それはファイルを見れば分かる）。
		 */
		assertEquals(1, Conf.sources().size(), Conf.sources().toString());
		assertTrue(Conf.sources().get(0).endsWith("application.unittest.conf"), Conf.sources().toString());

	}

	@Test
	@DisplayName("D-80 環境別が無ければ、読んだファイルは application.conf だけ")
	void sourcesWithoutEnvFile () {

		useEnv("nowhere");

		Conf.conf().getString("conf.test.origin", "");

		assertEquals(1, Conf.sources().size(), Conf.sources().toString());
		assertTrue(Conf.sources().get(0).endsWith("application.conf"), Conf.sources().toString());
		assertFalse(Conf.sources().get(0).contains("nowhere"), Conf.sources().toString());

	}

}
