package io.jimble.web.assets;

import io.jimble.web.router.Controller;
import io.jimble.web.router.Route;

import java.util.ArrayList;
import java.util.List;

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

	/* 登録したルート（D-70） */
	private final List<Route> routes = new ArrayList<>();

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
		routes.add(get(prefix + "/*", handler::handle));
		routes.add(head(prefix + "/*", handler::handle));

	}

	/**
	 * 登録したルート
	 *
	 * <p>
	 * <b>属性を付けられるように外に出す</b>（D-70）。
	 * 移送元も含めて、ここは登録して終わりだったので、
	 * {@code install} した子のルートに {@code AttributeKey} を付ける手が無かった
	 * （回避策としてパス文字列を突き合わせるコードが書かれていた）。
	 * </p>
	 *
	 * <pre>
	 * AssetController assets = AssetHandler.mount("/assets", "assets");
	 * for (Route route : assets.routes()) {
	 *     route.attribute(RateLimit.KEY, limit);
	 * }
	 *
	 * install(() -&gt; assets);   // ← 組み込みを忘れないこと
	 * </pre>
	 *
	 * @return	登録したルート（登録順。変更はできない）
	 */
	public List<Route> routes () {

		return List.copyOf(routes);

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
