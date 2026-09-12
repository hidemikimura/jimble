package io.jimble.web.session;

import java.time.Duration;
import io.jimble.db.FrameworkTables;
import io.jimble.util.conf.Conf;
import io.jimble.util.crypto.Secrets;
import java.util.List;

/**
 * セッションの設定
 *
 * <pre>
 * session {
 *   store           = "none"    # none | db | redis | cookie
 *   timeout         = 30m
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

	/** 設定キー：タイムアウト */
	public static final String KEY_TIMEOUT = "session.timeout";

	/** 設定キー：セッション ID の Cookie 名 */
	public static final String KEY_COOKIE_NAME = "session.cookie_name";

	/** 設定キー：テーブル名 */
	public static final String KEY_TABLE = "session.table";

	/** 設定キー：Cookie セッションの暗号鍵 */
	public static final String KEY_SECRET = "session.secret";

	/** 設定キー：入れ替え前の暗号鍵（読むときだけ試す） */
	public static final String KEY_PREVIOUS_SECRETS = "session.previous_secrets";

	/** 既定のタイムアウト */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

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
	 * タイムアウト
	 *
	 * @return	時間
	 */
	public static Duration timeout () {

		return Conf.conf().getDuration(KEY_TIMEOUT, DEFAULT_TIMEOUT);

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

	/**
	 * Cookie セッションの暗号鍵の並び（要件 NF-S-09）
	 *
	 * <p>
	 * <b>先頭が「いま書くのに使う鍵」</b>で、残りは
	 * {@code session.previous_secrets} に書いた古い鍵である。
	 * 読むときだけ順に試し、<b>古い鍵で読めたらその場で新しい鍵で保存し直す</b>。
	 * </p>
	 *
	 * @return	鍵の並び（1つも無ければ空）
	 */
	public static List<String> secrets () {

		return Secrets.of(secret(), Conf.conf().getStringListOptional(KEY_PREVIOUS_SECRETS));

	}

}
