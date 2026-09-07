package io.jimble.web.http;

/**
 * ルートが見つからない
 *
 * <p>
 * ルート未マッチも通常のエラー経路に流す。アプリ全体の404ページは
 * トップレベルの {@code error(...)} で書ける。
 * </p>
 */
public class NotFoundException extends HttpException {

	/**
	 * コンストラクタ
	 *
	 * @param path	パス
	 */
	public NotFoundException (String path) {

		super(404, "ルートが見つかりません: " + path);

	}

}
