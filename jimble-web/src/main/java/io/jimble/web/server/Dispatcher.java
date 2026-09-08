package io.jimble.web.server;

import io.jimble.core.executor.Executor;
import io.jimble.util.log.Log;
import io.jimble.web.call.CallRequest;
import io.jimble.web.call.CallResponse;
import io.jimble.web.call.Calls;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.NotFoundException;
import io.jimble.web.ratelimit.RateLimit;
import io.jimble.web.ratelimit.RateLimits;
import io.jimble.web.router.ErrorHandler;
import io.jimble.web.router.Handler;
import io.jimble.web.router.HttpMethods;
import io.jimble.web.router.Route;
import io.jimble.web.router.RouteMatch;
import io.jimble.web.router.Router;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * ディスパッチャ
 *
 * <p>
 * リクエスト1本の処理を統括する。HTTP サーバー実装（helidon）を知らない。
 * </p>
 *
 * <p>処理の流れ：</p>
 * <ol>
 *     <li>ルートをマッチさせる</li>
 *     <li>{@code onRequest}（ルート未マッチでも呼ばれる）</li>
 *     <li>未マッチなら {@link NotFoundException} を投げてエラー経路に合流する</li>
 *     <li>{@code before}（外側 → 内側）</li>
 *     <li>ルートのハンドラ、または Executor をキューに積む</li>
 *     <li>Executorキューを回す</li>
 *     <li>レスポンス送信</li>
 * </ol>
 * <p>
 * 各段で「送信済みなら打ち切る」判定を {@link Stage} が行う。
 * 例外はエラー経路へ。{@code after}（内側 → 外側）と {@code onComplete} は必ず実行される。
 * </p>
 */
public final class Dispatcher {

	/* アプリケーション */
	private final JimbleApp app;

	/* ルーター */
	private final Router router;

	/**
	 * コンストラクタ
	 *
	 * @param app	アプリケーション
	 */
	public Dispatcher (JimbleApp app) {

		this.app = Objects.requireNonNull(app, "app");
		this.router = app.router();

		// ルートごとのフックをここで確定する（要件 D-69）。以降はルートを足せない
		this.router.seal();

	}

	/**
	 * ルーター
	 *
	 * @return	ルーター
	 */
	public Router router () {

		return router;

	}

	/**
	 * ディスパッチする
	 *
	 * <p>
	 * ScopedValue をバインドした状態で処理する。コンテキストのクローズは呼び出し側の責務。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	public void dispatch (WebContext context) {

		context.dispatcher(this);
		context.run(() -> handle(context));

	}

	/**
	 * すでに実装してあるルートを、HTTP を通さずに呼ぶ（要件 F-W-27）
	 *
	 * <pre>
	 * CallResponse response = context.dispatcher().call(context
	 *     , CallRequest.of("GET", "/api/posts").query("page", "2"));
	 * </pre>
	 *
	 * <p>
	 * <b>通常のリクエストとまったく同じ道を通る。</b>
	 * {@code before} / {@code after} / エラーハンドラも流量制限も効く。
	 * 違うのは<b>送り先だけ</b>で、ネットワークに出す代わりに
	 * {@link CallResponse} に受け止める。
	 * </p>
	 *
	 * <p>
	 * MCP から API の実装を流用するのが最初の用途である（要件 F-MCP-15）。
	 * <b>ハンドラを2度書かないためにある。</b>
	 * ドメイン層を共有できるならそちらが先で、
	 * これは<b>「API として組み上がったものをそのまま出したい」</b>ときに使う。
	 * </p>
	 *
	 * <p>
	 * 内側は<b>別のコンテキスト</b>になる（実行IDだけ外側から引き継ぐ）。
	 * トランザクションも DB 接続も内側で別に持つので、
	 * <b>外側で開けたトランザクションの中には入らない。</b>
	 * </p>
	 *
	 * @param outer		外側のコンテキスト
	 * @param request	呼び出すもの
	 * @return	結果
	 */
	public CallResponse call (WebContext outer, CallRequest request) {

		Objects.requireNonNull(outer, "outer");
		Objects.requireNonNull(request, "request");

		return Calls.call(outer, request, inner -> {
			inner.dispatcher(this);
			inner.run(() -> handle(inner));
		});

	}

