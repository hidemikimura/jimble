package io.jimble.web.auth;

import io.jimble.util.conf.Conf;

/**
 * ログインしたままにする設定（要件 F-W-30）
 *
 * <pre>
 * auth {
 *     remember {
 *         enabled       = true        # 既定。DB が無ければ何もしない
 *         cookie_name   = "remember"
 *         sliding_days  = 30          # 最後に使ってからこれだけ
 *         absolute_days = 90          # 発行してからこれを超えたら、使っていても切れる
 *         grace_seconds = 60          # 回した直後、古いほうも通す時間
 *     }
 * }
 * </pre>
 *
 * <h2>なぜ期限が2つあるのか</h2>
 * <p>
 * <b>使うたびに延ばすだけだと、いつまでも切れない。</b>
 * 毎日来る人の Cookie は永遠に有効なままで、
 * <b>盗まれたことに誰も気づかなければ、盗んだ側も永遠に入れる</b>。
 * </p>
 *
 * <p>
 * 絶対の上限を別に置くと、<b>どんなに使っていても 90 日で一度は入り直す</b>ことになる。
 * 「毎日使う人は入り直さなくてよい」と「いつかは必ず切れる」を両立させるには、両方が要る。
 * </p>
 */
public final class RememberConf {

	/** 使うか */
	public static final String KEY_ENABLED = "auth.remember.enabled";

	/** Cookie の名前 */
	public static final String KEY_COOKIE_NAME = "auth.remember.cookie_name";

	/** 最後に使ってからの期限（日） */
	public static final String KEY_SLIDING_DAYS = "auth.remember.sliding_days";

	/** 発行してからの期限（日） */
	public static final String KEY_ABSOLUTE_DAYS = "auth.remember.absolute_days";

	/** 回した直後の猶予（秒） */
	public static final String KEY_GRACE_SECONDS = "auth.remember.grace_seconds";

	private RememberConf () {
	}

	/**
	 * 使うか
	 *
	 * @return	使う場合 = true
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, true);

	}

	/**
	 * Cookie の名前
	 *
	 * @return	名前
	 */
	public static String cookieName () {

		String name = Conf.conf().getString(KEY_COOKIE_NAME, "remember");

		return name == null || name.isEmpty() ? "remember" : name;

	}

	/**
	 * 最後に使ってからの期限（日）
	 *
	 * @return	日
	 */
	public static long slidingDays () {

		return Math.max(1, Conf.conf().getLong(KEY_SLIDING_DAYS, 30));

	}

	/**
	 * 発行してからの期限（日）
	 *
	 * <p>
	 * <b>{@link #slidingDays} より短くしても意味はある</b>（そのときは実質こちらだけが効く）。
	 * </p>
	 *
	 * @return	日
	 */
	public static long absoluteDays () {

		return Math.max(1, Conf.conf().getLong(KEY_ABSOLUTE_DAYS, 90));

	}

	/**
	 * 回した直後の猶予（秒）
	 *
	 * <p>
	 * <b>0 にすると、並列のリクエストでログアウトする。</b>
	 * 1枚のページが画像や API を同時に取りに行くと、
	 * <b>回した直後の古い Cookie を持った通信が数本あとから届く</b>——
	 * それを盗用とみなすと、ふつうに使っているだけの人が締め出される。
	 * </p>
	 *
	 * @return	秒
	 */
	public static long graceSeconds () {

		return Math.max(0, Conf.conf().getLong(KEY_GRACE_SECONDS, 60));

	}

}
