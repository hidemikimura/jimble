package io.jimble.web.router;

import io.jimble.core.executor.Executor;
import io.jimble.web.context.WebContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * コントローラ
 *
 * <p>
 * ルート定義は<b>インスタンス初期化ブロック</b>に書く。アノテーションは使わない。
 * </p>
 *
 * <pre>
 * public class AdminController extends Controller {
 *     {
 *         path("/admin", () -&gt; {
 *             before(AdminController::requireAuth);
 *
 *             post("/session", AdminController::session)
 *                 .attribute(NO_AUTH, true);
 *
 *             get("/users", ListUseCase::new);
 *
 *             install(GroupController::new);
 *         });
 *     }
 * }
 * </pre>
 *
 * <p>
 * {@code install} は子コントローラを生成し、その<b>ルーターをマージする</b>。
 * {@code jooby_base} は static フィールド経由で親ルーターを渡していたが、
 * この方式なら共有可変状態を持たずに済む。
 * </p>
 */
public abstract class Controller {

	/* 自身のルーター */
	private final Router root = new Router();

	/* 現在のスコープ（path のネスト） */
	private final Deque<Router> scopes = new ArrayDeque<>();

	/**
	 * コンストラクタ
	 */
	protected Controller () {

		scopes.push(root);

	}

	/**
	 * ルーター
	 *
	 * @return	ルーター
	 */
	public final Router router () {

		return root;

	}

	/**
	 * 現在のスコープ
	 *
	 * @return	ルーター
	 */
	private Router scope () {

		return scopes.peek();

	}


	// region フック

	/**
	 * before を登録する
	 *
	 * @param handler	処理
	 */
	protected final void before (Handler handler) {

		scope().before(handler);

	}

	/**
	 * after を登録する
	 *
	 * @param handler	処理
	 */
	protected final void after (Handler handler) {

		scope().after(handler);

	}

	/**
	 * error を登録する
	 *
	 * @param handler	処理
	 */
	protected final void error (ErrorHandler handler) {

		scope().error(handler);

	}

	// endregion


	/**
	 * パスをネストする
	 *
	 * @param path	パス
	 * @param body	配下の定義
	 */
	protected final void path (String path, Runnable body) {

		Objects.requireNonNull(body, "body");

		scopes.push(scope().path(path));
		try {
			body.run();
		} finally {
			scopes.pop();
		}

	}

	/**
	 * 子コントローラを取り込む
	 *
	 * @param factory	子コントローラの生成
	 */
	protected final void install (Supplier<? extends Controller> factory) {

		Objects.requireNonNull(factory, "factory");

		Controller child = factory.get();
		scope().merge(child.router());

	}


	// region ルート登録

	/**
	 * GET
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route get (String path, Handler handler) {

		return scope().get(path, handler);

	}

	/**
	 * WebSocket（要件 F-W-22）
	 *
	 * <pre>
	 * ws("/chat", ChatHandler::new);
	 * </pre>
	 *
	 * @param path		パス
	 * @param supplier	処理の作り手。<b>接続ごとに1つ作る</b>
	 * @return	ルート
	 */
	protected final Route ws (String path, java.util.function.Supplier<io.jimble.web.ws.WsHandler> supplier) {

		return scope().ws(path, supplier);

	}

	/**
	 * GET
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成（登録順に実行される）
	 * @return	ルート
	 */
	@SafeVarargs
	protected final Route get (String path, Supplier<Executor<WebContext>>... suppliers) {

		return scope().get(path, suppliers);

	}

	/**
	 * POST
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route post (String path, Handler handler) {

		return scope().post(path, handler);

	}

	/**
	 * POST
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	protected final Route post (String path, Supplier<Executor<WebContext>>... suppliers) {

		return scope().post(path, suppliers);

	}

	/**
	 * PUT
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route put (String path, Handler handler) {

		return scope().put(path, handler);

	}

	/**
	 * PUT
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	protected final Route put (String path, Supplier<Executor<WebContext>>... suppliers) {

		return scope().put(path, suppliers);

	}

	/**
	 * PATCH
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route patch (String path, Handler handler) {

		return scope().patch(path, handler);

	}

	/**
	 * PATCH
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	protected final Route patch (String path, Supplier<Executor<WebContext>>... suppliers) {

		return scope().patch(path, suppliers);

	}

	/**
	 * DELETE
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route delete (String path, Handler handler) {

		return scope().delete(path, handler);

	}

	/**
	 * DELETE
	 *
	 * @param path		パス
	 * @param suppliers	Executor生成
	 * @return	ルート
	 */
	@SafeVarargs
	protected final Route delete (String path, Supplier<Executor<WebContext>>... suppliers) {

		return scope().delete(path, suppliers);

	}

	/**
	 * HEAD
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route head (String path, Handler handler) {

		return scope().head(path, handler);

	}

	/**
	 * OPTIONS
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route options (String path, Handler handler) {

		return scope().options(path, handler);

	}

	/**
	 * TRACE
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	ルート
	 */
	protected final Route trace (String path, Handler handler) {

		return scope().trace(path, handler);

	}

	/**
	 * 全メソッドに一括登録する
	 *
	 * @param path		パス
	 * @param handler	処理
	 * @return	登録されたルート
	 */
	protected final List<Route> any (String path, Handler handler) {

		return scope().any(path, handler);

	}

	// endregion

}
