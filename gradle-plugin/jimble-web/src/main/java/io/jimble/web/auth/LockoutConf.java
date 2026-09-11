package io.jimble.web.auth;

import io.jimble.util.conf.Conf;

/**
 * ログインの失敗をどう抑えるかの設定（要件 F-W-29）
 *
 * <pre>
 * auth {
 *     lockout {
 *         enabled        = true    # 既定。DB が無ければ何もしない
 *         free_attempts  = 3       # ここまでは待たされない（打ち間違い）
 *         base_seconds   = 1       # 4回目から 1 → 2 → 4 → 8 …
 *         max_seconds    = 300     # 待ち時間の上限
 *         forget_hours   = 24      # これだけ間が空いたら数え直す
 *     }
 * }
 * </pre>
 *
 * <h2>なぜ「N 回で M 分ロック」にしないのか</h2>
 * <p>
 * <b>アカウント単位で止める仕組みは、そのまま嫌がらせの道具になる。</b>
 * 「5回わざと間違える」だけで、その人は M 分締め出される。
 * 全員ぶんやれば業務が止まる。
 * </p>
 *
 * <p>
 * 待ち時間を倍にしていく形なら、<b>攻撃者から見た試行速度は実質ゼロ</b>になり、
 * <b>正規の利用者は数秒待つだけ</b>で済む。被害の大きさが釣り合っている。
 * </p>
 */
public final class LockoutConf {

	/** 使うか */
	public static final String KEY_ENABLED = "auth.lockout.enabled";

	/** 待たされない回数 */
	public static final String KEY_FREE_ATTEMPTS = "auth.lockout.free_attempts";

	/** 待ち時間の基準（秒） */
	public static final String KEY_BASE_SECONDS = "auth.lockout.base_seconds";

	/** 待ち時間の上限（秒） */
	public static final String KEY_MAX_SECONDS = "auth.lockout.max_seconds";

	/** 数え直すまでの時間 */
	public static final String KEY_FORGET_HOURS = "auth.lockout.forget_hours";

	private LockoutConf () {
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
	 * 待たされない回数
	 *
	 * <p>
	 * <b>0 にしないこと。</b>1回打ち間違えただけの人を待たせると、
	 * 「反応が遅いサイト」だと思われる（そして<b>攻撃者は待たない</b>）。
	 * </p>
	 *
	 * @return	回数
	 */
	public static int freeAttempts () {

		return Math.max(0, Conf.conf().getInt(KEY_FREE_ATTEMPTS, 3));

	}

	/**
	 * 待ち時間の基準（秒）
	 *
	 * @return	秒
	 */
	public static long baseSeconds () {

		return Math.max(1, Conf.conf().getLong(KEY_BASE_SECONDS, 1));

	}

	/**
	 * 待ち時間の上限（秒）
	 *
	 * @return	秒
	 */
	public static long maxSeconds () {

		return Math.max(1, Conf.conf().getLong(KEY_MAX_SECONDS, 300));

	}

	/**
	 * 数え直すまでの時間
	 *
	 * <p>
	 * <b>これが無いと、半年前に3回間違えた人が今日いきなり待たされる。</b>
	 * </p>
	 *
	 * @return	時間
	 */
	public static long forgetHours () {

		return Math.max(1, Conf.conf().getLong(KEY_FORGET_HOURS, 24));

	}

}
