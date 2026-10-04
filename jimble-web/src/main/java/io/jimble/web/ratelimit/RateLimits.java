package io.jimble.web.ratelimit;

import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 流量制限をかける（要件 F-R-15 / F-R-22）
 *
 * <p>
 * <b>ルートに載っていれば自動でかかる。</b>{@link io.jimble.web.server.Dispatcher} が
 * {@code before} より前に1回だけ呼ぶ。宣言のしかたは {@link RateLimit} を参照。
 * </p>
 *
 * <h2>止めたときに返すもの</h2>
 * <table border="1">
 *   <caption>応答</caption>
 *   <tr><td>ステータス</td><td>429</td></tr>
 *   <tr><td>{@code Retry-After}</td><td>待つ秒数（切り上げ。最低 1）</td></tr>
 *   <tr><td>{@code X-RateLimit-Limit} / {@code X-RateLimit-Remaining}</td><td>上限と残り</td></tr>
 * </table>
 */
public final class RateLimits {

	/** 止めたときのステータス */
	public static final int STATUS_CODE = 429;

	/** ヘッダ：上限 */
	public static final String HEADER_LIMIT = "X-RateLimit-Limit";

	/** ヘッダ：残り */
	public static final String HEADER_REMAINING = "X-RateLimit-Remaining";

	/** ヘッダ：待つ秒数 */
	public static final String HEADER_RETRY_AFTER = "Retry-After";

	/* 置き場を作る排他 */
	private static final ReentrantLock LOCK = new ReentrantLock();

	/* 置き場 */
	private static volatile RateLimitStore store;

	/* どの設定で作ったか */
	private static volatile String storeKind;

	private RateLimits () {}

	/**
	 * 置き場
	 *
	 * <p>設定が変わったら作り直す（テストのため）。</p>
	 *
	 * @return	置き場
	 */
	public static RateLimitStore store () {

		String kind = RateLimitConf.store();

		RateLimitStore current = store;

		if (current != null && kind.equals(storeKind)) {
			return current;
		}

		LOCK.lock();

		try {

			if (store != null && kind.equals(storeKind)) {
				return store;
			}

			store = create(kind);
			storeKind = kind;

			return store;

		} finally {

			LOCK.unlock();

		}

	}

	/**
	 * 置き場を差し替える（テストと、自前の置き場を使うための拡張点）
	 *
	 * @param value	置き場。null なら設定から作り直す
	 */
	public static void replace (RateLimitStore value) {

		LOCK.lock();

		try {
			store = value;
			storeKind = value == null ? null : RateLimitConf.store();
		} finally {
			LOCK.unlock();
		}

	}

	/**
	 * 1回ぶん数えて、超えていたら 429 を返す
	 *
	 * <p>
	 * <b>数えられなかったときは通す。</b>Redis が落ちているだけで
	 * サイト全体が 429 になるほうが困る。ただし<b>黙っては通さない</b>（ログに出す）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param limit		宣言。null なら何もしない
	 */
	public static void apply (WebContext context, RateLimit limit) {

		if (limit == null || !RateLimitConf.enabled()) {
			return;
		}

		if (limit.exclude() != null && limit.exclude().test(context)) {
			return;
		}

		String key = limit.key().apply(context);

		if (key == null || key.isEmpty()) {
			// 単位が決まらないなら数えようがない
			return;
		}

		RateLimitResult result;

		try {
			result = store().consume(bucketKey(limit, key), limit.limit(), limit.duration());
		} catch (Exception cause) {
			Log.error(cause, "流量制限を数えられませんでした（通します）: %s".formatted(RateLimitConf.store()));
			return;
		}

		context.response().setResponseHeader(HEADER_LIMIT, String.valueOf(limit.limit()));
		context.response().setResponseHeader(HEADER_REMAINING, String.valueOf(result.remaining()));

		if (result.allowed()) {
			return;
		}

		long seconds = Math.max(1, (result.retryAfterMillis() + 999) / 1000);

		context.response().setResponseHeader(HEADER_RETRY_AFTER, String.valueOf(seconds));
		context.response().send(STATUS_CODE);

	}

	/**
	 * 置き場で数えるキー（回数と時間を頭に付ける。D-270）
	 *
	 * <p>
	 * <b>宣言が違えば、同じ人でも別に数える。</b>2.5.1 までは単位（IP など）だけで数えていたので、
	 * ログインに {@code perIp(5, 1m)}、{@code /api} 全体に {@code perIp(60, 1m)} と書くと、
	 * <b>{@code /api} を叩くたびに同じ枠が 60 回/分の速さで戻り、ログインの 5 回/分が効かなかった</b>
	 * （戻す速さは、そのとき数えた宣言のもので計算していた）。
	 * </p>
	 *
	 * <p>
	 * 回数と時間が同じ宣言どうしは、同じ枠を分け合う（厳しくなるほうに倒れるだけで、すり抜けはしない）。
	 * </p>
	 *
	 * @param limit	宣言
	 * @param key	単位
	 * @return	キー
	 */
	static String bucketKey (RateLimit limit, String key) {

		return limit.limit() + "/" + limit.duration().toMillis() + "ms:" + key;

	}

	/**
	 * 置き場を作る
	 *
	 * @param kind	設定の値
	 * @return	置き場
	 */
	private static RateLimitStore create (String kind) {

		return switch (kind) {

			case RateLimitConf.STORE_REDIS -> new RedisRateLimitStore();
			case RateLimitConf.STORE_DB -> new DbRateLimitStore();
			case RateLimitConf.STORE_MEMORY -> new MemoryRateLimitStore();

			default -> {
				// 知らない値で黙ってメモリになると、台をまたいで数えているつもりが数えていない
				Log.warn("%s に知らない値が指定されています: %s（memory を使います）"
					.formatted(RateLimitConf.KEY_STORE, kind));
				yield new MemoryRateLimitStore();
			}

		};

	}

}
