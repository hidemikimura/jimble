package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「同じ作りの DB を並べる」のページの設定の例が、そのまま読めて、書いてあるとおりになるか
 *
 * <p>
 * ページの例は、キーの書き間違い（起動で落ちる）や、ひな形の写し方の誤りに気づきにくい。
 * 例の {@code conf} のかたまりを取り出して、つながずに読む（{@link DBUtil#readDataSourceConfs(Config)}）。
 * </p>
 */
class MultiDbDocsExampleTest {

	/** 例のかたまり（先頭の行で見分ける） */
	private static final Pattern BLOCK = Pattern.compile("```conf\\n(#[^\\n]*\\n(?:(?!```)[\\s\\S])*?)```");

	@ParameterizedTest(name = "{0}")
	@CsvSource({ "docs/site/ja/multi-db.md, # 並べる設定の例", "docs/site/en/multi-db.md, # example of lined-up databases" })
	@DisplayName("ページの設定の例が、つながずに読めて、店の DB はひな形のとおり・主は書いたとおりになる")
	void exampleConfigReads (String page, String firstLine) throws IOException {

		Config config = ConfigFactory.parseString(example(page, firstLine)).resolve();

		Map<String, DBConf> confs = DBUtil.readDataSourceConfs(config);

		assertEquals(List.of("main", "shop_a", "shop_b"), List.copyOf(confs.keySet()), "ひな形や startup_parallelism がデータソースとして読まれた");

		for (String shop : new String[] { "shop_a", "shop_b" }) {
			DBConf conf = confs.get(shop);
			assertEquals(0, conf.minimumIdle(), shop);
			assertEquals(6, conf.maximumPoolSize(), shop);
			assertEquals("shop", conf.migrationDir(), shop);
			assertEquals("shop", conf.codegenName(), shop);
			assertEquals(Boolean.FALSE, conf.migrateOnStartup(), shop);
			assertFalse(conf.required(), shop);
			assertTrue(conf.mq(), shop);
			assertEquals(List.of("db_lock"), conf.frameworkTables(), shop);
			assertTrue(conf.url().endsWith("/" + shop), shop + " の url が上書きされていない");
		}

		DBConf main = confs.get("main");
		assertTrue(main.required());
		assertFalse(main.mq());

		assertEquals(8, config.getInt(DBUtil.KEY_STARTUP_PARALLELISM));
		assertEquals("redis", config.getString("mq.hint"));

	}

	/**
	 * ページから例のかたまりを取り出す
	 *
	 * @param page		ページ（リポジトリの根からの相対）
	 * @param firstLine	かたまりの先頭の行
	 * @return	中身
	 * @throws IOException	読めない場合
	 */
	private static String example (String page, String firstLine) throws IOException {

		String text = Files.readString(projectRoot().resolve(page), StandardCharsets.UTF_8);
		Matcher matcher = BLOCK.matcher(text);

		while (matcher.find()) {
			if (matcher.group(1).startsWith(firstLine)) {
				return matcher.group(1);
			}
		}

		throw new AssertionError(page + " に設定の例（" + firstLine + "）がありません");

	}

	private static Path projectRoot () {

		Path at = Path.of("").toAbsolutePath();

		while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
			at = at.getParent();
		}

		if (at == null) {
			throw new IllegalStateException("リポジトリの根が見つかりません");
		}

		return at;

	}

}
