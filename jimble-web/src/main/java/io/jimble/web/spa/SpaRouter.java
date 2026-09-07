package io.jimble.web.spa;

import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SPA のパス定義
 *
 * <p>
 * 通常のルートツリーとは別に持つ（要件 F-W-17）。
 * SPA は<b>どのパスでも同じ index.html を返す</b>のが基本なので、
 * ルートツリーに1つずつ登録するのではなく、ここでまとめて宣言する。
 * </p>
 *
 * <pre>
 * install(() -&gt; SpaHandler.mount("/app", "app", spa -&gt; spa
 *     .route("/items/{id}", (context, html) -&gt; withTitle(context, html))
 * ));
 * </pre>
 */
public final class SpaRouter {

	/* ルート（登録順） */
	private final List<SpaRoute> routes = new ArrayList<>();

	/**
	 * パスを登録する
	 *
	 * @param path		パス（{@code /items/{id}} 形式）
	 * @param rewriter	書き換え処理
	 * @return	自身
	 */
	public SpaRouter route (String path, SpaRewriter rewriter) {

		routes.add(new SpaRoute(path, rewriter));

		return this;

	}

	/**
	 * 登録されたルート
	 *
	 * @return	ルート
	 */
	public List<SpaRoute> routes () {

		return routes;

	}

	/**
	 * マッチするルートを探す
	 *
	 * <p>
	 * 見つかったらパスパラメータをリクエストに載せる。
	 * <b>登録順で最初に一致したものを使う</b>（ルートツリーと同じ考え方。D-8）。
	 * </p>
	 *
	 * @param context		コンテキスト
	 * @param requestPath	リクエストのパス
	 * @return	ルート（無ければ null）
	 */
	public SpaRoute match (WebContext context, String requestPath) {

		for (SpaRoute route : routes) {

			Map<String, String> variables = route.match(requestPath);

			if (variables != null) {
				context.request().bodyPath().putAll(variables);
				return route;
			}

		}

		return null;

	}

}
