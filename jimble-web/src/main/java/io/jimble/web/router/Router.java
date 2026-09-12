package io.jimble.web.router;

import io.jimble.core.executor.Executor;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.ServerConf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * ルーター
 *
 * <p>
 * ルートツリーへの登録とマッチングの入口。ルート定義は {@link Controller} から書く。
 * </p>
 *
 * <p>
 * メソッド名は小文字。{@code jooby_base} が大文字にしていたのは jooby 本体との
 * 名前衝突を避けるためで、jooby に依存しない jimble にはその制約がない。
 * </p>
 */
public final class Router {

	/* このスコープのルートツリー（path でネストすると子ノードになる） */
	private final RouteTree tree;

	/* 一番外側のルートツリー（フックの確定に使う） */
	private final RouteTree rootTree;

	/* フックのスコープ（要件 D-69） */
	private final Scope scope;

	/**
	 * コンストラクタ
	 */
	public Router () {

		this.tree = new RouteTree();
		this.rootTree = this.tree;
		this.scope = new Scope();

	}

	/**
	 * コンストラクタ
	 *
	 * @param tree		ルートツリー
	 * @param rootTree	一番外側のルートツリー
	 * @param scope		フックのスコープ
	 */
	private Router (RouteTree tree, RouteTree rootTree, Scope scope) {

		this.tree = tree;
		this.rootTree = rootTree;
		this.scope = scope;

	}

	/**
	 * ルートツリー
	 *
	 * @return	ルートツリー
	 */
	RouteTree tree () {

		return tree;

	}


	// region フック

	/**
	 * before を登録する
	 *
	 * <p>
	 * <b>効くのはこのスコープで登録したルートだけ</b>（と、このスコープから
	 * {@code path} / {@code install} でネストしたもの）。パスが同じでも、
	 * 別のスコープで登録したルートには効かない（要件 D-69）。
	 * </p>
	 *
	 * <p>実行順は 外側 → 内側。</p>
	 *
	 * @param handler	処理
	 */
	public void before (Handler handler) {

		scope.before(Objects.requireNonNull(handler, "handler"));

	}

	/**
	 * after を登録する
	 *
	 * <p>効く範囲は {@link #before(Handler)} と同じ。実行順は 内側 → 外側。</p>
	 *
	 * @param handler	処理
	 */
	public void after (Handler handler) {

		scope.after(Objects.requireNonNull(handler, "handler"));

	}

	/**
	 * error を登録する
	 *
	 * <p>
	 * このスコープで登録したルートで起きた例外を捕捉する。内側のスコープが優先される。
	 * </p>
	 *
	 * <p>
	 * <b>未マッチ（404）で呼ばれるのは一番外側のスコープの error だけ</b>である
	 * （要件 F-R-09b）。どのルートにも当たっていないので、内側のスコープが決まらない。
	 * </p>
	 *
	 * @param handler	処理
	 */
	public void error (ErrorHandler handler) {

		scope.error(Objects.requireNonNull(handler, "handler"));

	}

	/**
	 * 流量制限を宣言する（要件 F-R-15 / F-R-22）
	 *
	 * <p>
	 * before と同じで<b>書いたブロックに付く</b>。
	 * 内側に別のものが書いてあれば内側が勝ち、
	 * ルートが属性で持っていればそれが勝つ。
	 * </p>
	 *
	 * @param rateLimit	宣言
	 */
	public void rateLimit (io.jimble.web.ratelimit.RateLimit rateLimit) {

		attribute(io.jimble.web.ratelimit.RateLimit.KEY
			, Objects.requireNonNull(rateLimit, "rateLimit"));

	}

