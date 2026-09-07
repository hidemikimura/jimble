package io.jimble.util.bot;

import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ボット判定（要件 F-W-14 / F-H-05）
 */
class BotUtilTest {

	@AfterEach
	void resetLog () {

		Log.resetSink();

	}

	@Test
	@DisplayName("ボットの定義を読める")
	void definitionIsBundled () {

		/*
		 * 移送元は「クラスパスに無ければ jar の場所から
		 * ../../../resources/main/lib/base/util/common/bot/ を辿る」形だった。
		 * 移送先にそのパスは無いので、起動のたびにエラーを1行出して
		 * パターン0件のまま動いていた。ボット判定が効いていないことに気づけない。
		 */
		List<String> errors = new ArrayList<>();
		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level == org.slf4j.event.Level.ERROR) {
				errors.add(String.valueOf(message));
			}
		});

		assertTrue(BotUtil2.isBot("192.0.2.1", "Googlebot/2.1 (+http://www.google.com/bot.html)"));

		assertTrue(errors.isEmpty(), "定義の読み込みでエラーが出ている: " + errors);

	}

	@Test
	@DisplayName("よく知られたボットを見分ける")
	void knownBots () {

		for (String userAgent : List.of(
			"Googlebot/2.1 (+http://www.google.com/bot.html)"
			, "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"
			, "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)"
			, "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")) {

			assertTrue(BotUtil2.isBot("192.0.2.1", userAgent), userAgent);

		}

	}

	@Test
	@DisplayName("User-Agent が無ければボット扱い")
	void emptyUserAgent () {

		assertTrue(BotUtil2.isBot("192.0.2.1", ""));
		assertTrue(BotUtil2.isBot("192.0.2.1", null));

	}

	@Test
	@DisplayName("人のブラウザはボットにしない")
	void notBots () {

		for (String userAgent : List.of(
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
				+ " Chrome/140.0.0.0 Safari/537.36"
			, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
				+ " Version/18.0 Mobile/15E148 Safari/604.1")) {

			assertFalse(BotUtil2.isBot("192.0.2.1", userAgent), userAgent);

		}

	}

}
