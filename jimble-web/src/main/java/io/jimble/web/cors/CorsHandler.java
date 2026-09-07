package io.jimble.web.cors;

import io.jimble.util.string.StringUtil;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Handler;
import io.jimble.web.router.HttpMethods;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CORS を適用する（要件 F-W-12）
 *
 * <p>
 * {@code before} に挿す。<b>宣言的に書ける</b>ように、設定は {@link Cors} にまとめる。
 * </p>
 *
 * <pre>
 * // アプリ全体
 * before(new CorsHandler(new Cors()
 *     .addAllowOrigin("https://example.com")
 *     .addAllowedMethod("GET")
 *     .addAllowedMethod("POST")));
 *
 * // リクエストごとに決めたいとき
 * before(new CorsHandler(context -&gt; Conf.conf().isLocal() ? localCors(context) : null));
 * </pre>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>許可されない origin でも処理が続いていた。</b>移送元はフラグを立てるだけで、
 *       <b>そのまま本処理に進んでいた。</b>プリフライトなら実害は無いが、
 *       実リクエストではサーバー側の副作用が起きてからブラウザが結果を捨てることになる。
 *       403 で止める</li>
 *   <li>プリフライト（OPTIONS）は<b>ここで返し切る。</b>
 *       ルートが無くても 404 にならない</li>
 * </ol>
 */
public final class CorsHandler implements Handler {

	/** プリフライトで返すステータスコード */
	public static final int PREFLIGHT_STATUS_CODE = 204;

	/** 許可されなかったときのステータスコード */
	public static final int FORBIDDEN_STATUS_CODE = 403;

	/* CORS 設定を決める関数 */
	private final Function<WebContext, Cors> resolver;

	/**
	 * コンストラクタ
	 *
	 * @param cors	CORS 設定（null で CORS を適用しない）
	 */
	public CorsHandler (Cors cors) {

		this(context -> cors);

	}

	/**
	 * コンストラクタ
	 *
	 * @param resolver	リクエストから CORS 設定を決める関数（null を返すと適用しない）
	 */
	public CorsHandler (Function<WebContext, Cors> resolver) {

		this.resolver = resolver;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		String origin = context.request().origin();

		// 同一オリジン。CORS の出番ではない
		if (origin == null || origin.isEmpty()) {
			return;
		}

		Cors cors = resolver.apply(context);
		if (cors == null) {
			return;
		}

		if (!cors.matchOrigin(origin)) {
			throw new HttpException(FORBIDDEN_STATUS_CODE, "許可されていない origin です: " + origin);
		}

		List<String> requestHeaders = requestHeaders(context);
		if (!cors.matchHeader(requestHeaders)) {
			throw new HttpException(FORBIDDEN_STATUS_CODE, "許可されていないヘッダです");
		}

		String requestMethod = context.request().header().getStringOptional("access-control-request-method");
		if (!requestMethod.isEmpty() && !cors.matchMethod(requestMethod)) {
			throw new HttpException(FORBIDDEN_STATUS_CODE, "許可されていないメソッドです: " + requestMethod);
		}

		applyHeaders(context, cors, origin, requestHeaders);

		// プリフライトはここで返し切る
		if (HttpMethods.OPTIONS.equals(context.request().method())) {
			context.response().send(PREFLIGHT_STATUS_CODE);
		}

	}

	/**
	 * レスポンスヘッダを設定する
	 *
	 * @param context			コンテキスト
	 * @param cors				CORS 設定
	 * @param origin			リクエストの origin
	 * @param requestHeaders	要求されたヘッダ
	 */
	private void applyHeaders (WebContext context, Cors cors, String origin, List<String> requestHeaders) {

		context.response().setResponseHeader("Access-Control-Allow-Origin", origin);
		context.response().setResponseHeader("Access-Control-Allow-Methods",
			StringUtil.concat(", ", cors.allowedMethods()));

		context.response().setResponseHeader("Access-Control-Allow-Headers",
			StringUtil.concat(", ", cors.anyHeader() ? requestHeaders : cors.allowedHeaders()));

		context.response().setResponseHeader("Access-Control-Allow-Credentials",
			String.valueOf(cors.allowCredentials()));

		if (!cors.exposeHeaders().isEmpty()) {
			context.response().setResponseHeader("Access-Control-Expose-Headers",
				StringUtil.concat(", ", cors.exposeHeaders()));
		}

		if (cors.maxAge() > 0) {
			context.response().setResponseHeader("Access-Control-Max-Age", String.valueOf(cors.maxAge()));
		}

		// origin ごとにキャッシュを分ける。これが無いと別 origin に他人の応答が出る
		context.response().setResponseHeader("Vary", "Origin");

	}

	/**
	 * 要求されたヘッダの一覧
	 *
	 * @param context	コンテキスト
	 * @return	ヘッダ名
	 */
	private List<String> requestHeaders (WebContext context) {

		List<String> headers = new ArrayList<>();

		String value = context.request().header().getStringOptional("access-control-request-headers");
		if (value.isEmpty()) {
			return headers;
		}

		for (String header : value.split("\\s*,\\s*")) {
			if (!header.isEmpty()) {
				headers.add(header);
			}
		}

		return headers;

	}

}
