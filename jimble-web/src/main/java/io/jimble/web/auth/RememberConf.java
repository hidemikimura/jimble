package io.jimble.web.auth;

import java.time.Duration;
import io.jimble.util.conf.Conf;

/**
 * ログインしたままにする設定（要件 F-W-30）
 *
 * <pre>
 * auth {
 *     remember {
 *         enabled       = true        # 既定。DB が無ければ何もしない
 *         cookie_name   = "remember"
 *         sliding  = 30d              # 最後に使ってからこれだけ
 *         absolute = 90d              # 発行してからこれを超えたら、使っていても切れる
 *         grace    = 60s              # 回した直後、古いほうも通す時間
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

	/** 最後に使ってからの期限 */
	public static final String KEY_SLIDING = "auth.remember.sliding";

	/** 発行してからの期限 */
	public static final String KEY_ABSOLUTE = "auth.remember.absolute";

	/** 回した直後の猶予 */
	public static final String KEY_GRACE = "auth.remember.grace";

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
	 * 最後に使ってからの期限
	 *
	 * @return	期限
	 */
	public static Duration sliding () {

		return Conf.atLeast(Conf.conf().getDuration(KEY_SLIDING, Duration.ofDays(30))
			, Duration.ofDays(1));

	}

	/**
	 * 発行してからの期限（日）
	 *
	 * <p>
	 * <b>{@link #sliding} より短くしても意味はある</b>（そのときは実質こちらだけが効く）。
	 * </p>
	 *
	 * @return	期限
	 */
	public static Duration absolute () {

		return Conf.atLeast(Conf.conf().getDuration(KEY_ABSOLUTE, Duration.ofDays(90))
			, Duration.ofDays(1));

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
	 * @return	猶予
	 */
	public static Duration grace () {

		return Conf.atLeast(Conf.conf().getDuration(KEY_GRACE, Duration.ofSeconds(60))
			, Duration.ZERO);

	}

}
