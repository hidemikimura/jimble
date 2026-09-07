package io.jimble.web.server;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Controller;

/**
 * アプリケーション
 *
 * <p>
 * ルート定義は {@link Controller} と同じくインスタンス初期化ブロックに書く。
 * </p>
 *
 * <pre>
 * public class App extends JimbleApp {
 *     {
 *         before(App::commonHeader);
 *         error(App::renderError);
 *
 *         get("/health_check", context -&gt; context.response().send());
 *
 *         install(AdminController::new);
 *     }
 *
 *     public static void main (String[] args) {
 *         JimbleServer.start(new App());
 *     }
 * }
 * </pre>
 */
public abstract class JimbleApp extends Controller {

	/**
	 * 全リクエストの最初に呼ばれる
	 *
	 * <p>
	 * <b>ルートが見つからなかった場合も呼ばれる。</b>
	 * ルートスコープの {@code before(...)} はマッチしたときにしか呼ばれないので、
	 * 「必ず通したい処理」はここに書く。
	 * </p>
	 *
	 * <p>ここでレスポンスを送信すると、以降の処理は行われない。</p>
	 *
	 * @param context	コンテキスト
	 * @throws Exception	処理中の例外
	 */
	protected void onRequest (WebContext context) throws Exception {

	}

	/**
	 * 全リクエストの最後に必ず呼ばれる
	 *
	 * <p>例外が出ても呼ばれる。ここでの例外はログに出して握りつぶす。</p>
	 *
	 * @param context	コンテキスト
	 */
	protected void onComplete (WebContext context) {

	}

	/**
	 * 例外からHTTPステータスコードを解決する
	 *
	 * <p>
	 * 独自の例外にコードを割り当てたい場合はここを上書きする。
	 * </p>
	 *
	 * @param cause	原因
	 * @return	ステータスコード
	 */
	protected int resolveStatusCode (Throwable cause) {

		if (cause instanceof HttpException httpException) {
			return httpException.statusCode();
		}

		return 500;

	}

}
