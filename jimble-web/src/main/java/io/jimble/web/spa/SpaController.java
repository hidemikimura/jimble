package io.jimble.web.spa;

import io.jimble.web.router.Controller;

/**
 * SPA 配信のルート定義
 *
 * <p>
 * <b>ルーティング本体に混ぜない</b>（要件 F-W-20）ための独立コントローラ。
 * 通常のルートツリーと共存する（要件 F-W-17）ので、
 * API のルートと同じアプリに並べて書ける。
 * </p>
 */
public final class SpaController extends Controller {

	/**
	 * コンストラクタ
	 *
	 * @param routePath	ルートのパス（例 {@code /app}）
	 * @param baseDir	クラスパス上のベースディレクトリ
	 * @param router	SPA ルーター（無ければ null）
	 */
	public SpaController (String routePath, String baseDir, SpaRouter router) {

		SpaHandler handler = new SpaHandler(baseDir, router);

		String prefix = normalize(routePath);

		if (!prefix.isEmpty()) {
			// /app 自体（末尾スラッシュなし）
			get(prefix, handler::handle);
			head(prefix, handler::handle);
		}

		get(prefix + "/*", handler::handle);
		head(prefix + "/*", handler::handle);

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