	/**
	 * 処理する
	 *
	 * @param context	コンテキスト
	 */
	private void handle (WebContext context) {

		String method = context.request().method();
		String rawPath = context.request().rawPath();

		RouteMatch match = router.match(method, rawPath);
		context.route(match);

		Stage stage = new Stage(context);

		try {

			stage.run(app::onRequest);

			if (!stage.isDone() && !match.matched()) {
				throw new NotFoundException(context.request().path());
			}

			/*
			 * 流量制限（要件 F-R-15 / F-R-22）。
			 *
			 * before より前に見る。止めると決めたリクエストに
			 * 認証や DB を触らせないためである。
			 */
			stage.run(current ->
				RateLimits.apply(current, match.route() == null ? null : match.route().attribute(RateLimit.KEY)));

			for (Handler hook : match.beforeHooks()) {
				stage.run(hook);
			}

			stage.run(current -> invokeRoute(current, match));

			// OPTIONS はプリフライト専用。Executorキューは回さない（要件 F-W-19）
			if (!HttpMethods.OPTIONS.equals(method)) {
				stage.runExecutors();
			}

			stage.send();

		} catch (Throwable cause) {

			handleError(context, match, cause);

		} finally {

			runAfterHooks(context, match);

			try {
				app.onComplete(context);
			} catch (Throwable cause) {
				Log.error(cause, "onComplete で例外が発生しました");
			}

		}

	}

	/**
	 * ルートの処理を呼ぶ
	 *
	 * @param context	コンテキスト
	 * @param match		マッチ結果
	 * @throws Exception	処理中の例外
	 */
	private void invokeRoute (WebContext context, RouteMatch match) throws Exception {

		Route route = match.route();

		if (route.handler() != null) {
			route.handler().handle(context);
			return;
		}

		for (Supplier<Executor<WebContext>> supplier : route.executorSuppliers()) {
			context.addExecutor(supplier.get());
		}

	}

	/**
	 * エラーを処理する
	 *
	 * @param context	コンテキスト
	 * @param match		マッチ結果
	 * @param cause		原因
	 */
	private void handleError (WebContext context, RouteMatch match, Throwable cause) {

		int statusCode = app.resolveStatusCode(cause);

		// 500番台だけエラーログに出す。404 をエラーログに流さない（要件 F-C-16）
		if (statusCode >= 500) {
			Log.error(cause, "リクエスト処理で例外が発生しました: %s %s"
				.formatted(context.request().method(), context.request().path()));
		} else {
			Log.debug("%d を返します: %s %s (%s)"
				.formatted(statusCode, context.request().method(), context.request().path(), cause.getMessage()));
		}

		/*
		 * 先にステータスを入れておく。
		 * エラーハンドラが code(...) で上書きできるし、
		 * 何も返さなかったときの既定にもなる。
		 */
		context.response().code(statusCode);

		/*
		 * エラー経路も Stage を通す（要件 F-C-13）。
		 *
		 * ここだけ自前で isSent() を見ていると、
		 * <b>通常の経路と同じ判定が2つ</b>になる。片方だけ直すとずれる。
		 */
		Stage stage = new Stage(context);

		// 内側 → 外側
		for (ErrorHandler hook : match.errorHooks()) {
			stage.runError(hook, cause, statusCode);
		}

		/*
		 * エラーハンドラが json(...) や text(...) で組み立てただけで
		 * send() を呼んでいないことがある。通常の経路（Stage.send）と同じ形なので、
		 * こちらだけ違う扱いにすると気づけない。
		 *
		 * ここが send(statusCode) だったときは、
		 * <b>組み立てた中身を捨ててステータスだけを返していた。</b>
		 * ハンドラは動いているし例外も出ないので、
		 * 「404 は返るが本文が空」という形でしか表に出ない。
		 */
		stage.send();

	}

	/**
	 * after を実行する
	 *
	 * @param context	コンテキスト
	 * @param match		マッチ結果
	 */
	private void runAfterHooks (WebContext context, RouteMatch match) {

		for (Handler hook : match.afterHooks()) {
			try {
				hook.handle(context);
			} catch (Throwable cause) {
				Log.error(cause, "after で例外が発生しました");
			}
		}

	}

}
