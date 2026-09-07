package io.jimble.web.bot;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bot をブロックする（要件 F-W-14）
 *
 * <p>
 * 判定は {@code context.request().isBotAccess()}（User-Agent と IP を見る）。
 * <b>Bot だったときに何を返すかは宣言する。</b>
 * </p>
 *
 * <pre>
 * // 403 を返す
 * before(BotBlocker.forbidden());
 *
 * // 中身を出さずに 200 で返す（クロールはさせたいが負荷を掛けたくない）
 * before(BotBlocker.of(context -&gt; context.response().send("")));
 *
 * // 判定も差し替える
 * before(new BotBlocker(
 *     context -&gt; context.request().userAgent().ua.contains("Scrapy")
 *     , context -&gt; context.response().send(429)));
 * </pre>
 *
 * <p>
 * 検索エンジンまで弾くと困るので、<b>既定のハンドラは用意していない。</b>
 * 何を返すかは必ずアプリ側が決める。
 * </p>
 */
public final class BotBlocker implements Handler {

	/** ブロックのステータスコード */
	public static final int STATUS_CODE = 403;

	/* Bot 判定 */
	private final Predicate<WebContext> isBot;

	/* Bot だったときの処理 */
	private final Consumer<WebContext> onBot;

	/**
	 * コンストラクタ
	 *
	 * @param isBot	Bot 判定
	 * @param onBot	Bot だったときの処理
	 */
	public BotBlocker (Predicate<WebContext> isBot, Consumer<WebContext> onBot) {

		this.isBot = isBot;
		this.onBot = onBot;

	}

	/**
	 * 既定の判定で作る
	 *
	 * @param onBot	Bot だったときの処理
	 * @return	ハンドラ
	 */
	public static BotBlocker of (Consumer<WebContext> onBot) {

		return new BotBlocker(context -> context.request().isBotAccess(), onBot);

	}

	/**
	 * 403 を返す
	 *
	 * @return	ハンドラ
	 */
	public static BotBlocker forbidden () {

		return of(context -> context.response().send(STATUS_CODE));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		if (!isBot.test(context)) {
			return;
		}

		onBot.accept(context);

	}

}
