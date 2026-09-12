package io.jimble.util.conf;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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


	// region 時間と大きさ（要件 D-159）

	@Test
	@DisplayName("D-159 単位つきの時間を読む")
	void durationWithUnit () {

		use(Map.of("a.wait", "30m", "a.short", "200ms", "a.long", "24h"));

		assertEquals(Duration.ofMinutes(30), Conf.conf().getDuration("a.wait", Duration.ZERO));
		assertEquals(Duration.ofMillis(200), Conf.conf().getDuration("a.short", Duration.ZERO));
		assertEquals(Duration.ofHours(24), Conf.conf().getDuration("a.long", Duration.ZERO));

		assertEquals(Duration.ofSeconds(5)
			, Conf.conf().getDuration("a.nothing", Duration.ofSeconds(5)), "既定値が返らない");

	}

	@Test
	@DisplayName("D-159 素の数値は落とす。直し方まで言う")
	void bareNumberIsRefused () {

		/*
		 * <b>ここが「秒とミリ秒の取り違え」の入口だった。</b>
		 * 単位をキーの名前に書いていたので、{@code assets.max_age = 3600000} は
		 * <b>そのまま通って 41 日のキャッシュ</b>になった。落ちも警告も出ない。
		 */
		use(Map.of("a.wait", 3600000));

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> Conf.conf().getDuration("a.wait", Duration.ZERO)
			, "単位が無いのに通しています");

		assertTrue(thrown.getMessage().contains("a.wait"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("30m"), "直し方が書かれていない: " + thrown.getMessage());
		assertTrue(thrown.getMessage().contains("3600000"), "いまの値が出ていない: " + thrown.getMessage());

	}

	@Test
	@DisplayName("D-159 単位つきの大きさを読む")
	void bytesWithUnit () {

		use(Map.of("a.size", "10MiB", "a.small", "512KiB"));

		assertEquals(10L * 1024 * 1024, Conf.conf().getBytes("a.size", 0));
		assertEquals(512L * 1024, Conf.conf().getBytes("a.small", 0));
		assertEquals(99, Conf.conf().getBytes("a.nothing", 99));

	}

	@Test
	@DisplayName("D-159 大きさも素の数値は落とす")
	void bareNumberIsRefusedForBytes () {

		use(Map.of("a.size", 10));

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> Conf.conf().getBytes("a.size", 0));

		assertTrue(thrown.getMessage().contains("10MiB"), thrown.getMessage());

	}

	@Test
	@DisplayName("バイト数を明示した B も通る")
	void bytesUnitB () {

		use(Map.of("a.size", "100B"));

		assertEquals(100, Conf.conf().getBytes("a.size", 0));

	}

	@Test
	@DisplayName("上下の限")
	void clamp () {

		assertEquals(Duration.ofSeconds(5)
			, Conf.atLeast(Duration.ofSeconds(1), Duration.ofSeconds(5)));
		assertEquals(Duration.ofSeconds(9)
			, Conf.atLeast(Duration.ofSeconds(9), Duration.ofSeconds(5)));

		assertEquals(Duration.ofSeconds(5)
			, Conf.clamp(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(9)));
		assertEquals(Duration.ofSeconds(9)
			, Conf.clamp(Duration.ofSeconds(99), Duration.ofSeconds(5), Duration.ofSeconds(9)));
		assertEquals(Duration.ofSeconds(7)
			, Conf.clamp(Duration.ofSeconds(7), Duration.ofSeconds(5), Duration.ofSeconds(9)));

	}

	// region ここで固定していないこと

	/*
	 * - <b>使える単位の綴り</b>は見ていない（{@code milliseconds} のような長い形も通る）。
	 *   そこは HOCON の仕事である
	 * - <b>既定値の側に単位が要るか</b>も見ていない。既定値は Java の {@code Duration} なので、
	 *   そもそも単位を取り違えようがない
	 */

	// endregion

	// endregion

}
