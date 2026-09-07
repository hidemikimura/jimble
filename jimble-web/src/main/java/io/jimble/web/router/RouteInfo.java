package io.jimble.web.router;

/**
 * ルート一覧の1行
 *
 * <p>起動時のログ出力と重複検出に使う。</p>
 *
 * @param method	メソッド
 * @param path		絶対パス
 * @param route		ルート
 */
public record RouteInfo (String method, String path, Route route) {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "%-7s %s".formatted(method, path);

	}

}
