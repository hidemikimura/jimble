package io.jimble.web.csrf;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.nio.charset.StandardCharsets;
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

		String token = context.cookies().get(COOKIE_NAME);

		if (token != null && !token.isEmpty()) {
			return token;
		}

		token = generate();
		context.cookies().put(COOKIE_NAME, token);

		return token;

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

		String expected = context.cookies().get(COOKIE_NAME);
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
	 * 検証する（例外を投げない）
	 *
	 * @param context	コンテキスト
	 * @return	正しい場合 = true
	 */
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
	 * <p>ヘッダを先に見て、無ければフォーム（{@value #FORM_NAME}）を見る。</p>
	 *
	 * @param context	コンテキスト
	 * @return	トークン（無ければ null）
	 */
	private static String requestToken (WebContext context) {

		String header = context.request().header().getStringOptional(HEADER_NAME.toLowerCase());
		if (header != null && !header.isEmpty()) {
			return header;
		}

		return context.request().bodyAll().getStringOptional(FORM_NAME);

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
