package io.jimble.util.conf;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 設定（要件 F-U-01〜03）
 */
class ConfTest {

	@AfterEach
	void reload () {

		Conf.reload();

	}

	/**
	 * その場で設定を差し替える
	 *
	 * @param values	設定
	 */
	private void use (Map<String, Object> values) {

		Conf.replace(ConfigFactory.parseMap(values));

	}

	@Test
	@DisplayName("既定値つきで取れる")
	void defaults () {

		use(Map.of("a.b", "x"));

		assertEquals("x", Conf.conf().getString("a.b", "default"));
		assertEquals("default", Conf.conf().getString("a.nothing", "default"));
		assertEquals(7L, Conf.conf().getLong("a.nothing", 7));
		assertTrue(Conf.conf().getBoolean("a.nothing", true));

	}

	@Test
	@DisplayName("キーの有無が分かる")
	void has () {

		use(Map.of("a.b", "x"));

		assertTrue(Conf.conf().has("a.b"));
		assertFalse(Conf.conf().has("a.c"));

	}

	@Test
	@DisplayName("足りない設定をまとめて1回で報告する")
	void requireReportsAllMissing () {

		/*
		 * 要件 F-U-03。1つ直しては起動して次で落ちる、を繰り返さないため。
		 */
		use(Map.of("db.main.url", "jdbc:...", "db.main.username", ""));

		IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
			Conf.conf().require("db.main.url", "db.main.username", "db.main.password", "session.secret"));

		String message = ex.getMessage();

		assertFalse(message.contains("db.main.url"), message);
		assertTrue(message.contains("db.main.username"), "空文字も足りない扱い: " + message);
		assertTrue(message.contains("db.main.password"), message);
		assertTrue(message.contains("session.secret"), message);

	}

	@Test
	@DisplayName("揃っていれば何も起きない")
	void requirePasses () {

		use(Map.of("a.b", "x", "a.c", "y"));

		Conf.conf().require("a.b", "a.c");
		Conf.conf().require();
		Conf.conf().require((String[]) null);

	}

	// region 環境の判定（要件 F-U-01 / D-155）

	/**
	 * 環境を差し替えて確かめる
	 *
	 * <p>
	 * <b>{@code Conf.reload()} を呼ばないと変わらない</b>——
	 * 環境はクラスを読み込んだときに1度だけ解決される。
	 * </p>
	 *
	 * @param value	環境
	 * @param check	確かめること
	 */
	private void withEnv (String value, Runnable check) {

		String before = System.getProperty(Conf.PROPERTY_ENV);

		try {

			System.setProperty(Conf.PROPERTY_ENV, value);
			Conf.reload();

			check.run();

		} finally {

			if (before == null) {
				System.clearProperty(Conf.PROPERTY_ENV);
			} else {
				System.setProperty(Conf.PROPERTY_ENV, before);
			}

			Conf.reload();

		}

	}

	@Test
	@DisplayName("D-155 prod でも isProduction() が true になる")
	void prodIsProduction () {

		/*
		 * <b>ここが false だったせいで、本番の分岐が丸ごと素通りしていた。</b>
		 *
		 * deploy.md は最初から `-Djimble.env=prod` と書いていて、
		 * コードは `"production"` としか一致していなかった。
		 * <b>どちらも「間違っている」ようには見えない</b>ので、
		 * `if (Conf.conf().isProduction())` を書いた人は
		 * <b>本番で通らないことに気づけない</b>——例外も警告も出ないからである。
		 */
		withEnv("prod", () -> {
			assertTrue(Conf.conf().isProduction(), "prod が本番として扱われていません");
			assertFalse(Conf.conf().isLocal());
			assertFalse(Conf.conf().isStaging());
		});

		withEnv("production", () -> assertTrue(Conf.conf().isProduction()));
		withEnv("PROD", () -> assertTrue(Conf.conf().isProduction(), "大文字が通っていません"));

	}

	@Test
	@DisplayName("D-155 stg / dev も同じものとして扱う")
	void otherAliases () {

		withEnv("stg", () -> assertTrue(Conf.conf().isStaging()));
		withEnv("stage", () -> assertTrue(Conf.conf().isStaging()));
		withEnv("dev", () -> assertTrue(Conf.conf().isLocal()));
		withEnv("development", () -> assertTrue(Conf.conf().isLocal()));

	}

	@Test
	@DisplayName("D-155 環境別ファイルの名前は、書いたとおりのままにする")
	void fileNameKeepsTheRawEnv () {

		/*
		 * <b>別名で受けるのは判定だけである。</b>
		 * ここまで正式名に直してしまうと、
		 * `-Djimble.env=prod` と書いた人が <b>application.production.conf を要求される</b>——
		 * <b>読むファイルが書いたとおりでなくなるほうが分かりにくい</b>。
		 */
		withEnv("prod", () -> assertEquals("prod", Conf.env(), "ファイル名に使う値まで変えています"));

	}

	@Test
	@DisplayName("D-155 知らない環境は、どれにも当たらない")
	void unknownEnvMatchesNothing () {

		/*
		 * <b>打ち間違いを local に倒さない。</b>
		 * 倒すと `producton` と書いた本番機が「ローカルです」と名乗ることになる。
		 * どれにも当たらないうえで、起動時に1度だけ警告を出す。
		 */
		withEnv("producton", () -> {
			assertFalse(Conf.conf().isProduction(), "打ち間違いが本番として通っています");
			assertFalse(Conf.conf().isLocal(), "打ち間違いがローカルに倒れています");
			assertFalse(Conf.conf().isStaging());
		});

	}

	// endregion

}
