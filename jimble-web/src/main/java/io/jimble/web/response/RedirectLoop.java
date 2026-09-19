package io.jimble.web.response;

import io.jimble.web.request.Request;
import io.jimble.web.router.PathSegments;

import java.net.URI;
import java.util.Objects;

/**
 * リダイレクトループ判定
 *
 * <p>
 * <b>飛び先が、いま処理しているリクエストと同じ URL かどうか</b>を見る。
 * 同じなら、ブラウザはまた同じ URL を取りに来て、同じ処理が同じ飛び先を返す——
 * これが止まらない。
 * </p>
 *
 * <p>
 * <b>GET / HEAD のときだけ見る。</b>{@code POST /login} を受けて {@code /login} に 302 で返すのは
 * 入力し直しの画面へ戻す普通の形（PRG）で、ブラウザは次を GET で取りに来るので回らない。
 * </p>
 *
 * <p>
 * 比べるのは scheme・host・port・パス（スラッシュを正規化したもの）・クエリ。
 * <b>フラグメント（{@code #...}）はサーバーに届かないので見ない。</b>
 * 相対 URL はリクエストの URL に対して解決する。
 * <b>解決できない飛び先は「ループではない」とする</b>——
 * 判定のために正しいリダイレクトを止めるほうが害が大きい。
 * </p>
 */
final class RedirectLoop {

	private RedirectLoop () {}

	/**
	 * 飛び先がいまのリクエストと同じ URL か
	 *
	 * @param request	リクエスト
	 * @param location	飛び先
	 * @return	同じなら true
	 */
	static boolean isLoop (Request request, String location) {

		if (request == null || location == null || location.isBlank()) {
			return false;
		}

		String method = request.method();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			return false;
		}

		String requestUrl = request.url();
		if (requestUrl == null || requestUrl.isBlank()) {
			return false;
		}

		URI current;
		URI target;

		try {
			current = new URI(requestUrl).normalize();
			// {@code /./x} や {@code /a/../x} を素の形に寄せる（resolve だけでは残る）
			target = current.resolve(new URI(location.trim())).normalize();
		} catch (Exception ex) {
			return false;
		}

		if (!equalsIgnoreCase(current.getScheme(), target.getScheme())) {
			return false;
		}

		if (!equalsIgnoreCase(current.getHost(), target.getHost())) {
			return false;
		}

		if (port(current) != port(target)) {
			return false;
		}

		if (!PathSegments.canonicalRawPath(rawPath(current)).equals(PathSegments.canonicalRawPath(rawPath(target)))) {
			return false;
		}

		return Objects.equals(emptyToNull(current.getRawQuery()), emptyToNull(target.getRawQuery()));

	}

	/* ポート（省略は scheme の既定に寄せる） */
	private static int port (URI uri) {

		if (uri.getPort() != -1) {
			return uri.getPort();
		}

		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;

	}

	/* 生のパス（空は "/"） */
	private static String rawPath (URI uri) {

		String rawPath = uri.getRawPath();
		return rawPath == null || rawPath.isEmpty() ? "/" : rawPath;

	}

	private static String emptyToNull (String value) {

		return value == null || value.isEmpty() ? null : value;

	}

	private static boolean equalsIgnoreCase (String a, String b) {

		return a == null ? b == null : a.equalsIgnoreCase(b);

	}

}