	/**
	 * ブロックの中のルート全部に属性を付ける（要件 F-R-26）
	 *
	 * <p>
	 * <b>ルート1本ずつに書かなくてよくする</b>ための口である。
	 * 認証が要る／要らないのように、<b>ほとんどのルートで同じ値になるもの</b>を
	 * 1行で宣言し、違うものだけルート側で上書きする。
	 * </p>
	 *
	 * <pre>
	 * attribute(Auth.PUBLIC, true);          // このブロックは全部公開
	 *
	 * get("/guide", Guide::show);            // 公開
	 * get("/me",    Me::show).attribute(Auth.PUBLIC, false);   // ここだけ要ログイン
	 * </pre>
	 *
	 * <p>強い順に <b>ルート &gt; 内側のブロック &gt; 外側のブロック &gt; キーの既定値</b>。</p>
	 *
	 * <p>
	 * <b>パスのノードには付かない</b>（要件 D-69）。効くのは
	 * <b>このブロックで登録したルート</b>と、そこから {@code path} / {@code merge} で
	 * ネストしたものだけである。同じパスでも別のブロックで登録したルートには付かない——
	 * <b>付いているかどうかがコードを読んで分かる</b>ようにするためで、
	 * {@code before} / {@code after} と同じ決まりである。
	 * </p>
	 *
	 * @param key	キー
	 * @param value	値
	 * @param <T>	値の型
	 */
	public <T> void attribute (AttributeKey<T> key, T value) {

		scope.attribute(Objects.requireNonNull(key, "key"), value);

	}

	// endregion


	// region パス

	/**
	 * パスをネストする
	 *
	 * @param path	パス
	 * @return	配下のルーター
	 */
	public Router path (String path) {

		return new Router(tree.node(PathSegments.ofPattern(path)), rootTree, scope.child());

	}

	/**
	 * 別のルーターを取り込む
	 *
	 * <p>子コントローラの取り込みに使う。</p>
	 *
	 * @param other	取り込むルーター
	 */
	public void merge (Router other) {

		Objects.requireNonNull(other, "other");

		tree.merge(other.tree);

		// 取り込んだ側のフックが子にも効くよう、スコープを自分の下にぶら下げる
		scope.adopt(other.scope);

	}

	// endregion


	// region ルート登録

	/**
	 * GET
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route get (String path, Handler handler) {

		return route(HttpMethods.GET, path, handler);

	}

	/**
	 * GET
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成（登録順に実行される）
	 * @return	ルート
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")	// 配列は List にコピーするだけで外に漏らさない
	public final Route get (String path, Supplier<Executor<WebContext>>... suppliers) {

		return route(HttpMethods.GET, path, List.of(suppliers));

	}

	/**
	 * POST
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route post (String path, Handler handler) {

		return route(HttpMethods.POST, path, handler);

	}

	/**
	 * POST
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")	// 配列は List にコピーするだけで外に漏らさない
	public final Route post (String path, Supplier<Executor<WebContext>>... suppliers) {

		return route(HttpMethods.POST, path, List.of(suppliers));

	}

	/**
	 * PUT
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route put (String path, Handler handler) {

		return route(HttpMethods.PUT, path, handler);

	}

	/**
	 * PUT
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")	// 配列は List にコピーするだけで外に漏らさない
	public final Route put (String path, Supplier<Executor<WebContext>>... suppliers) {

		return route(HttpMethods.PUT, path, List.of(suppliers));

	}

	/**
	 * PATCH
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route patch (String path, Handler handler) {

		return route(HttpMethods.PATCH, path, handler);

	}

	/**
	 * PATCH
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")	// 配列は List にコピーするだけで外に漏らさない
	public final Route patch (String path, Supplier<Executor<WebContext>>... suppliers) {

		return route(HttpMethods.PATCH, path, List.of(suppliers));

	}

	/**
	 * DELETE
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route delete (String path, Handler handler) {

		return route(HttpMethods.DELETE, path, handler);

	}

	/**
	 * DELETE
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")	// 配列は List にコピーするだけで外に漏らさない
	public final Route delete (String path, Supplier<Executor<WebContext>>... suppliers) {

		return route(HttpMethods.DELETE, path, List.of(suppliers));

	}

	/**
	 * HEAD
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route head (String path, Handler handler) {

		return route(HttpMethods.HEAD, path, handler);

	}

	/**
	 * OPTIONS
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route options (String path, Handler handler) {

		return route(HttpMethods.OPTIONS, path, handler);

	}

	/**
	 * TRACE
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	public Route trace (String path, Handler handler) {

		return route(HttpMethods.TRACE, path, handler);

	}

	/**
	 * 全メソッドに一括登録する
	 *
	 * <p>リバースプロキシのように、メソッドを問わず同じ処理に流す場合に使う。</p>
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	登録されたルート
	 */
	public List<Route> any (String path, Handler handler) {

		List<Route> result = new ArrayList<>();

		for (String method : HttpMethods.ALL) {
			result.add(route(method, path, handler));
		}

		return result;

	}

