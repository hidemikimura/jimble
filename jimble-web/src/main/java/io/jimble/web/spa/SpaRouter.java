package io.jimble.web.spa;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.router.Handler;
import io.jimble.web.router.HttpMethods;
import io.jimble.web.router.RouteMatch;
import io.jimble.web.router.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SPA のパス定義（要件 F-W-17 / D-75）
 *
 * <p>
 * SPA は<b>どのパスでも同じ index.html を返す</b>のが基本だが、
 * クローラや OGP のためにパスごとに中身を差し替えたいことがある。
 * その差し替え先をここで宣言する。
 * </p>
 *
 * <pre>
 * install(() -&gt; SpaHandler.mount("/app", "app", spa -&gt; spa
 *     .route("/app/items/{id}", (context, html) -&gt; withTitle(context, html))
 * ));
 * </pre>
 *
 * <h2>マッチは本体と同じルートツリーでやる</h2>
 * <p>
 * <b>{@link Router} をそのまま使う。</b>ここだけ別の仕組み（正規表現）で
 * マッチさせていたので、次の点が本体と食い違っていた。
 * </p>
 *
 * <ol>
 *   <li><b>優先順位。</b>正規表現版は<b>登録順に最初に当たったもの</b>だった。
 *       ツリーは 固定セグメント &gt; パスパラメータ &gt; ワイルドカード の順で、
 *       {@code /app/items/new} と {@code /app/items/{id}} を両方書いたときに
 *       <b>書いた順に関係なく</b>具体的なほうが勝つ</li>
 *   <li><b>後戻り。</b>正規表現版は1本ずつ試すだけだった。
 *       ツリーは枝を戻って別の候補を試す（要件 T-4）</li>
 *   <li><b>パーセントエンコード。</b>正規表現版は<b>デコード済みのパス</b>を見ていたので、
 *       {@code %2F} を含む値がセグメントの区切りに化けて当たらなかった。
 *       ツリーは生のパスをセグメントに割ってからデコードする</li>
 *   <li><b>重複。</b>正規表現版は同じパスを2回書いても<b>黙って先勝ち</b>だった。
 *       ツリーは登録時に落とす（要件 F-R-13）</li>
 *   <li><b>ワイルドカード。</b>{@code /app/docs/*} が書けるようになった</li>
 * </ol>
 *
 * <p>
 * <b>本体のルート表とは別のインスタンスを持つ</b>（要件 F-W-20）。
 * SPA のパスが起動時のルート一覧に混ざったり、
 * API のルートと重複判定でぶつかったりしないようにするためである。
 * </p>
 */
public final class SpaRouter {

	/* 書き換え先を載せる属性 */
	private static final AttributeKey<SpaRoute> ROUTE = new AttributeKey<>("spa.route", null);

	/*
	 * ツリーに載せるためのダミー。
	 * SPA のパスは「当たったかどうか」しか見ないので、処理は呼ばれない。
	 */
	private static final Handler NEVER_CALLED = context -> {
		throw new IllegalStateException("SPA のルートは HTTP からは呼ばれません");
	};

	/* ルートツリー（本体と同じもの。SPA 用に別のインスタンス） */
	private final Router router = new Router();

	/* 登録したもの（登録順。一覧用） */
	private final List<SpaRoute> routes = new ArrayList<>();

	/**
	 * パスを登録する
	 *
	 * @param path		パス（{@code /items/{id}} 形式。末尾の {@code /*} も書ける）
	 * @param rewriter	書き換え処理
	 * @return	自身
	 */
	public SpaRouter route (String path, SpaRewriter rewriter) {

		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(rewriter, "rewriter");

		SpaRoute route = new SpaRoute(path, rewriter);

		router.get(path, NEVER_CALLED).attribute(ROUTE, route);
		routes.add(route);

		return this;

	}

	/**
	 * 登録されたルート
	 *
	 * @return	ルート（登録順）
	 */
	public List<SpaRoute> routes () {

		return List.copyOf(routes);

	}

	/**
	 * マッチするルートを探す
	 *
	 * <p>見つかったらパスパラメータをリクエストに載せる。</p>
	 *
	 * @param context		コンテキスト
	 * @param requestRawPath	リクエストの<b>生の</b>パス（パーセントエンコードされたまま）
	 * @return	ルート（無ければ null）
	 */
	public SpaRoute match (WebContext context, String requestRawPath) {

		/*
		 * SPA は GET / HEAD でしか来ないので、メソッドでは分けない。
		 * ツリーには GET として載せてある。
		 */
		RouteMatch match = router.match(HttpMethods.GET, requestRawPath == null ? "" : requestRawPath);

		if (!match.matched()) {
			return null;
		}

		context.request().bodyPath().putAll(match.variables().values());

		return match.route().attribute(ROUTE);

	}

}
