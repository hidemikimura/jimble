package io.jimble.web.captcha;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.http.HttpException;
import io.jimble.web.ratelimit.RateLimits;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ロボットでないことを確かめる（ALTCHA。外のサービスに頼らない）
 *
 * <p>
 * ブラウザに計算（proof-of-work）を解かせ、答えをサーバーで確かめる。
 * <b>外のサービスへの登録も、別のサーバーも要らない。</b>問題を返すルートと、確かめるフィルタの 2 つを書く。
 * ブラウザ側は ALTCHA のウィジェット（{@code altcha} 3.x。MIT）を置く。
 * </p>
 *
 * <pre>
 * get("/altcha/challenge", Altcha::challenge);       // 問題を返す
 *
 * path("/contact", () -&gt; {
 *     before(Altcha::verify);                       // 答えを確かめる（GET などは素通し）
 *     post("", context -&gt; ...);
 * });
 * </pre>
 *
 * <pre>
 * &lt;form method="post" action="/contact"&gt;
 *   ...
 *   &lt;altcha-widget challenge="/altcha/challenge"&gt;&lt;/altcha-widget&gt;
 * &lt;/form&gt;
 * </pre>
 *
 * <p>
 * <b>一度通った答えは、もう一度は通さない。</b>使った印を流量制限の置き場（{@code rate_limit.store}）に
 * 期限まで置く。複数台に並べるなら {@code rate_limit.store} を {@code redis} か {@code db} にすること
 * （{@code memory} のままだと、別の台に送られた同じ答えは通る）。
 * </p>
 *
 * <p>
 * 確かめるだけで、ボットを止めきるものではない。送る回数は流量制限（{@code RateLimit}）で、
 * ログインの試行はロックアウト（{@code Lockout}）で別に絞ること。
 * </p>
 */
public final class Altcha {

	/** 答えを入れるフォームの項目名（ウィジェットの既定） */
	public static final String FIELD_NAME = "altcha";

	/** 答えを入れるヘッダ名（フォームでなく fetch で送るとき） */
	public static final String HEADER_NAME = "X-Altcha";

	/** 断るときのステータスコード */
	public static final int STATUS_CODE = 403;

	/* 使った印のキーの接頭辞 */
	static final String USED_PREFIX = "altcha:";

	/*
	 * 使った印を、期限よりこれだけ長く置く（台どうしの時計のずれのぶん）。
	 * 期限を過ぎた答えは、印が無くても期限切れで断る
	 */
	private static final Duration USED_MARGIN = Duration.ofMinutes(1);

	/* 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	private Altcha () {}

	/**
	 * 問題を返す（ルートに置く）
	 *
	 * <p>ウィジェットが GET で取りに来る。毎回違う問題なので、キャッシュさせない。</p>
	 *
	 * @param context	コンテキスト
	 */
	public static void challenge (WebContext context) {

		context.response().setResponseHeader("Cache-Control", "no-store");
		context.response().json(Data.fromJsonString(createChallenge()));

	}

	/**
	 * 問題を作る（JSON）
	 *
	 * <p>{@link #challenge(WebContext)} を使わずに、ページに埋め込むときに使う（{@code challenge='{...}'}）。</p>
	 *
	 * @return	問題（ウィジェットの {@code challenge} にそのまま渡せる JSON）
	 */
	public static String createChallenge () {

		int counterMax = AltchaConf.counterMax();
		long counter = ThreadLocalRandom.current().nextLong(counterMax / 2, counterMax + 1L);
		long expiresAt = System.currentTimeMillis() / 1000 + AltchaConf.expires().toSeconds();

		return AltchaPow.create(keys(), AltchaConf.cost(), counter, expiresAt, RANDOM).toJson();

	}

	/**
	 * 答えを確かめる（フィルタに置く）
	 *
	 * <p>
	 * 状態を変えないメソッド（GET など）は素通しする。答えはフォームの {@value #FIELD_NAME}、
	 * JSON の本文の {@value #FIELD_NAME}、ヘッダの {@value #HEADER_NAME} の順に探す。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @throws HttpException	確かめられなかった場合（403）
	 */
	public static void verify (WebContext context) {

		if (Csrf.SAFE_METHODS.contains(context.request().method().toUpperCase())) {
			return;
		}

		AltchaResult result = check(payload(context));

		if (!result.verified()) {
			throw new HttpException(STATUS_CODE, result.message());
		}

	}

	/**
	 * 答えを確かめる（断り方を自分で決めるとき。たとえばフォームを出し直す）
	 *
	 * <p>
	 * 通った答えは、使った印を置く。<b>同じ答えでもう一度呼ぶと {@link AltchaResult#REPLAYED} になる。</b>
	 * </p>
	 *
	 * @param payload	ウィジェットが送ってきた値
	 * @return	結果
	 */
	public static AltchaResult check (String payload) {

		long now = System.currentTimeMillis();
		AltchaPow.Checked checked = AltchaPow.verify(payload, keys(), now / 1000);

		if (!checked.result().verified()) {
			return checked.result();
		}

		return markUsed(checked, now) ? AltchaResult.VERIFIED : AltchaResult.REPLAYED;

	}

	/**
	 * 送られてきた答え
	 *
	 * @param context	コンテキスト
	 * @return	答え（無ければ null）
	 */
	static String payload (WebContext context) {

		String form = context.request().bodyForm().getStringOptional(FIELD_NAME);

		if (form != null && !form.isEmpty()) {
			return form;
		}

		String json = context.request().bodyJson().get(FIELD_NAME) instanceof String value ? value : null;

		if (json != null && !json.isEmpty()) {
			return json;
		}

		return context.request().header().getStringOptional(HEADER_NAME.toLowerCase());

	}

	/**
	 * 使った印を置く
	 *
	 * <p>
	 * 置き場が使えないときは、<b>通す</b>（ログに出す）。流量制限と同じ扱いで、
	 * Redis が落ちただけで全部のフォームが送れなくなるのを避ける。署名と答えは確かめてある。
	 * </p>
	 *
	 * @param checked	確かめた結果
	 * @param nowMillis	いまの時刻（ミリ秒）
	 * @return	初めて使った場合 = true
	 */
	private static boolean markUsed (AltchaPow.Checked checked, long nowMillis) {

		Duration ttl = Duration.ofMillis(Math.max(1, checked.expiresAt() * 1000 - nowMillis)).plus(USED_MARGIN);

		try {
			return RateLimits.store().consume(USED_PREFIX + checked.signature(), 1, ttl).allowed();
		} catch (Exception ex) {
			Log.error(ex, "ALTCHA の使った印を置けませんでした（通します）");
			return true;
		}

	}

	private static AltchaPow.Keys keys () {

		return AltchaPow.Keys.derive(AltchaConf.secret());

	}

}
