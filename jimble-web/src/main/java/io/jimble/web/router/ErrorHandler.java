package io.jimble.web.router;

import io.jimble.web.context.WebContext;

/**
 * エラー処理
 *
 * <p>
 * ルート未マッチ（404）もここに流れてくる。アプリ全体の404ページは
 * トップレベルの {@code error(...)} で書ける。
 * </p>
 */
@FunctionalInterface
public interface ErrorHandler {

	/**
	 * 処理する
	 *
	 * @param context		コンテキスト
	 * @param cause			原因
	 * @param statusCode	原因から解決されたステータスコード
	 * @throws Exception	処理中の例外
	 */
	void handle (WebContext context, Throwable cause, int statusCode) throws Exception;

}
