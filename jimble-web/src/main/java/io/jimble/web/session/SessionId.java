package io.jimble.web.session;

import io.jimble.util.string.StringUtil;
import io.jimble.web.context.WebContext;

/**
 * セッション ID の発行
 *
 * <p>
 * Cookie（既定 {@code sid}）に置く。<b>要求されて初めて発行する。</b>
 * </p>
 *
 * <p>
 * 移送元はリクエストのたびに無条件で {@code cid} と {@code sid} を発行しており、
 * <b>セッションを使わないページにも Cookie が付いていた</b>（要件 F-S-12）。
 * </p>
 */
public final class SessionId {

	private SessionId () {}

	/**
	 * セッション ID を取り出す。無ければ発行する
	 *
	 * @param context	コンテキスト
	 * @return	セッション ID
	 */
	public static String getOrCreate (WebContext context) {

		String name = SessionConf.cookieName();

		String sessionId = context.cookies().get(name);

		if (sessionId != null && !sessionId.isEmpty()) {

			resignIfStale(context);

			return sessionId;

		}

		sessionId = StringUtil.uniqueString();
		context.cookies().put(name, sessionId, SessionConf.timeout().toSeconds());

		return sessionId;

	}

	/**
	 * 古い鍵で署名されたセッション ID を、今の鍵で署名し直す（要件 NF-S-09）
	 *
	 * <p>
	 * ここで書き直さないと、この人が来るたびに古い鍵で読み続けることになり、
	 * <b>入れ替えが終わらない</b>。ID が無ければ何もしない（発行はしない）。
	 * </p>
	 *
	 * <p>
	 * 有効期限は<b>発行するときと同じもの</b>を使う。
	 * 既定の有効期限（1年）で書き直すと、
	 * <b>30分で切れるはずの Cookie が1年ブラウザに残る</b>
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @since 2.7.6
	 */
	public static void resignIfStale (WebContext context) {

		String name = SessionConf.cookieName();

		String sessionId = context.cookies().get(name);

		if (sessionId != null && !sessionId.isEmpty() && context.cookies().isStale(name)) {
			context.cookies().put(name, sessionId, SessionConf.timeout().toSeconds());
		}

	}

	/**
	 * セッション ID を取り出す（発行しない）
	 *
	 * @param context	コンテキスト
	 * @return	セッション ID（無ければ空文字）
	 */
	public static String get (WebContext context) {

		return context.cookies().get(SessionConf.cookieName());

	}

	/**
	 * セッション ID の Cookie を消す
	 *
	 * @param context	コンテキスト
	 */
	public static void remove (WebContext context) {

		context.cookies().remove(SessionConf.cookieName());

	}

}
