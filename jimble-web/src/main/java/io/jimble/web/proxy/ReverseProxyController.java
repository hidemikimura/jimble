package io.jimble.web.proxy;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.Controller;

import java.util.function.Function;

/**
 * リバースプロキシのルート定義
 *
 * <p>
 * <b>{@code any()} で全メソッドを1回で登録する</b>（要件 F-R-21）。
 * 移送元は get / post / put / patch / delete / head / options … と
 * <b>同じ登録を 14 回書いていた</b>ので、メソッドを1つ足し忘れると
 * そのメソッドだけ静かに 404 になっていた。
 * </p>
 */
public final class ReverseProxyController extends Controller {

	/**
	 * コンストラクタ
	 *
	 * @param routePath			ルートのパス（例 {@code /api}）
	 * @param factory			リクエストから転送先を決める
	 */
	public ReverseProxyController (String routePath, Function<WebContext, ReverseProxy> factory) {

		String prefix = normalize(routePath);

		if (!prefix.isEmpty()) {
			any(prefix, context -> factory.apply(context).handle(context));
		}

		any((prefix.isEmpty() ? "" : prefix) + "/*", context -> factory.apply(context).handle(context));

	}

	/**
	 * ルートのパスを整える
	 *
	 * @param routePath	パス
	 * @return	末尾の「/」を落としたもの
	 */
	private static String normalize (String routePath) {

		if (routePath == null || routePath.isEmpty() || "/".equals(routePath)) {
			return "";
		}

		String path = routePath.startsWith("/") ? routePath : "/" + routePath;

		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

	}

}
