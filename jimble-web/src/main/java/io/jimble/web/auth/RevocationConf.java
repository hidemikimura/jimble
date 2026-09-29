package io.jimble.web.auth;

import java.time.Duration;
import io.jimble.util.conf.Conf;

/**
 * 利用者を外からログアウトさせる設定（要件 F-W-33）
 *
 * <pre>
 * auth {
 *     revocation {
 *         enabled   = true    # 既定。DB が無ければ比べない（Auth.revoke は例外）
 *         cache_ttl = 5s      # 引いた世代を控える時間。0s で毎回引く
 *     }
 * }
 * </pre>
 *
 * <h2>控える時間は、ほかの台で効くまでの遅れである</h2>
 * <p>
 * {@link Auth#revoke} を呼んだ台は、その場で自分の控えを書き換える（遅れ 0）。
 * <b>ほかの台は、控えが切れるまで古い世代で比べる</b>ので、複数台で動かすと最大これだけ遅れる。
 * 控えないと、ログインしている人のリクエストのたびに DB を1回引く。
 * </p>
 *
 * <h2>切っても黙らない</h2>
 * <p>
 * {@code enabled = false} のとき、{@link Auth#guard} は比べない。
 * <b>{@link Auth#revoke} は例外になる</b>——呼んだ側が締め出したつもりで進まないように。
 * </p>
 */
public final class RevocationConf {

	/** 使うか */
	public static final String KEY_ENABLED = "auth.revocation.enabled";

	/** 引いた世代を控える時間 */
	public static final String KEY_CACHE_TTL = "auth.revocation.cache_ttl";

	private RevocationConf () {
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
	 * 引いた世代を控える時間
	 *
	 * @return	時間。0 なら控えない
	 */
	public static Duration cacheTtl () {

		Duration ttl = Conf.conf().getDuration(KEY_CACHE_TTL, Duration.ofSeconds(5));

		return ttl.isNegative() ? Duration.ZERO : ttl;

	}

}
