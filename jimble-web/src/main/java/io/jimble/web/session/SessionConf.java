package io.jimble.web.session;

import io.jimble.db.FrameworkTables;
import io.jimble.util.conf.Conf;

/**
 * セッションの設定
 *
 * <pre>
 * session {
 *   store           = "none"    # none | db | redis | cookie
 *   timeout_minutes = 30
 *   cookie_name     = "sid"
 *   table           = "session" # store = db のとき
 *   secret          = "..."     # store = cookie のとき（暗号鍵。必須）
 * }
 * </pre>
 *
 * <p>
 * <b>既定は {@code none}。</b>セッションを使わないアプリに Cookie を発行させないため（要件 F-S-12）。
 * </p>
 */
public final class SessionConf {

	/** 設定キー：保存先 */
	public static final String KEY_STORE = "session.store";

	/** 設定キー：タイムアウト（分） */
	public static final String KEY_TIMEOUT_MINUTES = "session.timeout_minutes";

	/** 設定キー：セッション ID の Cookie 名 */
	public static final String KEY_COOKIE_NAME = "session.cookie_name";

	/** 設定キー：テーブル名 */
	public static final String KEY_TABLE = "session.table";

	/** 設定キー：Cookie セッションの暗号鍵 */
	public static final String KEY_SECRET = "session.secret";

	/** 既定のタイムアウト（分） */
	public static final long DEFAULT_TIMEOUT_MINUTES = 30;

	/** 既定の Cookie 名 */
	public static final String DEFAULT_COOKIE_NAME = "sid";

	/** 既定のテーブル名 */
	public static final String DEFAULT_TABLE = FrameworkTables.SESSION;

	private SessionConf () {}

	/**
	 * 保存先の名前
	 *
	 * @return	{@code none} / {@code db} / {@code redis} / {@code cookie}
	 */
	public static String store () {

		return Conf.conf().getString(KEY_STORE, "none");

	}

	/**
	 * タイムアウト（分）
	 *
	 * @return	分
	 */
	public static long timeoutMinutes () {

		return Conf.conf().getLong(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);

	}

	/**
	 * セッション ID の Cookie 名
	 *
	 * @return	Cookie 名
	 */
	public static String cookieName () {

		return Conf.conf().getString(KEY_COOKIE_NAME, DEFAULT_COOKIE_NAME);

	}

	/**
	 * テーブル名
	 *
	 * @return	テーブル名
	 */
	public static String table () {

		return Conf.conf().getString(KEY_TABLE, DEFAULT_TABLE);

	}

	/**
	 * Cookie セッションの暗号鍵
	 *
	 * @return	鍵
	 */
	public static String secret () {

		return Conf.conf().getString(KEY_SECRET, "");

	}

}
