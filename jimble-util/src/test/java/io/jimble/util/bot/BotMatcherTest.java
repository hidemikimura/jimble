package io.jimble.util.bot;

import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一度に突き合わせる（D-287）
 *
 * <p>
 * <b>答えは「順に全部試す」と同じでなければならない。</b>同梱の一覧に入っている実際の名乗り（instances）全部と、
 * ふつうのブラウザ・作った名乗りで、2つのやり方の答えを比べる。
 * </p>
 */
class BotMatcherTest {

	private static List<Data> definitions () throws Exception {

		try (Reader reader = new InputStreamReader(
				BotUtil.class.getResourceAsStream("crawler-user-agents.json"), StandardCharsets.UTF_8)) {
			return Dson.decodes(reader, List.class, Data.class);
		}

	}

	@Test
	@DisplayName("D-287 同梱の一覧の実際の名乗り全部・ブラウザ・作った名乗りで、順に全部試すのと答えが同じ")
	void sameAsSequential () throws Exception {

		List<Data> list = definitions();
		List<String> patterns = new ArrayList<>();
		List<Pattern> compiled = new ArrayList<>();
		List<String> samples = new ArrayList<>();

		for (Data data : list) {
			patterns.add(data.getString("pattern"));
			compiled.add(Pattern.compile(data.getString("pattern")));
			samples.addAll(data.getStringList("instances"));
		}

		samples.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36");
		samples.add("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1");
		samples.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0");

		// 定義の一部を切り出した名乗り（ぎりぎり当たらない・当たるを混ぜる）
		Random random = new Random(1);
		for (String pattern : patterns) {
			String plain = pattern.replaceAll("\\\\(.)", "$1");
			int from = random.nextInt(Math.max(1, plain.length()));
			samples.add("Mozilla/5.0 (" + plain.substring(from) + ")");
			samples.add("Mozilla/5.0 (" + plain.substring(0, Math.max(0, plain.length() - 1)) + ")");
		}

		BotMatcher matcher = new BotMatcher(patterns);

		int bots = 0;

		for (String sample : samples) {

			boolean sequential = false;
			for (Pattern pattern : compiled) {
				if (pattern.matcher(sample).find()) {
					sequential = true;
					break;
				}
			}

			assertEquals(sequential, matcher.matches(sample), "答えが違う: " + sample);

			if (sequential) {
				bots++;
			}

		}

		assertTrue(bots > 2000, "実際の名乗りがボットと判定されていない: " + bots);
		assertFalse(matcher.matches(samples.get(samples.size() - patterns.size() * 2 - 1)), "ブラウザをボットにした");

	}

	@Test
	@DisplayName("必ず含む文字列の取り出し（迷ったら取らない）")
	void requiredLiteral () {

		assertEquals("Googlebot/", BotMatcher.literalOf("Googlebot\\/"));
		assertNull(BotMatcher.literalOf("^Seekbot"));
		assertEquals("Seekbot", BotMatcher.requiredLiteral("^Seekbot"));
		assertEquals("AdsBot-Google", BotMatcher.requiredLiteral("AdsBot-Google([^-]|$)"));
		assertEquals("rawler", BotMatcher.requiredLiteral("(sistrix|SISTRIX) [cC]rawler"));
		assertEquals("get", BotMatcher.requiredLiteral("[wW]get"));
		assertEquals("abcd", BotMatcher.requiredLiteral("abcde?x"));
		assertNull(BotMatcher.requiredLiteral("foo|bar"));
		assertNull(BotMatcher.requiredLiteral("(?i)googlebot"));
		assertNull(BotMatcher.requiredLiteral("a\\db"));

	}

}
