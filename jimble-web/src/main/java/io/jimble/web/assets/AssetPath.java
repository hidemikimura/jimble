package io.jimble.web.assets;

/**
 * 配信するファイルのパスを組み立てる
 *
 * <p>
 * <b>ここがディレクトリトラバーサルの入口になる。</b>
 * リクエストのパスは利用者が自由に書けるので、ベースディレクトリの外に出ないことを
 * ここで必ず確かめる。
 * </p>
 *
 * <p>
 * 移送元はワイルドカードの値をそのまま連結して {@code getResourceAsStream} に渡していた。
 * jimble の {@code PathSegments} はセグメントごとに percent デコードするため、
 * <b>{@code %2e%2e} のような書き方でも {@code ..} が復元される。</b>
 * 連結する前に弾く必要がある。
 * </p>
 */
public final class AssetPath {

	private AssetPath () {}

	/**
	 * リソースパスに解決する
	 *
	 * @param baseDir		ベースディレクトリ（例 {@code assets}）
	 * @param requestPath	リクエストのパス（ワイルドカード部分）
	 * @return	クラスパス上のリソースパス（安全でなければ null）
	 */
	public static String resolve (String baseDir, String requestPath) {

		if (requestPath == null) {
			requestPath = "";
		}

		// 先頭の「/」は落とす（ベースディレクトリの下に置くため）
		while (requestPath.startsWith("/")) {
			requestPath = requestPath.substring(1);
		}

		if (!isSafe(requestPath)) {
			return null;
		}

		String base = normalizeBase(baseDir);

		return base.isEmpty() ? "/" + requestPath : "/" + base + "/" + requestPath;

	}

	/**
	 * ベースディレクトリを整える
	 *
	 * @param baseDir	ベースディレクトリ
	 * @return	前後の「/」を落としたもの
	 */
	public static String normalizeBase (String baseDir) {

		if (baseDir == null) {
			return "";
		}

		String base = baseDir;

		while (base.startsWith("/")) {
			base = base.substring(1);
		}
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}

		return base;

	}

	/**
	 * ベースディレクトリの外に出ないか
	 *
	 * <p>
	 * {@code ..} を含むセグメント、空のセグメント（{@code //}）、
	 * バックスラッシュ、NUL 文字を弾く。
	 * </p>
	 *
	 * @param requestPath	リクエストのパス
	 * @return	安全な場合 = true
	 */
	public static boolean isSafe (String requestPath) {

		if (requestPath.isEmpty()) {
			return true;
		}

		if (requestPath.indexOf('\0') >= 0) {
			return false;
		}

		// Windows 由来の区切り。ここを通すとセグメント判定をすり抜ける
		if (requestPath.indexOf('\\') >= 0) {
			return false;
		}

		for (String segment : requestPath.split("/", -1)) {

			if (segment.isEmpty()) {
				// 「//」や末尾の「/」。ディレクトリを指すので配信しない
				return false;
			}

			if ("..".equals(segment) || ".".equals(segment)) {
				return false;
			}

		}

		return true;

	}

	/**
	 * 内容が変わらない前提で長くキャッシュしてよいか
	 *
	 * <p>
	 * ファイル名にハッシュが入るビルド成果物（{@code app.9f2c1a.js} など）を想定。
	 * ここでは拡張子で判断する。
	 * </p>
	 *
	 * @param path	パス
	 * @return	不変扱いにしてよい場合 = true
	 */
	public static boolean isImmutable (String path) {

		if (path == null) {
			return false;
		}

		String lower = path.toLowerCase();

		return lower.endsWith(".js") || lower.endsWith(".css")
			|| lower.endsWith(".woff") || lower.endsWith(".woff2");

	}

}
