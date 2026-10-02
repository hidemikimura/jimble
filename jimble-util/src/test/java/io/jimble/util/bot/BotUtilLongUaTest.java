package io.jimble.util.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 長い User-Agent で CPU とキャッシュを使わせない（D-232）
 */
class BotUtilLongUaTest {

	@Test
	@DisplayName("先頭の 512 文字だけを見る。キャッシュのキーも 512 文字まで")
	void truncates () throws Exception {

		String chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

		assertFalse(BotUtil.isBot("203.0.113.1", chrome + " " + "x".repeat(16_000)));
		assertTrue(BotUtil.isBot("203.0.113.1", "Googlebot/2.1 (+http://www.google.com/bot.html) " + "x".repeat(16_000)));

		java.lang.reflect.Field field = BotUtil.class.getDeclaredField("uaResultMap");
		field.setAccessible(true);
		Map<?, ?> cache = (Map<?, ?>) field.get(null);

		for (Object key : cache.keySet()) {
			assertTrue(key.toString().length() <= BotUtil.MAX_UA_LENGTH, "長い UA をそのままキャッシュに入れています");
		}

	}

}
