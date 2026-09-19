package io.jimble.web.http;

/**
 * リダイレクトが自分自身に戻る
 *
 * <p>
 * {@code Response.redirect(...)} の飛び先が、<b>いま処理しているリクエストと同じ URL</b>
 * だったときに投げる。ブラウザはそのまま同じ URL を取りに来るので、
 * <b>止めなければ無限に回り続ける</b>（ブラウザ側で「リダイレクトが多すぎます」になるまで）。
 * </p>
 *
 * <p>
 * {@link HttpException} なので、他の例外と同じく<b>アプリの {@code error(...)} に 500 で渡る</b>。
 * 画面へ飛ばすか JSON を返すかはそちらが決める。
 * </p>
 *
 * <p>
 * <b>見つけられるのは1段のループだけである。</b>{@code /a → /b → /a} のように
 * 別のリクエストをまたいで回るものは、1つのリクエストの中からは分からない。
 * </p>
 */
public class RedirectLoopException extends HttpException {

	/* 飛び先 */
	private final String location;

	/**
	 * コンストラクタ
	 *
	 * @param location	飛び先
	 * @param requestUrl	いま処理しているリクエストの URL
	 */
	public RedirectLoopException (String location, String requestUrl) {

		super(500, "リダイレクトループです。飛び先がいまのリクエストと同じ URL です: %s (request: %s)"
			.formatted(location, requestUrl));
		this.location = location;

	}

	/**
	 * 飛び先
	 *
	 * @return	飛び先
	 */
	public String location () {

		return location;

	}

}
