package io.jimble.web.ratelimit;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.AttributeKey;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 流量制限の宣言（要件 F-R-15 / F-R-22）
 *
 * <p>
 * <b>ルートに載せる。</b>1本だけなら属性で、まとめてなら書いたブロックに。
 * </p>
 *
 * <pre>
 * // 1本だけ
 * post("/api/login", handler).attribute(RateLimit.KEY, RateLimit.perIp(5, Duration.ofMinutes(1)));
 *
 * // このブロック全部
 * path("/api", () -&gt; {
 *     rateLimit(RateLimit.perIp(60, Duration.ofMinutes(1)));
 *     get("/items", handler);
 * });
 * </pre>
 *
 * <h2>数え方</h2>
 * <p>
 * トークンバケットである。{@code duration} かけて {@code limit} 個まで戻る。
 * <b>短いバーストは通り、続けて叩くと止まる。</b>
 * </p>
 *
 * <p>
 * 固定の窓（「1分あたり N 回」）にしなかったのは、
 * 窓の境目で<b>一瞬 2N 回通ってしまう</b>ためである。
 * </p>
 *
 * @param key		数える単位を返す（IP、ログイン ID など）
 * @param limit		貯められる回数
 * @param duration	空から満タンに戻るまでの時間
 * @param exclude	制限しない条件（null なら全部数える）
 */
public record RateLimit (
	Function<WebContext, String> key
	, long limit
	, Duration duration
	, Predicate<WebContext> exclude
) {

	/** ルートに載せるときのキー */
	public static final AttributeKey<RateLimit> KEY = new AttributeKey<>("rate_limit", null);

	/**
	 * コンストラクタ
	 */
	public RateLimit {

		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(duration, "duration");

		if (limit <= 0) {
			throw new IllegalArgumentException("limit は 1 以上にしてください: " + limit);
		}

		if (duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException("duration は正の長さにしてください: " + duration);
		}

	}

	/**
	 * 送信元ごとに数える
	 *
	 * <p>
	 * <b>{@code server.trust_proxy} を見る。</b>プロキシの後ろに置いているなら
	 * true にしておかないと、<b>全員がロードバランサの IP として1つに数えられる</b>
	 * （[サーバー設定] 参照）。
	 * </p>
	 *
	 * @param limit		回数
	 * @param duration	戻るまでの時間
	 * @return	宣言
	 */
	public static RateLimit perIp (long limit, Duration duration) {

		return new RateLimit(context -> context.request().proxyAddress(), limit, duration, null);

	}

	/**
	 * 数える単位を自分で決める
	 *
	 * @param key		単位
	 * @param limit		回数
	 * @param duration	戻るまでの時間
	 * @return	宣言
	 */
	public static RateLimit of (Function<WebContext, String> key, long limit, Duration duration) {

		return new RateLimit(key, limit, duration, null);

	}

	/**
	 * 制限しない条件を足す
	 *
	 * @param exclude	条件（true なら数えない）
	 * @return	宣言
	 */
	public RateLimit exclude (Predicate<WebContext> exclude) {

		return new RateLimit(key, limit, duration, exclude);

	}

}
