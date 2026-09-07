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
			return sessionId;
		}

		sessionId = StringUtil.uniqueString();
		context.cookies().put(name, sessionId, SessionConf.timeoutMinutes() * 60);

		return sessionId;

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
