package io.jimble.web.assets;

import io.jimble.web.router.Controller;

/**
 * 静的ファイル配信のルート定義
 *
 * <p>
 * <b>ルーティング本体に静的配信を混ぜない</b>（要件 F-W-20）ための独立コントローラ。
 * </p>
 *
 * <pre>
 * install(() -&gt; AssetHandler.mount("/assets", "assets"));
 * </pre>
 */
public final class AssetController extends Controller {

	/**
	 * コンストラクタ
	 *
	 * @param routePath	ルートのパス（例 {@code /assets}）
	 * @param baseDir	クラスパス上のベースディレクトリ（例 {@code assets}）
	 */
	public AssetController (String routePath, String baseDir) {

		AssetHandler handler = new AssetHandler(baseDir);

		String prefix = normalize(routePath);

		// GET と HEAD を登録する。HEAD は本文を読まずにヘッダだけ返す
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
