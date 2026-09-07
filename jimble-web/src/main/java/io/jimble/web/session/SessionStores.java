package io.jimble.web.session;

import io.jimble.util.log.Log;

/**
 * 保存先の解決
 *
 * <p>
 * 設定 {@code session.store} から既定の保存先を1つ作る。<b>作るのは1回だけ</b>で、
 * リクエストごとには作らない（テーブル作成や接続を毎回やらないため）。
 * </p>
 *
 * <p>
 * リクエスト単位で変えたいときは {@code context.sessionStore(...)}（要件 F-S-11）。
 * </p>
 *
 * <pre>
 * // 管理画面だけ DB セッション、公開側はセッションなし
 * path("/admin", () -&gt; {
 *     before(context -&gt; context.sessionStore(SessionStores.db()));
 *     ...
 * });
 * </pre>
 */
public final class SessionStores {

	/** 保存先の名前：なし */
	public static final String NONE = "none";

	/** 保存先の名前：DB */
	public static final String DB = "db";

	/** 保存先の名前：Redis */
	public static final String REDIS = "redis";

	/** 保存先の名前：Cookie */
	public static final String COOKIE = "cookie";

	/* 既定の保存先 */
	private static volatile SessionStore defaultStore;

	/* DB セッション */
	private static volatile DbSessionStore dbStore;

	/* Redis セッション */
	private static volatile RedisSessionStore redisStore;

	/* Cookie セッション */
	private static volatile CookieSessionStore cookieStore;

	private SessionStores () {}

	/**
	 * 既定の保存先
	 *
	 * @return	保存先
	 */
	public static SessionStore defaultStore () {

		if (defaultStore == null) {
			synchronized (SessionStores.class) {
				if (defaultStore == null) {
					defaultStore = create(SessionConf.store());
				}
			}
		}

		return defaultStore;

	}

	/**
	 * DB セッション
	 *
	 * @return	保存先
	 */
	public static DbSessionStore db () {

		if (dbStore == null) {
			synchronized (SessionStores.class) {
				if (dbStore == null) {
					dbStore = new DbSessionStore();
				}
			}
		}

		return dbStore;

	}

	/**
	 * Redis セッション
	 *
	 * @return	保存先
	 */
	public static RedisSessionStore redis () {

		if (redisStore == null) {
			synchronized (SessionStores.class) {
				if (redisStore == null) {
					redisStore = new RedisSessionStore();
				}
			}
		}

		return redisStore;

	}

	/**
	 * Cookie セッション
	 *
	 * @return	保存先
	 */
	public static CookieSessionStore cookie () {

		if (cookieStore == null) {
			synchronized (SessionStores.class) {
				if (cookieStore == null) {
					cookieStore = new CookieSessionStore();
				}
			}
		}

		return cookieStore;

	}

	/**
	 * セッションなし
	 *
	 * @return	保存先
	 */
	public static SessionStore none () {

		return EmptySessionStore.INSTANCE;

	}

	/**
	 * 名前から作る
	 *
	 * @param name	保存先の名前
	 * @return	保存先
	 */
	public static SessionStore create (String name) {

		return switch (name == null ? NONE : name.toLowerCase()) {
			case DB -> db();
			case REDIS -> redis();
			case COOKIE -> cookie();
			case NONE -> none();
			default -> {
				// 設定の書き間違いで黙ってセッションが効かなくなるより、気づける形にする
				Log.warn("不明なセッション保存先です。セッションなしで動かします: " + name);
				yield none();
			}
		};

	}

	/**
	 * 作り直す（テスト用）
	 */
	public static void reset () {

		synchronized (SessionStores.class) {
			defaultStore = null;
			dbStore = null;
			redisStore = null;
			cookieStore = null;
		}

	}

}
