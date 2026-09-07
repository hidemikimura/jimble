package io.jimble.web.mpa;

import io.jimble.web.router.Controller;

/**
 * MPA 配信のルート定義
 *
 * <p><b>ルーティング本体に混ぜない</b>（要件 F-W-20）ための独立コントローラ。</p>
 */
public final class MpaController extends Controller {

	/**
	 * コンストラクタ
	 *
	 * @param routePath	ルートのパス（例 {@code /} や {@code /docs}）
	 * @param baseDir	クラスパス上のベースディレクトリ
	 */
	public MpaController (String routePath, String baseDir) {

		MpaHandler handler = new MpaHandler(baseDir);

		String prefix = normalize(routePath);

		if (!prefix.isEmpty()) {
			get(prefix, handler::handle);
			head(prefix, handler::handle);
		} else {
			get("/", handler::handle);
			head("/", handler::handle);
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