	/**
	 * ルートを登録する
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	private Route route (String method, String path, Handler handler) {

		Objects.requireNonNull(handler, "handler");

		Route route = new Route(method, path, handler, null);
		route.scope(scope);
		tree.add(method, PathSegments.ofPattern(path), route);
		return route;

	}

	/**
	 * ルートを登録する
	 *
	 * @param method	メソッド
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	private Route route (String method, String path, List<Supplier<Executor<WebContext>>> suppliers) {

		if (suppliers.isEmpty()) {
			throw new IllegalArgumentException(
				"Executor が1つも指定されていません: %s %s".formatted(method, path));
		}

		Route route = new Route(method, path, null, suppliers);
		route.scope(scope);
		tree.add(method, PathSegments.ofPattern(path), route);
		return route;

	}

	// endregion


	// region マッチング・一覧

	/**
	 * マッチさせる
	 *
	 * @param method	メソッド
	 * @param rawPath	生のパス（パーセントエンコードされたまま）
	 * @return	マッチ結果
	 */
	public RouteMatch match (String method, String rawPath) {

		seal();

		return tree.match(method, PathSegments.ofRawPath(rawPath), scope.root().ownErrors());

	}

	/**
	 * フックを確定する（要件 D-69）
	 *
	 * <p>
	 * ルートごとの before / after / error を<b>ここで1度だけ組み立てる。</b>
	 * リクエストのたびに親を辿って集め直さない。
	 * </p>
	 *
	 * <p>
	 * 確定した後にフックを足すと落ちる。<b>「足したのに効かない」を黙って通さない</b>
	 * ためである。{@link io.jimble.web.server.JimbleServer} が起動時に呼ぶので、
	 * 普通は自分で呼ぶ必要はない。
	 * </p>
	 */
	public void seal () {

		if (scope.isSealed()) {
			return;
		}

		scope.root().seal();
		rootTree.forEachRoute(Route::seal);

		checkDuplicateAttributeNames();
		checkUnreachable();

	}

	/**
	 * 同じ名前の属性キーが2つ無いかを見る（要件 D-157）
	 *
	 * <p>
	 * <b>キーはインスタンスそのもの</b>（{@link AttributeKey}）なので、
	 * 名前がぶつかっても<b>振る舞いは正しい</b>——別のキーとして扱われる。
	 * それでも落とすのは、<b>ログと診断で見分けが付かなくなる</b>ためである。
	 * {@code auth_public} が2つ出てきたとき、
	 * <b>どちらが {@code Auth.PUBLIC} なのかを読み手が決められない。</b>
	 * </p>
	 *
	 * <p>
	 * <b>起動時に落とす。</b>リクエストが来てからでは、
	 * 出たログのほうを疑うことになる。
	 * </p>
	 */
	private void checkDuplicateAttributeNames () {

		Map<String, AttributeKey<?>> seen = new LinkedHashMap<>();
		List<String> duplicated = new ArrayList<>();

		collectAttributeNames(scope.root(), seen, duplicated);

		rootTree.forEachRoute(route -> {
			for (AttributeKey<?> key : route.attributeKeys()) {
				noteAttributeName(key, seen, duplicated);
			}
		});

		if (duplicated.isEmpty()) {
			return;
		}

		throw new IllegalStateException(
			"同じ名前の属性キーが2つ以上あります: %s"
				.formatted(String.join(", ", duplicated))
			+ "。キーはインスタンスで見分けるので動きはしますが、ログで区別が付きません。"
			+ "どちらかの名前を変えてください");

	}

