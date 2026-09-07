package io.jimble.web.mpa;

import io.jimble.web.assets.AssetHandler;
import io.jimble.web.assets.AssetPath;
import io.jimble.web.assets.ContentTypes;
import io.jimble.web.assets.Resources;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;

/**
 * MPA 配信（要件 F-R-14 / F-W-20）
 *
 * <p>
 * 静的に書き出した HTML のサイトを配る。
 * <b>拡張子が無いパスは {@code <パス>/index.html} を返す。</b>
 * </p>
 *
 * <pre>
 * /guide        → /site/guide/index.html
 * /guide/a.png  → /site/guide/a.png
 * /             → /site/index.html
 * </pre>
 *
 * <pre>
 * install(() -&gt; MpaHandler.mount("/", "site"));
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>トラバーサルの確認がまったく無かった。</b>SPA 側にはログだけの確認があったが、
 *       MPA 側は {@code baseDir + ctx.path("*")} を素で連結していた</li>
 *   <li><b>ファイルかどうかを {@code Files.probeContentType()} で判定していた。</b>
 *       OS の設定に依存するので、<b>同じ jar でも環境によって
 *       ファイルを返すか index.html を返すかが変わる。</b>
 *       拡張子の対応表で判断する（{@link ContentTypes}）</li>
 *   <li>解決結果のキャッシュに上限が無かった（{@link Resources} 参照）</li>
 * </ol>
 */
public final class MpaHandler implements Handler {

	/** index のファイル名 */
	public static final String INDEX = "index.html";

	/* ベースディレクトリ */
	private final String baseDir;

	/* 静的ファイルの配信 */
	private final AssetHandler assets;

	/**
	 * コンストラクタ
	 *
	 * @param baseDir	クラスパス上のベースディレクトリ
	 */
	public MpaHandler (String baseDir) {

		this.baseDir = AssetPath.normalizeBase(baseDir);
		this.assets = new AssetHandler(baseDir);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		String requestPath = context.route() == null ? "" : context.route().variables().wildcard();

		String resource = resolve(requestPath);

		if (resource == null) {
			context.response().send(404);
			return;
		}

		assets.send(context, resource);

	}

	/**
	 * 配信するリソースを決める
	 *
	 * @param requestPath	リクエストのパス
	 * @return	リソースパス（決まらなければ null）
	 */
	public String resolve (String requestPath) {

		String path = trimTrailingSlash(requestPath);

		String base = baseDir.isEmpty() ? "" : "/" + baseDir;

		if (path.isEmpty()) {
			return base + "/" + INDEX;
		}

		String resource = AssetPath.resolve(baseDir, path);

		if (resource == null) {
			return null;
		}

		/*
		 * 知っている拡張子ならファイルとして扱う。
		 * 移送元は Files.probeContentType() を使っていたので、
		 * 同じ jar でも OS の設定で結果が変わっていた。
		 */
		if (ContentTypes.hasKnownExtension(resource)) {
			return resource;
		}

		return resource + "/" + INDEX;

	}

	/**
	 * 末尾の「/」を落とす
	 *
	 * @param path	パス
	 * @return	落としたもの
	 */
	private static String trimTrailingSlash (String path) {

		if (path == null) {
			return "";
		}

		String value = path;
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

		return value;

	}

	/**
	 * ルートに組み込む
	 *
	 * @param routePath	ルートのパス
	 * @param baseDir	クラスパス上のベースディレクトリ
	 * @return	コントローラ
	 */
	public static MpaController mount (String routePath, String baseDir) {

		return new MpaController(routePath, baseDir);

	}

}
