package io.jimble.util.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOT 判定（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code context.request().isBotAccess()} がここを呼んでいる。</b>
 * アクセスログを {@code access} と {@code access.bot} に分ける判断も、
 * {@code BotBlocker} の判断も、全部これ1本である——<b>なのにテストが無かった</b>。
 * </p>
 *
 * <p>
 * <b>外し方が両方向とも痛い。</b>
 * 人を BOT と見れば締め出し、BOT を人と見れば<b>人のアクセス数が水増しされる</b>。
 * どちらも例外は出ない。
 * </p>
 *
 * <p>
 * <b>いちばん静かな壊れ方は「定義が0件のまま動くこと」である。</b>
 * 移送直後は実際にそうなっていて（リソースが同梱されておらず、
 * 起動のたびにエラーが1行出るだけ）、<b>全員が人として数えられていた</b>。
 * </p>
 *
 * <p>
 * <b>初代の {@code BotUtil} は、それを直さないまま残っていた</b>——
 * 誰も呼んでいなかったので消し、<b>2代目がこの名前を継いだ</b>（要件 D-164）。
 * </p>
 */
class BotUtilTest {

	/** よくあるブラウザの名乗り */
	private static final String BROWSER =
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
			+ " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

	/** IP（判定に使うのは名乗りのほう） */
	private static final String IP = "203.0.113.1";

	@Test
	@DisplayName("知られた巡回は BOT と分かる")
	void knownCrawlersAreFound () {

		/*
		 * <b>ここが false になったら、定義が読めていない。</b>
		 * 0件のまま動くのがいちばん静かな壊れ方なので、
		 * <b>代表を何本か当てて「一覧が入っていること」を見る</b>。
		 */
		assertTrue(BotUtil.isBot(IP, "Googlebot/2.1 (+http://www.google.com/bot.html)"));
		assertTrue(BotUtil.isBot(IP, "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"));
		assertTrue(BotUtil.isBot(IP, "curl/8.4.0"));

	}

	@Test
	@DisplayName("ふつうのブラウザは BOT ではない")
	void anOrdinaryBrowserIsNotABot () {

		assertFalse(BotUtil.isBot(IP, BROWSER));

		assertFalse(BotUtil.isBot(IP
			, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
				+ " (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"));

	}

	@Test
	@DisplayName("名乗らない相手は BOT とみなす")
	void anEmptyUserAgentIsABot () {

		/*
		 * <b>ここは「閉じる」側に倒してある。</b>
		 * User-Agent を送らないふつうのブラウザは無い。
		 */
		assertTrue(BotUtil.isBot(IP, ""));
		assertTrue(BotUtil.isBot(IP, null));

	}

	@Test
	@DisplayName("IP が分からなければ判定しない")
	void withoutAnIpNothingIsDecided () {

		/*
		 * <b>ここは逆に「開く」側である。</b>
		 * IP が取れないのは<b>こちらの都合</b>（前段の設定漏れなど）なので、
		 * 相手のせいにしない——<b>名乗らなくても BOT にはしない</b>。
		 */
		assertFalse(BotUtil.isBot(null, BROWSER));
		assertFalse(BotUtil.isBot("", BROWSER));
		assertFalse(BotUtil.isBot(null, ""));

	}

	@Test
	@DisplayName("同じ名乗りは2回目から覚えている")
	void theAnswerIsRemembered () {

		/*
		 * 名乗りごとに<b>1000本近いパターンを順に当てる</b>ので、
		 * 覚えていないと1リクエストごとにその総当たりが走る。
		 * <b>答えが変わらないこと</b>だけを見る（速さは測らない）。
		 */
		String ua = "Mozilla/5.0 (compatible; SomeUnknownAgent/1.0)";

		boolean first = BotUtil.isBot(IP, ua);

		assertFalse(first);
		assertFalse(BotUtil.isBot(IP, ua), "2回目で答えが変わりました");

	}

	// region ここで固定していないこと

	/*
	 * - <b>一覧に何が載っているか</b>は固定していない。
	 *   {@code crawler-user-agents.json} は増える一方なので、
	 *   <b>ここで並べると更新のたびに落ちる</b>。当てているのは
	 *   <b>まず消えない代表</b>（Googlebot / bingbot / curl）だけである
	 * - <b>覚えておく数の上限</b>（10000）を超えたときの振る舞いも見ていない
	 * - <b>IP による判定</b>は無い。{@code BotUtil} が見るのは名乗りだけで、
	 *   IP は「分かっているか」の確認にしか使っていない
	 */

	// endregion

}
