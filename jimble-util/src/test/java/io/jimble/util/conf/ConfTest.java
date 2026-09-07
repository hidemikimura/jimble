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

}