	/**
	 * ブロックを降りながらキーを集める
	 *
	 * @param target		ブロック
	 * @param seen			見た名前
	 * @param duplicated	ぶつかった名前
	 */
	private void collectAttributeNames (
		Scope target, Map<String, AttributeKey<?>> seen, List<String> duplicated) {

		for (AttributeKey<?> key : target.ownAttributeKeys()) {
			noteAttributeName(key, seen, duplicated);
		}

		for (Scope child : target.children()) {
			collectAttributeNames(child, seen, duplicated);
		}

	}

	/**
	 * キーを1つ数える
	 *
	 * @param key			キー
	 * @param seen			見た名前
	 * @param duplicated	ぶつかった名前
	 */
	private void noteAttributeName (
		AttributeKey<?> key, Map<String, AttributeKey<?>> seen, List<String> duplicated) {

		AttributeKey<?> first = seen.putIfAbsent(key.name(), key);

		/*
		 * <b>同じインスタンスを何度見ても重複ではない。</b>
		 * ブロックからルートへ配られたキーは、同じものが何度も出てくる。
		 */
		if (first != null && first != key && !duplicated.contains(key.name())) {
			duplicated.add(key.name());
		}

	}

	/**
	 * 一生呼ばれないルートを知らせる（要件 F-R-13 / D-10）
	 *
	 * <p>
	 * <b>既定は警告だけ。</b>いま動いているアプリを、版を上げただけで
	 * 起動しなくするわけにはいかない。{@code server.strict_routes = true} で例外になる。
	 * </p>
	 */
	private void checkUnreachable () {

		List<UnreachableRoutes.Finding> findings = UnreachableRoutes.detect(rootTree);

		if (findings.isEmpty()) {
			return;
		}

		if (ServerConf.strictRoutes()) {

			StringBuilder message = new StringBuilder("一生呼ばれないルートがあります:");

			for (UnreachableRoutes.Finding finding : findings) {
				message.append("\n  ").append(finding);
			}

			throw new IllegalStateException(message.toString());

		}

		for (UnreachableRoutes.Finding finding : findings) {
			Log.warn("ルート: " + finding);
		}

	}

	/**
	 * WebSocket（要件 F-W-22）
	 *
	 * <pre>
	 * ws("/chat", ChatHandler::new);
	 * </pre>
	 *
	 * <p>
	 * helidon では WebSocket は別のルーティングになり、
	 * {@code routing.any()} には来ない。それでも<b>ルートの置き場所を1つに保つ</b>ため、
	 * ここでは擬似メソッド {@code WS} としてルートツリーに載せる
	 * （{@link io.jimble.web.ws.WsRoutes} 参照）。
	 * 起動時の一覧（要件 F-R-12）にも重複の検出（要件 F-R-13）にも乗る。
	 * 起動時に helidon の {@code WsRouting} へ流し込む。
	 * </p>
	 *
	 * @param path		パス
	 * @param supplier	処理の作り手。<b>接続ごとに1つ作る</b>
	 * @return	ルート
	 */
	public Route ws (String path, Supplier<io.jimble.web.ws.WsHandler> supplier) {

		Objects.requireNonNull(supplier, "supplier");

		/*
		 * 処理そのものはここでは呼ばれない（helidon が呼ぶ）。
		 * ツリーには「そこに WS がある」ことだけを載せ、
		 * 作り手は属性で持たせる。
		 */
		Route route = route(io.jimble.web.ws.WsRoutes.METHOD, path, context -> {
			throw new IllegalStateException("WebSocket のルートは HTTP からは呼ばれません: " + path);
		});

		return route.attribute(io.jimble.web.ws.WsRoutes.HANDLER, supplier);

	}

	/**
	 * ルート一覧
	 *
	 * <p>起動時のログ出力に使う。</p>
	 *
	 * @return	ルート一覧（パス → メソッド の順に並ぶ）
	 */
	public List<RouteInfo> routes () {

		List<RouteInfo> result = new ArrayList<>();
		tree.collect("", result);
		result.sort(Comparator.comparing(RouteInfo::path).thenComparing(RouteInfo::method));
		return result;

	}

	// endregion

}
