package io.jimble.web.csrf;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF トークン（要件 F-S-06）
 *
 * <p>
 * トークンは Cookie に置き、リクエストではヘッダかフォームで送り返してもらって
 * 突き合わせる（double submit cookie）。
 * </p>
 *
 * <p>
 * {@code csrf.bind_session = true} にすると、トークンを Cookie ではなく<b>セッションに置く</b>
 * （{@link #KEY_BIND_SESSION}）。ログインでセッション ID を振り直すとき、トークンも作り直し、
 * 新しいトークンを応答の {@value #HEADER_NAME} ヘッダで返す（{@link #rotate}）。
 * </p>
 *
 * <pre>
 * before(Csrf::verify);          // 全体に掛ける
 * path("/api", () -&gt; {
 *     before(Csrf::verify);      // スコープに掛ける
 *     ...
 * });
 * </pre>
 *
 * <h2>移送元から変えたところ</h2>
 * <p>
 * 移送元の {@code CsrfChecker.check()} は <b>boolean を返すだけ</b>で、
 * 呼び出し側が結果を見なければ素通りした。ここでは {@link #verify(WebContext)} が
 * 403 の {@link HttpException} を投げる。<b>掛け忘れは起きても、見落としは起きない。</b>
 * </p>
 */
public final class Csrf {

	/** Cookie 名 */
	public static final String COOKIE_NAME = "csrf_token";

	/** ヘッダ名 */
	public static final String HEADER_NAME = "X-CSRF-Token";

	/** フォーム項目名 */
	public static final String FORM_NAME = "csrf_token";

	/** 検証しないメソッド（状態を変えない） */
	public static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	/**
	 * 設定キー：トークンの寿命（要件 D-159）
	 *
	 * <p>
	 * <b>以前は {@code cookie.max_age}（既定1年）に相乗りしていた。</b>
	 * アプリが自分の都合で {@code cookie.max_age = 1h} と書くと、
	 * <b>CSRF トークンも1時間で切れる</b>——出るのは
	 * 「CSRF トークンがありません」の 403 だけで、
	 * <b>Cookie の設定を短くしたせいだとは分からない</b>。
	 * </p>
	 */
	public static final String KEY_MAX_AGE = "csrf.max_age";

	/**
	 * 既定の寿命
	 *
	 * <p>
	 * <b>1日にしてある。</b>開きっぱなしのフォームを一晩越えて送れる長さで、
	 * かつ<b>盗まれたトークンが使える窓を1年にしない</b>ところを選んだ。
	 * </p>
	 */
	public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(1);

	/**
	 * 設定キー：トークンをセッションに結びつけるか（D-255。既定 false）
	 *
	 * <p>
	 * 既定の double submit cookie は、トークンが<b>利用者に結びついていない</b>。
	 * 同じ親ドメインの下に Cookie を書ける場所（別のサブドメインなど）を攻撃者が持っていると、
	 * <b>攻撃者が自分で受け取った正しいトークン</b>を被害者のブラウザに植え付けて、送らせられる。
	 * true にすると、トークンをセッションに置き、ログインのたびに作り直すので、これが効かなくなる。
	 * </p>
	 *
	 * <p>
	 * <b>既定で入れていないのは、壊れるものがあるため</b>である。
	 * </p>
	 * <ul>
	 *   <li>{@code session.store = "none"} のアプリには置き場が無い（true にすると、使ったところで例外）</li>
	 *   <li>ページを読み直さずにログインする SPA は、ログインの前に受け取ったトークンを持ち続けるので、
	 *       ログインのあとの POST が 403 になる。応答の {@value #HEADER_NAME} ヘッダが来たら差し替えること</li>
	 *   <li>true にした時点で開いているフォームは、1度だけ 403 になる（トークンの置き場が変わるため）</li>
	 * </ul>
	 */
	public static final String KEY_BIND_SESSION = "csrf.bind_session";

	/** セッションのキー：トークン（csrf.bind_session = true のとき） */
	static final String SESSION_KEY = "__csrf_token";

	/** トークンの長さ（バイト） */
	private static final int TOKEN_LENGTH = 32;

	/* 乱数生成器 */
	private static final SecureRandom RANDOM = new SecureRandom();

	private Csrf () {}

	/**
	 * トークンを取り出す。無ければ発行する
	 *
	 * <p>
	 * 発行した場合は Cookie に載る。<b>フォームを出すページで呼ぶ。</b>
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	トークン
	 */
	public static String token (WebContext context) {

		if (bound(context)) {

			String token = context.session().get(SESSION_KEY);

			if (token == null || token.isEmpty()) {
				token = generate();
				context.session().put(SESSION_KEY, token);

				/*
				 * <b>ここで保存する。</b>セッションは明示保存（要件 F-S-02）なので、
				 * 保存しないとトークンを出したのに残らず、送り返されたときに必ず 403 になる。
				 * あとでアプリが put すれば保存済みの印は戻るので、アプリの save() は効く
				 */
				context.session().save();
			}

			return token;

		}

		String token = context.cookies().get(COOKIE_NAME);

		if (token != null && !token.isEmpty()) {

			/*
			 * 古い鍵で読めたなら、<b>今の鍵で署名し直す</b>（要件 NF-S-09）。
			 * トークンの値は変えない——変えると、いま開いているフォームが
			 * <b>送信した瞬間に 403 になる</b>
			 */
			if (context.cookies().isStale(COOKIE_NAME)) {
				context.cookies().put(COOKIE_NAME, token, maxAge().toSeconds());
			}

			return token;

		}

		token = generate();
		context.cookies().put(COOKIE_NAME, token, maxAge().toSeconds());

		return token;

	}

	/**
	 * トークンの寿命
	 *
	 * @return	寿命
	 */
	public static Duration maxAge () {

		return Conf.conf().getDuration(KEY_MAX_AGE, DEFAULT_MAX_AGE);

	}

	/**
	 * 検証する
	 *
	 * <p>
	 * 状態を変えないメソッド（{@link #SAFE_METHODS}）は素通しする。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @throws HttpException	検証に失敗した場合（403）
	 */
	public static void verify (WebContext context) {

		if (SAFE_METHODS.contains(context.request().method())) {
			return;
		}

		String expected = bound(context)
			? context.session().get(SESSION_KEY)
			: context.cookies().get(COOKIE_NAME);
		if (expected == null || expected.isEmpty()) {
			throw new HttpException(403, "CSRF トークンがありません");
		}

		String actual = requestToken(context);
		if (actual == null || actual.isEmpty()) {
			throw new HttpException(403, "CSRF トークンが送られていません");
		}

		// タイミング攻撃を避けるため定数時間で比べる
		if (!MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.UTF_8)
			, actual.getBytes(StandardCharsets.UTF_8))) {
			throw new HttpException(403, "CSRF トークンが一致しません");
		}

	}

	/**
	 * トークンを作り直す（csrf.bind_session = true のときだけ）
	 *
	 * <p>
	 * {@link io.jimble.web.session.Session#regenerateId()}（ログイン・二要素認証の完了・一部のログアウト）が呼ぶ。
	 * 新しいトークンは、応答の {@value #HEADER_NAME} ヘッダでも返す。
	 * <b>SPA は、応答にこのヘッダがあったら、手元のトークンを差し替える</b>。
	 * </p>
	 *
	 * <p>
	 * 別のオリジンから呼ぶ SPA は、CORS で {@code Access-Control-Expose-Headers: X-CSRF-Token} を出さないと読めない。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	新しいトークン（csrf.bind_session = false なら null）
	 */
	public static String rotate (WebContext context) {

		if (!bound(context)) {
			return null;
		}

		String token = generate();
		context.session().put(SESSION_KEY, token);
		context.response().setResponseHeader(HEADER_NAME, token);

		return token;

	}

	/**
	 * トークンをセッションに結びつけるか
	 *
	 * @return	{@code csrf.bind_session}
	 */
	public static boolean bindSession () {

		return Conf.conf().getBoolean(KEY_BIND_SESSION, false);

	}

	/**
	 * このリクエストで、トークンをセッションに置くか
	 *
	 * @param context	コンテキスト
	 * @return	置く場合 = true
	 * @throws IllegalStateException	csrf.bind_session = true なのにセッションの置き場が無い場合
	 */
	private static boolean bound (WebContext context) {

		if (!bindSession()) {
			return false;
		}

		/*
		 * <b>黙って Cookie に戻さない。</b>戻すと、結びつけたつもりで結びついていない、
		 * という気づけない形になる
		 */
		if (!context.session().isAvailable()) {
			throw new IllegalStateException(
				"csrf.bind_session = true には、セッションの置き場（session.store = db / redis / cookie）が要ります"
					+ io.jimble.util.internal.Docs.see("session-security"));
		}

		return true;

	}

	/**
	 * 検証する（例外を投げない）
	 *
	 * @param context	コンテキスト
	 * @return	正しい場合 = true
	 */
	@CheckReturnValue
	public static boolean isValid (WebContext context) {

		try {
			verify(context);
			return true;
		} catch (HttpException ex) {
			return false;
		}

	}

	/**
	 * リクエストから送られたトークン
	 *
	 * <p>ヘッダを先に見て、無ければフォーム、JSON の本文（{@value #FORM_NAME}）を見る。クエリ文字列は見ない。</p>
	 *
	 * @param context	コンテキスト
	 * @return	トークン（無ければ null）
	 */
	private static String requestToken (WebContext context) {

		String header = context.request().header().getStringOptional(HEADER_NAME.toLowerCase());
		if (header != null && !header.isEmpty()) {
			return header;
		}

		/*
		 * <b>本文（フォーム・JSON）からだけ読む</b>（D-245）。かつては bodyAll() から読んだので、
		 * クエリ文字列やパスでも受け付け、トークンが URL に載ってアクセスログや Referer に残りえた。
		 * 入れ子を解く bodyAll() を、認証の前にここで走らせることも無くなる
		 */
		String form = context.request().bodyForm().getStringOptional(FORM_NAME);
		if (form != null && !form.isEmpty()) {
			return form;
		}

		return context.request().bodyJson().getStringOptional(FORM_NAME);

	}

	/**
	 * トークンを作る
	 *
	 * @return	トークン
	 */
	private static String generate () {

		byte[] bytes = new byte[TOKEN_LENGTH];
		RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

	}

}
