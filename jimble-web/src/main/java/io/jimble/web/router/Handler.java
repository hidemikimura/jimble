package io.jimble.web.router;

import io.jimble.web.context.WebContext;

/**
 * リクエスト処理
 */
@FunctionalInterface
public interface Handler {

	/**
	 * 処理する
	 *
	 * @param context	コンテキスト
	 * @throws Exception	処理中の例外
	 */
	void handle (WebContext context) throws Exception;

}
