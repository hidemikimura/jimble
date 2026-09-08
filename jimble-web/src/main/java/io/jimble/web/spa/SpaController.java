package io.jimble.web.spa;

import io.jimble.web.router.Controller;
import io.jimble.web.router.Route;

import java.util.ArrayList;
import java.util.List;

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

	/* 登録したルート（D-70） */
	private final List<Route> routes = new ArrayList<>();

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
			routes.add(get(prefix, handler::handle));
			routes.add(head(prefix, handler::handle));
		} else {
			/*
			 * ルートに置いたときの "/" 自身。
			 * <b>"/*" は "/" に当たらない</b>（セグメントが0個なので、
			 * ルート木は終端でワイルドカードを見ない）。
			 * 足さないと<b>トップページだけ 404</b> になり、
			 * /any は 200 で返るので気づきにくい。
			 */
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
	 * 移送元も含めて、ここは登録して終わりだったので、
	 * {@code install} した子のルートに {@code AttributeKey} を付ける手が無かった
	 * （回避策としてパス文字列を突き合わせるコードが書かれていた）。
	 * </p>
	 *
	 * <pre>
	 * SpaController spa = SpaHandler.mount("/app", "app");
	 * for (Route route : spa.routes()) {
	 *     route.attribute(RateLimit.KEY, limit);
	 * }
	 *
	 * install(() -&gt; spa);   // ← 組み込みを忘れないこと
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
