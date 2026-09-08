package io.jimble.web.mpa;

import io.jimble.web.router.Controller;
import io.jimble.web.router.Route;

import java.util.ArrayList;
import java.util.List;

/**
 * MPA 配信のルート定義
 *
 * <p><b>ルーティング本体に混ぜない</b>（要件 F-W-20）ための独立コントローラ。</p>
 */
public final class MpaController extends Controller {

	/* 登録したルート（D-70） */
	private final List<Route> routes = new ArrayList<>();

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
			routes.add(get(prefix, handler::handle));
			routes.add(head(prefix, handler::handle));
		} else {
			routes.add(get("/", handler::handle));
			routes.add(head("/", handler::handle));
		}

		routes.add(get(prefix + "/*", handler::handle));
		routes.add(head(prefix + "/*", handler::handle));

	}

	/**
	 * 登録したルート
	 *
	 * <p>
	 * <b>属性を付けられるように外に出す</b>（D-70）。
	 * </p>
	 *
	 * <pre>
	 * MpaController mpa = MpaHandler.mount("/", "site");
	 * for (Route route : mpa.routes()) {
	 *     route.attribute(NO_AUTH, true);
	 * }
	 *
	 * install(() -&gt; mpa);   // ← 組み込みを忘れないこと
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
